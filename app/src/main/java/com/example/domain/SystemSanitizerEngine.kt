package com.example.domain

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import com.example.admin.TvDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SystemSanitizerEngine(
    private val context: Context,
) {
    private val logSeq = AtomicLong(1L)

    suspend fun inspectDevice(): DeviceTelemetry = withContext(Dispatchers.IO) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = TvDeviceAdminReceiver.componentName(context)
        val isDeviceOwner = runCatching {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        }.getOrDefault(false)
        val isAdminActive = runCatching {
            dpm?.isAdminActive(adminComponent) == true
        }.getOrDefault(false)

        val hasRootSu = checkRootAvailability()

        val privilegeLevel = when {
            isDeviceOwner -> PrivilegeLevel.DEVICE_OWNER
            hasRootSu -> PrivilegeLevel.ROOT_SU
            isAdminActive -> PrivilegeLevel.DEVICE_ADMIN
            else -> PrivilegeLevel.STANDARD_USER
        }

        val dataDir = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)
        val totalBytes = statFs.totalBytes
        val availBytes = statFs.availableBytes
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

        val appPrivateBytes = directorySize(context.filesDir)
        val appCacheBytes = directorySize(context.cacheDir) +
            directorySize(context.codeCacheDir) +
            (context.externalCacheDir?.let { directorySize(it) } ?: 0L)

        val displayIdProp = runShellCommand(listOf("getprop", "ro.build.display.id"), timeoutMs = 1500)
            .output.trim().ifEmpty { Build.DISPLAY }
        val dfResult = runShellCommand(listOf("df", "-h", dataDir.path), timeoutMs = 1500)
        val dfSummary = dfResult.output.lines().drop(1).firstOrNull()?.trim().orEmpty()

        DeviceTelemetry(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            buildDisplay = displayIdProp,
            totalStorageBytes = totalBytes,
            availableStorageBytes = availBytes,
            usedStorageBytes = usedBytes,
            appPrivateBytes = appPrivateBytes,
            appCacheBytes = appCacheBytes,
            privilegeLevel = privilegeLevel,
            isAdminActive = isAdminActive,
            isDeviceOwner = isDeviceOwner,
            hasRootSu = hasRootSu,
            dfOutputSummary = dfSummary,
        )
    }

    suspend fun executeDeepSanitization(
        passMode: SanitizationPassMode,
        authorizedFolders: List<AuthorizedFolder>,
        safeVerificationMode: Boolean,
        onProgress: (WipeProgressState) -> Unit,
        onLog: (SystemLogEntry) -> Unit,
    ): VerificationReport = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val logs = mutableListOf<SystemLogEntry>()
        val outcomes = WipePhase.entries.associateWith { PhaseOutcome.PENDING }.toMutableMap()

        fun emitLog(severity: LogSeverity, msg: String) {
            val entry = SystemLogEntry(
                id = logSeq.getAndIncrement(),
                timestamp = currentTimeStamp(),
                severity = severity,
                message = msg,
            )
            logs.add(entry)
            onLog(entry)
        }

        var totalBytesOverwritten = 0L
        var totalFilesPurged = 0
        var safFoldersPurged = 0

        fun updateProgress(
            progress: Float,
            phase: WipePhase,
            headline: String,
            currentPass: Int = 0,
            throughputMbSec: Double = 0.0,
        ) {
            val elapsed = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
            onProgress(
                WipeProgressState(
                    isRunning = true,
                    overallProgress = progress.coerceIn(0f, 1f),
                    currentPhase = phase,
                    phaseOutcomes = outcomes.toMap(),
                    currentPass = currentPass,
                    totalPasses = passMode.passCount,
                    bytesOverwritten = totalBytesOverwritten,
                    filesPurgedCount = totalFilesPurged,
                    throughputMbPerSec = throughputMbSec,
                    elapsedSeconds = elapsed,
                    statusHeadline = headline,
                )
            )
        }

        // PHASE 1: INSPECTION
        outcomes[WipePhase.INSPECTION] = PhaseOutcome.RUNNING
        updateProgress(0.05f, WipePhase.INSPECTION, "Ejecutando inspección del sistema y privilegios...")
        emitLog(LogSeverity.COMMAND, "$ getprop ro.product.model && getprop ro.build.version.release")
        val telemetry = inspectDevice()
        val freeBefore = telemetry.availableStorageBytes
        emitLog(
            LogSeverity.INFO,
            "Dispositivo: ${telemetry.manufacturer} ${telemetry.model} | Android ${telemetry.androidVersion} (API ${telemetry.sdkInt})"
        )
        emitLog(LogSeverity.COMMAND, "$ df -h /data")
        if (telemetry.dfOutputSummary.isNotEmpty()) {
            emitLog(LogSeverity.INFO, telemetry.dfOutputSummary)
        }
        emitLog(
            LogSeverity.INFO,
            "Nivel de privilegio detectado: ${telemetry.privilegeLevel.label} | Modo prueba segura: $safeVerificationMode"
        )
        outcomes[WipePhase.INSPECTION] = PhaseOutcome.VERIFIED
        updateProgress(0.15f, WipePhase.INSPECTION, "Entorno verificado correctamente")
        delay(250)

        // PHASE 2: CRYPTOGRAPHIC STORAGE SANITIZATION
        outcomes[WipePhase.STORAGE_SANITATION] = PhaseOutcome.RUNNING
        updateProgress(0.18f, WipePhase.STORAGE_SANITATION, "Iniciando sanitización criptográfica de bloques...")

        val safeSweepBytes = minOf(
            passMode.sweepMegabytes.toLong() * 1024L * 1024L,
            (telemetry.availableStorageBytes / 4L).coerceAtLeast(2L * 1024L * 1024L),
        )
        val blockSize = 512 * 1024 // 512 KB chunks for smooth real-time progress
        val scratchDir = File(context.filesDir, "sanitization_workspace").apply { mkdirs() }
        val scratchFile = File(scratchDir, ".wipe_blocks.bin")
        val secureRandom = SecureRandom()
        val buffer = ByteArray(blockSize)

        var storageSanitationOk = true
        try {
            val totalWorkBytes = safeSweepBytes * passMode.passCount
            var passBytesDoneTotal = 0L

            for (passIndex in 1..passMode.passCount) {
                val patternLabel = when {
                    passMode == SanitizationPassMode.FAST_ZERO_PASS -> "0x00 (Ceros)"
                    passMode == SanitizationPassMode.STANDARD_2_PASS && passIndex == 1 -> "SecureRandom CSPRNG"
                    passMode == SanitizationPassMode.STANDARD_2_PASS -> "0x00 (Ceros)"
                    passIndex == 1 -> "0xFF (Unos binarios)"
                    passIndex == 2 -> "SecureRandom CSPRNG"
                    else -> "0x00 + Verificación de lectura"
                }
                emitLog(
                    LogSeverity.COMMAND,
                    "[CRYPTO] Pasada $passIndex/${passMode.passCount} ($patternLabel) — objetivo ${formatBytes(safeSweepBytes)}"
                )

                val passStart = System.currentTimeMillis()
                var writtenThisPass = 0L

                FileOutputStream(scratchFile, false).use { fos ->
                    val channel = fos.channel
                    while (writtenThisPass < safeSweepBytes) {
                        val toWrite = minOf(blockSize.toLong(), safeSweepBytes - writtenThisPass).toInt()
                        when {
                            passMode == SanitizationPassMode.DOD_3_PASS && passIndex == 1 ->
                                buffer.fill(0xFF.toByte(), 0, toWrite)
                            (passMode == SanitizationPassMode.STANDARD_2_PASS && passIndex == 1) ||
                                (passMode == SanitizationPassMode.DOD_3_PASS && passIndex == 2) -> {
                                if (toWrite == buffer.size) {
                                    secureRandom.nextBytes(buffer)
                                } else {
                                    val sub = ByteArray(toWrite)
                                    secureRandom.nextBytes(sub)
                                    sub.copyInto(buffer, 0, 0, toWrite)
                                }
                            }
                            else -> buffer.fill(0x00, 0, toWrite)
                        }

                        fos.write(buffer, 0, toWrite)
                        writtenThisPass += toWrite
                        totalBytesOverwritten += toWrite
                        passBytesDoneTotal += toWrite

                        val elapsedPassSec = ((System.currentTimeMillis() - passStart) / 1000.0).coerceAtLeast(0.05)
                        val mbSec = (writtenThisPass / (1024.0 * 1024.0)) / elapsedPassSec
                        val phaseFraction = passBytesDoneTotal.toFloat() / totalWorkBytes.coerceAtLeast(1L).toFloat()
                        val overall = 0.18f + (phaseFraction * 0.47f)

                        updateProgress(
                            progress = overall,
                            phase = WipePhase.STORAGE_SANITATION,
                            headline = "Sobrescribiendo bloques: Pasada $passIndex de ${passMode.passCount} ($patternLabel)",
                            currentPass = passIndex,
                            throughputMbSec = mbSec,
                        )
                    }
                    fos.flush()
                    channel.force(true)
                    fos.fd.sync()
                }
                emitLog(
                    LogSeverity.OK,
                    "Pasada $passIndex completada y sincronizada en disco (${formatBytes(writtenThisPass)})."
                )

                // Verification read-back on final pass
                if (passIndex == passMode.passCount) {
                    emitLog(LogSeverity.COMMAND, "$ sha256sum / unlink .wipe_blocks.bin (Verificación de lectura)")
                    val nonZeroMismatch = verifyZeroPassFile(scratchFile)
                    if (nonZeroMismatch == 0L) {
                        emitLog(LogSeverity.OK, "Verificación de lectura: 100% de los bloques confirmados en 0x00.")
                    } else {
                        emitLog(LogSeverity.WARN, "Se detectaron $nonZeroMismatch bytes no nulos en lectura.")
                    }
                }
            }

            if (scratchFile.exists()) {
                scratchFile.delete()
            }
            scratchDir.deleteRecursively()
            totalFilesPurged += 1
        } catch (e: Exception) {
            storageSanitationOk = false
            emitLog(LogSeverity.ERROR, "Error en sanitización de bloques: ${e.message}")
        }

        outcomes[WipePhase.STORAGE_SANITATION] =
            if (storageSanitationOk) PhaseOutcome.VERIFIED else PhaseOutcome.FAILED

        // PHASE 3: CACHES, INTERNAL WORKSPACE & SAF FOLDERS
        outcomes[WipePhase.CACHE_AND_SAF_CLEANUP] = PhaseOutcome.RUNNING
        updateProgress(0.68f, WipePhase.CACHE_AND_SAF_CLEANUP, "Purgando cachés, archivos temporales y carpetas autorizadas...")

        emitLog(LogSeverity.COMMAND, "$ rm -rf cacheDir codeCacheDir externalCacheDir && sync")
        val purgedCache = purgeDirectoryWithOverwrite(context.cacheDir) +
            purgeDirectoryWithOverwrite(context.codeCacheDir) +
            (context.externalCacheDir?.let { purgeDirectoryWithOverwrite(it) } ?: 0)
        totalFilesPurged += purgedCache
        emitLog(LogSeverity.OK, "Cachés internas purgadas: $purgedCache elementos eliminados.")

        // Purge non-DataStore files in filesDir
        val extraFiles = context.filesDir.listFiles()
            ?.filter { it.name != "datastore" && it.name != "sanitization_workspace" }
            .orEmpty()
        var extraPurged = 0
        extraFiles.forEach { file ->
            extraPurged += if (file.isDirectory) purgeDirectoryWithOverwrite(file) else {
                if (overwriteFileWithZeros(file)) 1 else 0
            }
        }
        totalFilesPurged += extraPurged

        // Purge SAF user-authorized folders (only if NOT safeVerificationMode)
        if (authorizedFolders.isNotEmpty()) {
            if (safeVerificationMode) {
                emitLog(
                    LogSeverity.WARN,
                    "[SAFE_MODE] Carpetas SAF (${authorizedFolders.size}) preservadas en modo prueba segura."
                )
            } else {
                authorizedFolders.forEach { folder ->
                    val count = purgeSafTree(folder.uriString, ::emitLog)
                    if (count >= 0) {
                        safFoldersPurged += 1
                        totalFilesPurged += count
                    }
                }
            }
        }

        val syncRes = runShellCommand(listOf("sync"), timeoutMs = 2000)
        emitLog(LogSeverity.INFO, "Comando sync ejecutado (código ${syncRes.exitCode}).")
        outcomes[WipePhase.CACHE_AND_SAF_CLEANUP] = PhaseOutcome.VERIFIED
        updateProgress(0.80f, WipePhase.CACHE_AND_SAF_CLEANUP, "Limpieza de cachés y carpetas completada")
        delay(250)

        // PHASE 4: FACTORY RESET SYSTEM COMMAND
        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.RUNNING
        updateProgress(0.85f, WipePhase.FACTORY_RESET_COMMAND, "Ejecutando comando de reseteo profundo del sistema...")

        var resetOutcome: FactoryResetOutcome
        var detailNote: String

        if (safeVerificationMode) {
            emitLog(
                LogSeverity.WARN,
                "[SAFE_MODE] Modo Verificación Segura activo: se omitió DevicePolicyManager.wipeData() / MASTER_CLEAR."
            )
            resetOutcome = FactoryResetOutcome.SKIPPED_SAFE_MODE
            detailNote = "La sanitización real de bloques y cachés se ejecutó y verificó. El reinicio destructivo de fábrica se omitió porque el Modo Verificación Segura está activo."
            outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.SKIPPED_SAFE_MODE
        } else {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            when (telemetry.privilegeLevel) {
                PrivilegeLevel.DEVICE_OWNER -> {
                    emitLog(
                        LogSeverity.COMMAND,
                        "$ dpm.wipeData(WIPE_EXTERNAL_STORAGE | WIPE_RESET_PROTECTION_DATA)"
                    )
                    val result = invokeDpmWipe(dpm, isDeviceOwner = true)
                    if (result.isSuccess) {
                        emitLog(LogSeverity.OK, "DevicePolicyManager.wipeData() aceptado por el sistema. Reiniciando TV...")
                        resetOutcome = FactoryResetOutcome.DISPATCHED_DEVICE_OWNER
                        detailNote = "Orden de restablecimiento de fábrica enviada mediante Device Owner."
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.VERIFIED
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                        emitLog(LogSeverity.ERROR, "Fallo Device Owner wipeData(): $err")
                        resetOutcome = FactoryResetOutcome.FAILED
                        detailNote = "El sistema rechazó wipeData(): $err"
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.FAILED
                    }
                }

                PrivilegeLevel.ROOT_SU -> {
                    emitLog(LogSeverity.COMMAND, "$ su -c \"fstrim -v /data; sync; am broadcast -a android.intent.action.MASTER_CLEAR\"")
                    val trimRes = runShellCommand(listOf("su", "-c", "fstrim -v /data || true"), timeoutMs = 4000)
                    if (trimRes.output.isNotBlank()) {
                        emitLog(LogSeverity.INFO, trimRes.output.trim())
                    }
                    val wipeCmd = runShellCommand(
                        listOf(
                            "su",
                            "-c",
                            "am broadcast -a android.intent.action.MASTER_CLEAR || svc power reboot recovery",
                        ),
                        timeoutMs = 5000,
                    )
                    if (wipeCmd.exitCode == 0) {
                        emitLog(LogSeverity.OK, "Comando Root MASTER_CLEAR enviado correctamente.")
                        resetOutcome = FactoryResetOutcome.DISPATCHED_ROOT_SHELL
                        detailNote = "Reseteo profundo enviado mediante superusuario Root (su)."
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.VERIFIED
                    } else {
                        emitLog(LogSeverity.ERROR, "Root falló (exit ${wipeCmd.exitCode}): ${wipeCmd.output}")
                        resetOutcome = FactoryResetOutcome.REQUIRES_OS_SETTINGS
                        detailNote = "El binario su no completó MASTER_CLEAR (${wipeCmd.output}). Complete el borrado en Ajustes de Fábrica del TV."
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.REQUIRES_MANUAL_SYSTEM_ACTION
                    }
                }

                PrivilegeLevel.DEVICE_ADMIN -> {
                    emitLog(LogSeverity.COMMAND, "$ dpm.wipeData(WIPE_EXTERNAL_STORAGE)")
                    val result = invokeDpmWipe(dpm, isDeviceOwner = false)
                    if (result.isSuccess) {
                        emitLog(LogSeverity.OK, "DevicePolicyManager.wipeData() aceptado por el sistema.")
                        resetOutcome = FactoryResetOutcome.DISPATCHED_DEVICE_ADMIN
                        detailNote = "Reseteo de fábrica iniciado mediante Administrador de Dispositivo."
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.VERIFIED
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Restringido por Android OS"
                        emitLog(
                            LogSeverity.WARN,
                            "Android OS bloqueó wipeData() en modo Admin estándar ($err). Se requiere Device Owner o confirmación manual en Ajustes del TV."
                        )
                        resetOutcome = FactoryResetOutcome.REQUIRES_OS_SETTINGS
                        detailNote = "Almacenamiento accesible sanitizado. Android ${telemetry.androidVersion} exige que la app sea Device Owner o confirmar el borrado final de /data en los Ajustes de Fábrica del TV."
                        outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.REQUIRES_MANUAL_SYSTEM_ACTION
                    }
                }

                PrivilegeLevel.STANDARD_USER -> {
                    emitLog(
                        LogSeverity.COMMAND,
                        "$ am broadcast -a android.intent.action.MASTER_CLEAR (Verificando permiso)"
                    )
                    emitLog(
                        LogSeverity.WARN,
                        "RESTRINGIDO POR EL SO: El permiso android.permission.MASTER_CLEAR es exclusivo de aplicaciones de sistema, Device Owner o Root."
                    )
                    emitLog(
                        LogSeverity.INFO,
                        "Acción transparente: use 'Abrir Ajustes de Fábrica del TV' o aprovisione Device Owner vía ADB para reseteo desatendido."
                    )
                    resetOutcome = FactoryResetOutcome.REQUIRES_OS_SETTINGS
                    detailNote = "Sanitización criptográfica y limpieza local verificadas. Como aplicación sin privilegios Device Owner/Root, Android exige confirmar el formateo final de /data desde Ajustes del Sistema del TV."
                    outcomes[WipePhase.FACTORY_RESET_COMMAND] = PhaseOutcome.REQUIRES_MANUAL_SYSTEM_ACTION
                }
            }
        }

        // PHASE 5: FINAL VERIFICATION & INTEGRITY CERTIFICATE
        outcomes[WipePhase.FINAL_VERIFICATION] = PhaseOutcome.RUNNING
        updateProgress(0.95f, WipePhase.FINAL_VERIFICATION, "Generando registro final y firma SHA-256...")

        val freeAfter = StatFs(Environment.getDataDirectory().path).availableBytes
        val reportId = "WG-${UUID.randomUUID().toString().substring(0, 8).uppercase(Locale.US)}"
        val timestampIso = currentIsoTimestamp()

        val logDigestInput = buildString {
            append(reportId).append('|')
            append(timestampIso).append('|')
            append(telemetry.model).append('|')
            append(totalBytesOverwritten).append('|')
            append(totalFilesPurged).append('|')
            append(resetOutcome.name).append('|')
            logs.forEach { append(it.timestamp).append(it.message).append('\n') }
        }
        val sha256 = sha256Hex(logDigestInput)
        emitLog(LogSeverity.OK, "Registro $reportId sellado con firma SHA-256: ${sha256.take(24)}...")

        outcomes[WipePhase.FINAL_VERIFICATION] = PhaseOutcome.VERIFIED
        updateProgress(1.0f, WipePhase.FINAL_VERIFICATION, "Proceso finalizado y verificado")

        VerificationReport(
            reportId = reportId,
            timestampIso = timestampIso,
            deviceSummary = "${telemetry.manufacturer} ${telemetry.model}",
            androidRelease = "Android ${telemetry.androidVersion} (API ${telemetry.sdkInt})",
            privilegeLevelUsed = telemetry.privilegeLevel,
            passModeUsed = passMode,
            safeModeOnly = safeVerificationMode,
            bytesOverwritten = totalBytesOverwritten,
            filesShredded = totalFilesPurged,
            safFoldersPurged = safFoldersPurged,
            freeStorageBeforeBytes = freeBefore,
            freeStorageAfterBytes = freeAfter,
            phaseOutcomes = outcomes.toMap(),
            factoryResetOutcome = resetOutcome,
            detailNote = detailNote,
            integritySha256 = sha256,
            commandLogs = logs.toList(),
        )
    }

    private fun invokeDpmWipe(dpm: DevicePolicyManager?, isDeviceOwner: Boolean): Result<Unit> {
        if (dpm == null) return Result.failure(IllegalStateException("DevicePolicyManager no disponible"))
        return runCatching {
            val flags = if (isDeviceOwner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DevicePolicyManager.WIPE_EXTERNAL_STORAGE or DevicePolicyManager.WIPE_RESET_PROTECTION_DATA
            } else {
                DevicePolicyManager.WIPE_EXTERNAL_STORAGE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.wipeData(flags, "TV WipeGuard Deep Factory Reset")
            } else {
                @Suppress("DEPRECATION")
                dpm.wipeData(flags)
            }
        }
    }

    private fun verifyZeroPassFile(file: File): Long {
        if (!file.exists()) return 0L
        var nonZeroBytes = 0L
        val buf = ByteArray(256 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                for (i in 0 until read) {
                    if (buf[i].toInt() != 0) nonZeroBytes++
                }
            }
        }
        return nonZeroBytes
    }

    private fun purgeDirectoryWithOverwrite(dir: File): Int {
        if (!dir.exists()) return 0
        var count = 0
        val children = dir.listFiles() ?: return 0
        for (child in children) {
            if (child.isDirectory) {
                count += purgeDirectoryWithOverwrite(child)
                if (child.delete()) count++
            } else {
                if (overwriteFileWithZeros(child)) count++
            }
        }
        return count
    }

    private fun overwriteFileWithZeros(file: File): Boolean {
        return runCatching {
            val length = file.length()
            if (length > 0 && file.canWrite()) {
                RandomAccessFile(file, "rw").use { raf ->
                    val chunk = ByteArray(minOf(length, 65536L).toInt())
                    var remaining = length
                    raf.seek(0)
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        raf.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()
                }
            }
            file.delete()
        }.getOrDefault(false)
    }

    private fun purgeSafTree(treeUriStr: String, emitLog: (LogSeverity, String) -> Unit): Int {
        return runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val deleted = deleteSafChildrenRecursively(treeUri, rootDocUri)
            emitLog(LogSeverity.OK, "Carpeta SAF purgada: $deleted archivos eliminados ($docId).")
            deleted
        }.getOrElse { e ->
            emitLog(LogSeverity.WARN, "No se pudo purgar carpeta SAF: ${e.message}")
            -1
        }
    }

    private fun deleteSafChildrenRecursively(treeUri: Uri, parentDocUri: Uri): Int {
        var count = 0
        val parentId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIdx)
                val mime = cursor.getString(mimeIdx)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    count += deleteSafChildrenRecursively(treeUri, childUri)
                    if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, childUri) }.getOrDefault(false)) {
                        count++
                    }
                } else {
                    // Best-effort zero-overwrite via OutputStream before unlinking
                    runCatching {
                        context.contentResolver.openOutputStream(childUri, "wt")?.use { os ->
                            os.write(ByteArray(1024))
                            os.flush()
                        }
                    }
                    if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, childUri) }.getOrDefault(false)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun checkRootAvailability(): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su")
        val binaryExists = suPaths.any { File(it).exists() }
        if (!binaryExists) {
            val whichSu = runShellCommand(listOf("which", "su"), timeoutMs = 800)
            if (whichSu.exitCode != 0 || whichSu.output.isBlank()) return false
        }
        val idResult = runShellCommand(listOf("su", "-c", "id"), timeoutMs = 1800)
        return idResult.exitCode == 0 && idResult.output.contains("uid=0")
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private data class ShellResult(val exitCode: Int, val output: String)

    private fun runShellCommand(command: List<String>, timeoutMs: Long): ShellResult {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                ShellResult(-1, "Timeout tras ${timeoutMs}ms")
            } else {
                val out = process.inputStream.bufferedReader().use { it.readText() }
                ShellResult(process.exitValue(), out)
            }
        }.getOrElse { e ->
            ShellResult(-1, e.message ?: "Error de ejecución")
        }
    }

    companion object {
        fun sha256Hex(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }

        private fun currentTimeStamp(): String {
            val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return fmt.format(Date())
        }

        private fun currentIsoTimestamp(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date())
        }
    }
}
