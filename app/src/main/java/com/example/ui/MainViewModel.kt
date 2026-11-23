package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PinVerifyResult
import com.example.data.SecurityPreferencesRepository
import com.example.data.SecuritySettingsState
import com.example.domain.AppScreen
import com.example.domain.AuthorizedFolder
import com.example.domain.DeviceTelemetry
import com.example.domain.SanitizationPassMode
import com.example.domain.SystemLogEntry
import com.example.domain.SystemSanitizerEngine
import com.example.domain.VerificationReport
import com.example.domain.WipeProgressState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val isRefreshingTelemetry: Boolean = true,
    val settings: SecuritySettingsState = SecuritySettingsState(),
    val pinInput: String = "",
    val pinConfirmInput: String = "",
    val pinSetupStep: PinSetupStep = PinSetupStep.ENTER_NEW,
    val pinError: String? = null,
    val isPinArmedForExecution: Boolean = false,
    val lockoutRemainingSeconds: Long = 0L,
    val wipeProgress: WipeProgressState = WipeProgressState(),
    val liveLogs: List<SystemLogEntry> = emptyList(),
    val activeReport: VerificationReport? = null,
    val bannerMessage: String? = null,
)

enum class PinSetupStep {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW,
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SecurityPreferencesRepository(application.applicationContext)
    private val sanitizerEngine = SystemSanitizerEngine(application.applicationContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lockoutTimerJob: Job? = null
    private var wipeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.stateFlow.collect { settings ->
                _uiState.update { state ->
                    val now = System.currentTimeMillis()
                    val lockRem = ((settings.lockoutUntilEpochMs - now) / 1000L).coerceAtLeast(0L)
                    state.copy(
                        settings = settings,
                        lockoutRemainingSeconds = lockRem,
                    )
                }
                if (settings.lockoutUntilEpochMs > System.currentTimeMillis()) {
                    startLockoutCountdown(settings.lockoutUntilEpochMs)
                }
            }
        }
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTelemetry = true) }
            val info = sanitizerEngine.inspectDevice()
            _uiState.update { it.copy(telemetry = info, isRefreshingTelemetry = false) }
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(bannerMessage = message) }
    }

    fun navigateTo(screen: AppScreen) {
        if (_uiState.value.wipeProgress.isRunning && screen != AppScreen.EXECUTION_CONSOLE) {
            return
        }
        _uiState.update {
            it.copy(
                currentScreen = screen,
                pinInput = "",
                pinConfirmInput = "",
                pinError = null,
                isPinArmedForExecution = false,
            )
        }
    }

    fun openPinSetup() {
        val hasPin = _uiState.value.settings.hasPinConfigured
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.PIN_SETUP,
                pinSetupStep = if (hasPin) PinSetupStep.VERIFY_CURRENT else PinSetupStep.ENTER_NEW,
                pinInput = "",
                pinConfirmInput = "",
                pinError = null,
            )
        }
    }

    fun startDeepWipeFlow() {
        if (!_uiState.value.settings.hasPinConfigured) {
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.PIN_SETUP,
                    pinSetupStep = PinSetupStep.ENTER_NEW,
                    pinInput = "",
                    pinConfirmInput = "",
                    pinError = null,
                    bannerMessage = "Configure un código PIN de 4 a 6 dígitos antes de iniciar el borrado.",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.CONFIRMATION,
                pinInput = "",
                pinError = null,
                isPinArmedForExecution = false,
            )
        }
    }

    fun proceedToPinVerification() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.PIN_VERIFY,
                pinInput = "",
                pinError = null,
                isPinArmedForExecution = false,
            )
        }
    }

    fun appendPinDigit(digit: Char) {
        if (!digit.isDigit()) return
        val state = _uiState.value
        if (state.lockoutRemainingSeconds > 0 && state.currentScreen == AppScreen.PIN_VERIFY) return
        if (state.isPinArmedForExecution) return
        if (state.pinInput.length >= 6) return
        _uiState.update { it.copy(pinInput = it.pinInput + digit, pinError = null) }
    }

    fun backspacePin() {
        val state = _uiState.value
        if (state.isPinArmedForExecution) return
        if (state.pinInput.isNotEmpty()) {
            _uiState.update { it.copy(pinInput = it.pinInput.dropLast(1), pinError = null) }
        }
    }

    fun clearPinInput() {
        if (_uiState.value.isPinArmedForExecution) return
        _uiState.update { it.copy(pinInput = "", pinError = null) }
    }

    fun submitPinSetupStep() {
        val state = _uiState.value
        val currentInput = state.pinInput
        if (currentInput.length < 4) {
            _uiState.update { it.copy(pinError = "El PIN debe tener entre 4 y 6 dígitos.") }
            return
        }

        when (state.pinSetupStep) {
            PinSetupStep.VERIFY_CURRENT -> {
                viewModelScope.launch {
                    when (val res = repository.verifyPin(currentInput)) {
                        is PinVerifyResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    pinSetupStep = PinSetupStep.ENTER_NEW,
                                    pinInput = "",
                                    pinError = null,
                                )
                            }
                        }
                        is PinVerifyResult.Invalid -> {
                            _uiState.update {
                                it.copy(
                                    pinInput = "",
                                    pinError = "PIN actual incorrecto. Intentos restantes: ${res.attemptsRemaining}",
                                )
                            }
                        }
                        is PinVerifyResult.LockedOut -> {
                            _uiState.update {
                                it.copy(
                                    pinInput = "",
                                    lockoutRemainingSeconds = res.remainingSeconds,
                                    pinError = "Bloqueo temporal activo (${res.remainingSeconds}s).",
                                )
                            }
                        }
                        PinVerifyResult.NotConfigured -> {
                            _uiState.update {
                                it.copy(pinSetupStep = PinSetupStep.ENTER_NEW, pinInput = "", pinError = null)
                            }
                        }
                    }
                }
            }

            PinSetupStep.ENTER_NEW -> {
                _uiState.update {
                    it.copy(
                        pinConfirmInput = currentInput,
                        pinInput = "",
                        pinSetupStep = PinSetupStep.CONFIRM_NEW,
                        pinError = null,
                    )
                }
            }

            PinSetupStep.CONFIRM_NEW -> {
                if (currentInput != state.pinConfirmInput) {
                    _uiState.update {
                        it.copy(
                            pinInput = "",
                            pinConfirmInput = "",
                            pinSetupStep = PinSetupStep.ENTER_NEW,
                            pinError = "Los PIN ingresados no coinciden. Intente nuevamente.",
                        )
                    }
                    return
                }
                viewModelScope.launch {
                    repository.saveNewPin(currentInput)
                    _uiState.update {
                        it.copy(
                            currentScreen = AppScreen.DASHBOARD,
                            pinInput = "",
                            pinConfirmInput = "",
                            pinError = null,
                            bannerMessage = "PIN de seguridad guardado con cifrado SHA-256.",
                        )
                    }
                }
            }
        }
    }

    fun submitPinForWipeAuthorization() {
        val state = _uiState.value
        if (state.lockoutRemainingSeconds > 0) {
            _uiState.update {
                it.copy(pinError = "Esperando fin de bloqueo (${state.lockoutRemainingSeconds}s).")
            }
            return
        }
        if (state.pinInput.length < 4) {
            _uiState.update { it.copy(pinError = "Ingrese su PIN de 4 a 6 dígitos.") }
            return
        }

        viewModelScope.launch {
            when (val res = repository.verifyPin(state.pinInput)) {
                is PinVerifyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPinArmedForExecution = true,
                            pinError = null,
                        )
                    }
                }
                is PinVerifyResult.Invalid -> {
                    _uiState.update {
                        it.copy(
                            pinInput = "",
                            pinError = "PIN incorrecto. Intentos restantes antes del bloqueo: ${res.attemptsRemaining}",
                        )
                    }
                }
                is PinVerifyResult.LockedOut -> {
                    _uiState.update {
                        it.copy(
                            pinInput = "",
                            lockoutRemainingSeconds = res.remainingSeconds,
                            pinError = "Demasiados intentos fallidos. Bloqueado por ${res.remainingSeconds}s.",
                        )
                    }
                }
                PinVerifyResult.NotConfigured -> {
                    _uiState.update {
                        it.copy(currentScreen = AppScreen.PIN_SETUP, pinInput = "", pinError = null)
                    }
                }
            }
        }
    }

    fun executeArmedWipe() {
        val state = _uiState.value
        if (!state.isPinArmedForExecution || state.wipeProgress.isRunning) return

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.EXECUTION_CONSOLE,
                liveLogs = emptyList(),
                wipeProgress = WipeProgressState(
                    isRunning = true,
                    totalPasses = state.settings.passMode.passCount,
                    statusHeadline = "Inicializando secuencia de borrado profundo...",
                ),
            )
        }

        wipeJob?.cancel()
        wipeJob = viewModelScope.launch {
            val report = sanitizerEngine.executeDeepSanitization(
                passMode = state.settings.passMode,
                authorizedFolders = state.settings.authorizedFolders,
                safeVerificationMode = state.settings.safeVerificationMode,
                onProgress = { progress ->
                    _uiState.update { it.copy(wipeProgress = progress) }
                },
                onLog = { entry ->
                    _uiState.update { current ->
                        current.copy(liveLogs = (current.liveLogs + entry).takeLast(250))
                    }
                },
            )
            repository.appendVerificationReport(report)
            if (!state.settings.safeVerificationMode) {
                repository.clearAuthorizedFolders()
            }
            val updatedTelemetry = sanitizerEngine.inspectDevice()
            _uiState.update {
                it.copy(
                    wipeProgress = it.wipeProgress.copy(isRunning = false, overallProgress = 1f),
                    activeReport = report,
                    telemetry = updatedTelemetry,
                    currentScreen = AppScreen.VERIFICATION_REPORT,
                    isPinArmedForExecution = false,
                    pinInput = "",
                )
            }
        }
    }

    fun setPassMode(mode: SanitizationPassMode) {
        viewModelScope.launch { repository.setPassMode(mode) }
    }

    fun setSafeVerificationMode(enabled: Boolean) {
        viewModelScope.launch { repository.setSafeVerificationMode(enabled) }
    }

    fun addAuthorizedFolder(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            app.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
        }.getOrDefault(uri.lastPathSegment ?: "Carpeta externa")

        viewModelScope.launch {
            repository.addAuthorizedFolder(AuthorizedFolder(uri.toString(), name))
            showBanner("Carpeta vinculada para sanitización: $name")
        }
    }

    fun removeAuthorizedFolder(uriString: String) {
        viewModelScope.launch {
            repository.removeAuthorizedFolder(uriString)
        }
    }

    fun openHistoryReport(report: VerificationReport) {
        _uiState.update {
            it.copy(
                activeReport = report,
                currentScreen = AppScreen.VERIFICATION_REPORT,
            )
        }
    }

    fun buildExportableReportText(report: VerificationReport): String = buildString {
        appendLine("====================================================")
        appendLine("   TV WIPEGUARD — REGISTRO FINAL DE VERIFICACIÓN    ")
        appendLine("====================================================")
        appendLine("ID Auditoría:     ${report.reportId}")
        appendLine("Fecha UTC:        ${report.timestampIso}")
        appendLine("Dispositivo:      ${report.deviceSummary}")
        appendLine("Sistema:          ${report.androidRelease}")
        appendLine("Privilegios:      ${report.privilegeLevelUsed.label}")
        appendLine("Modo Sanitización:${report.passModeUsed.title}")
        appendLine("Modo Seguro (Dry):${if (report.safeModeOnly) "SÍ" else "NO (Ejecución Real)"}")
        appendLine("Bytes Sobrescrit.:${SystemSanitizerEngine.formatBytes(report.bytesOverwritten)}")
        appendLine("Elementos Borrad.:${report.filesShredded}")
        appendLine("Carpetas SAF:     ${report.safFoldersPurged}")
        appendLine("Libre Antes:      ${SystemSanitizerEngine.formatBytes(report.freeStorageBeforeBytes)}")
        appendLine("Libre Después:    ${SystemSanitizerEngine.formatBytes(report.freeStorageAfterBytes)}")
        appendLine("Reseteo Fábrica:  ${report.factoryResetOutcome.label}")
        appendLine("Firma SHA-256:    ${report.integritySha256}")
        appendLine("----------------------------------------------------")
        appendLine("Detalle: ${report.detailNote}")
        appendLine("----------------------------------------------------")
        appendLine("Fases:")
        report.phaseOutcomes.forEach { (phase, outcome) ->
            appendLine(" - [${outcome.name}] ${phase.title}")
        }
        if (report.commandLogs.isNotEmpty()) {
            appendLine("----------------------------------------------------")
            appendLine("Registro de Comandos Ejecutados:")
            report.commandLogs.forEach { log ->
                appendLine("[${log.timestamp}] [${log.severity.name}] ${log.message}")
            }
        }
        appendLine("====================================================")
    }

    fun buildSystemResetSettingsIntent(): Intent {
        val candidates = listOf(
            Intent("android.settings.BACKUP_AND_RESET_SETTINGS"),
            Intent(Settings.ACTION_PRIVACY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        val pm = getApplication<Application>().packageManager
        return candidates.firstOrNull { it.resolveActivity(pm) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
    }

    private fun startLockoutCountdown(untilEpochMs: Long) {
        lockoutTimerJob?.cancel()
        lockoutTimerJob = viewModelScope.launch {
            while (true) {
                val rem = ((untilEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(lockoutRemainingSeconds = rem) }
                if (rem <= 0L) break
                delay(1000L)
            }
        }
    }
}
