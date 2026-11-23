package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AuthorizedFolder
import com.example.domain.FactoryResetOutcome
import com.example.domain.PhaseOutcome
import com.example.domain.PrivilegeLevel
import com.example.domain.SanitizationPassMode
import com.example.domain.VerificationReport
import com.example.domain.WipePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.wipeGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tv_wipeguard_prefs"
)

data class SecuritySettingsState(
    val hasPinConfigured: Boolean = false,
    val failedPinAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val passMode: SanitizationPassMode = SanitizationPassMode.STANDARD_2_PASS,
    val safeVerificationMode: Boolean = false,
    val authorizedFolders: List<AuthorizedFolder> = emptyList(),
    val verificationHistory: List<VerificationReport> = emptyList(),
)

class SecurityPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_sha256_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt_hex")
        val FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")
        val PASS_MODE = stringPreferencesKey("sanitization_pass_mode")
        val SAFE_VERIFICATION_MODE = booleanPreferencesKey("safe_verification_mode")
        val AUTHORIZED_FOLDERS_JSON = stringPreferencesKey("authorized_folders_json")
        val HISTORY_JSON = stringPreferencesKey("verification_history_json")
    }

    val stateFlow: Flow<SecuritySettingsState> = context.wipeGuardDataStore.data.map { prefs ->
        val hash = prefs[Keys.PIN_HASH].orEmpty()
        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val modeName = prefs[Keys.PASS_MODE] ?: SanitizationPassMode.STANDARD_2_PASS.name
        val passMode = runCatching { SanitizationPassMode.valueOf(modeName) }
            .getOrDefault(SanitizationPassMode.STANDARD_2_PASS)

        SecuritySettingsState(
            hasPinConfigured = hash.isNotEmpty() && salt.isNotEmpty(),
            failedPinAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilEpochMs = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L,
            passMode = passMode,
            safeVerificationMode = prefs[Keys.SAFE_VERIFICATION_MODE] ?: false,
            authorizedFolders = decodeFolders(prefs[Keys.AUTHORIZED_FOLDERS_JSON]),
            verificationHistory = decodeHistory(prefs[Keys.HISTORY_JSON]),
        )
    }

    suspend fun saveNewPin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hashHex = hashPinWithSalt(pin, saltHex)
        context.wipeGuardDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = saltHex
            prefs[Keys.PIN_HASH] = hashHex
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun verifyPin(pin: String, nowEpochMs: Long = System.currentTimeMillis()): PinVerifyResult {
        val prefs = context.wipeGuardDataStore.data.first()
        val lockoutUntil = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L
        if (lockoutUntil > nowEpochMs) {
            val remainingSec = ((lockoutUntil - nowEpochMs) / 1000L).coerceAtLeast(1L)
            return PinVerifyResult.LockedOut(remainingSec)
        }

        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val expectedHash = prefs[Keys.PIN_HASH].orEmpty()
        if (salt.isEmpty() || expectedHash.isEmpty()) {
            return PinVerifyResult.NotConfigured
        }

        val candidateHash = hashPinWithSalt(pin, salt)
        return if (MessageDigest.isEqual(candidateHash.toByteArray(), expectedHash.toByteArray())) {
            context.wipeGuardDataStore.edit { mutable ->
                mutable[Keys.FAILED_ATTEMPTS] = 0
                mutable[Keys.LOCKOUT_UNTIL_MS] = 0L
            }
            PinVerifyResult.Success
        } else {
            val newAttempts = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
            val shouldLock = newAttempts >= MAX_FAILED_ATTEMPTS
            val nextLockout = if (shouldLock) nowEpochMs + LOCKOUT_DURATION_MS else 0L
            context.wipeGuardDataStore.edit { mutable ->
                mutable[Keys.FAILED_ATTEMPTS] = if (shouldLock) 0 else newAttempts
                mutable[Keys.LOCKOUT_UNTIL_MS] = nextLockout
            }
            if (shouldLock) {
                PinVerifyResult.LockedOut(LOCKOUT_DURATION_MS / 1000L)
            } else {
                PinVerifyResult.Invalid(attemptsRemaining = MAX_FAILED_ATTEMPTS - newAttempts)
            }
        }
    }

    suspend fun setPassMode(mode: SanitizationPassMode) {
        context.wipeGuardDataStore.edit { it[Keys.PASS_MODE] = mode.name }
    }

    suspend fun setSafeVerificationMode(enabled: Boolean) {
        context.wipeGuardDataStore.edit { it[Keys.SAFE_VERIFICATION_MODE] = enabled }
    }

    suspend fun addAuthorizedFolder(folder: AuthorizedFolder) {
        context.wipeGuardDataStore.edit { prefs ->
            val current = decodeFolders(prefs[Keys.AUTHORIZED_FOLDERS_JSON]).toMutableList()
            if (current.none { it.uriString == folder.uriString }) {
                current.add(folder)
            }
            prefs[Keys.AUTHORIZED_FOLDERS_JSON] = encodeFolders(current)
        }
    }

    suspend fun removeAuthorizedFolder(uriString: String) {
        context.wipeGuardDataStore.edit { prefs ->
            val updated = decodeFolders(prefs[Keys.AUTHORIZED_FOLDERS_JSON])
                .filterNot { it.uriString == uriString }
            prefs[Keys.AUTHORIZED_FOLDERS_JSON] = encodeFolders(updated)
        }
    }

    suspend fun clearAuthorizedFolders() {
        context.wipeGuardDataStore.edit { it.remove(Keys.AUTHORIZED_FOLDERS_JSON) }
    }

    suspend fun appendVerificationReport(report: VerificationReport) {
        context.wipeGuardDataStore.edit { prefs ->
            val current = decodeHistory(prefs[Keys.HISTORY_JSON]).toMutableList()
            current.add(0, report)
            val trimmed = current.take(MAX_HISTORY_ITEMS)
            prefs[Keys.HISTORY_JSON] = encodeHistory(trimmed)
        }
    }

    private fun encodeFolders(folders: List<AuthorizedFolder>): String {
        val arr = JSONArray()
        folders.forEach { folder ->
            arr.put(
                JSONObject().apply {
                    put("uri", folder.uriString)
                    put("name", folder.displayName)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeFolders(raw: String?): List<AuthorizedFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AuthorizedFolder(
                            uriString = obj.getString("uri"),
                            displayName = obj.getString("name"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeHistory(reports: List<VerificationReport>): String {
        val arr = JSONArray()
        reports.forEach { r ->
            val phasesObj = JSONObject()
            r.phaseOutcomes.forEach { (phase, outcome) ->
                phasesObj.put(phase.name, outcome.name)
            }
            arr.put(
                JSONObject().apply {
                    put("reportId", r.reportId)
                    put("timestampIso", r.timestampIso)
                    put("deviceSummary", r.deviceSummary)
                    put("androidRelease", r.androidRelease)
                    put("privilegeLevelUsed", r.privilegeLevelUsed.name)
                    put("passModeUsed", r.passModeUsed.name)
                    put("safeModeOnly", r.safeModeOnly)
                    put("bytesOverwritten", r.bytesOverwritten)
                    put("filesShredded", r.filesShredded)
                    put("safFoldersPurged", r.safFoldersPurged)
                    put("freeStorageBeforeBytes", r.freeStorageBeforeBytes)
                    put("freeStorageAfterBytes", r.freeStorageAfterBytes)
                    put("phaseOutcomes", phasesObj)
                    put("factoryResetOutcome", r.factoryResetOutcome.name)
                    put("detailNote", r.detailNote)
                    put("integritySha256", r.integritySha256)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeHistory(raw: String?): List<VerificationReport> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val phasesJson = obj.optJSONObject("phaseOutcomes")
                    val phasesMap = WipePhase.entries.associateWith { phase ->
                        val outcomeStr = phasesJson?.optString(phase.name) ?: PhaseOutcome.PENDING.name
                        runCatching { PhaseOutcome.valueOf(outcomeStr) }
                            .getOrDefault(PhaseOutcome.PENDING)
                    }
                    add(
                        VerificationReport(
                            reportId = obj.getString("reportId"),
                            timestampIso = obj.getString("timestampIso"),
                            deviceSummary = obj.getString("deviceSummary"),
                            androidRelease = obj.getString("androidRelease"),
                            privilegeLevelUsed = runCatching {
                                PrivilegeLevel.valueOf(obj.getString("privilegeLevelUsed"))
                            }.getOrDefault(PrivilegeLevel.STANDARD_USER),
                            passModeUsed = runCatching {
                                SanitizationPassMode.valueOf(obj.getString("passModeUsed"))
                            }.getOrDefault(SanitizationPassMode.STANDARD_2_PASS),
                            safeModeOnly = obj.optBoolean("safeModeOnly", false),
                            bytesOverwritten = obj.optLong("bytesOverwritten", 0L),
                            filesShredded = obj.optInt("filesShredded", 0),
                            safFoldersPurged = obj.optInt("safFoldersPurged", 0),
                            freeStorageBeforeBytes = obj.optLong("freeStorageBeforeBytes", 0L),
                            freeStorageAfterBytes = obj.optLong("freeStorageAfterBytes", 0L),
                            phaseOutcomes = phasesMap,
                            factoryResetOutcome = runCatching {
                                FactoryResetOutcome.valueOf(obj.getString("factoryResetOutcome"))
                            }.getOrDefault(FactoryResetOutcome.REQUIRES_OS_SETTINGS),
                            detailNote = obj.optString("detailNote", ""),
                            integritySha256 = obj.optString("integritySha256", ""),
                            commandLogs = emptyList(),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L
        private const val MAX_HISTORY_ITEMS = 12

        fun hashPinWithSalt(pin: String, saltHex: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "$saltHex:$pin:tv_wipeguard_v1".toByteArray(Charsets.UTF_8)
            val bytes = digest.digest(input)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data object NotConfigured : PinVerifyResult
    data class Invalid(val attemptsRemaining: Int) : PinVerifyResult
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult
}
