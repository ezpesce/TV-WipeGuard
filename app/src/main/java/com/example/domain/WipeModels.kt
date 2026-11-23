package com.example.domain

enum class AppScreen {
    DASHBOARD,
    PIN_SETUP,
    CONFIRMATION,
    PIN_VERIFY,
    EXECUTION_CONSOLE,
    VERIFICATION_REPORT,
    SETTINGS_AND_HISTORY,
}

enum class PrivilegeLevel(val label: String) {
    DEVICE_OWNER("Device Owner (Control Total)"),
    DEVICE_ADMIN("Administrador de Dispositivo"),
    ROOT_SU("Superusuario Root (su)"),
    STANDARD_USER("Usuario Estándar (Sin privilegios OS)"),
}

enum class SanitizationPassMode(
    val title: String,
    val subtitle: String,
    val passCount: Int,
    val sweepMegabytes: Int,
) {
    FAST_ZERO_PASS(
        title = "1 Pasada — Ceros (0x00)",
        subtitle = "Sobrescritura lineal rápida + verificación de borrado.",
        passCount = 1,
        sweepMegabytes = 12,
    ),
    STANDARD_2_PASS(
        title = "2 Pasadas — Aleatorio + Ceros",
        subtitle = "Bloques criptográficos SecureRandom seguidos de patrón cero.",
        passCount = 2,
        sweepMegabytes = 18,
    ),
    DOD_3_PASS(
        title = "3 Pasadas — Estándar Profundo (DoD)",
        subtitle = "Patrón 0xFF, patrón aleatorio criptográfico y verificación por lectura.",
        passCount = 3,
        sweepMegabytes = 24,
    ),
}

enum class LogSeverity {
    INFO,
    COMMAND,
    OK,
    WARN,
    ERROR,
}

data class SystemLogEntry(
    val id: Long,
    val timestamp: String,
    val severity: LogSeverity,
    val message: String,
)

enum class WipePhase(val index: Int, val title: String) {
    INSPECTION(0, "Inspección de Sistema y Privilegios"),
    STORAGE_SANITATION(1, "Sanitización Criptográfica de Almacenamiento"),
    CACHE_AND_SAF_CLEANUP(2, "Limpieza de Cachés, Credenciales y Carpetas"),
    FACTORY_RESET_COMMAND(3, "Comando de Reseteo a Valores de Fábrica"),
    FINAL_VERIFICATION(4, "Verificación de Integridad y Registro"),
}

enum class PhaseOutcome {
    PENDING,
    RUNNING,
    VERIFIED,
    REQUIRES_MANUAL_SYSTEM_ACTION,
    SKIPPED_SAFE_MODE,
    FAILED,
}

enum class FactoryResetOutcome(val label: String) {
    DISPATCHED_DEVICE_OWNER("Comando wipeData() ejecutado vía Device Owner"),
    DISPATCHED_DEVICE_ADMIN("Comando wipeData() ejecutado vía Device Admin"),
    DISPATCHED_ROOT_SHELL("Comando MASTER_CLEAR / recovery ejecutado vía Root"),
    REQUIRES_OS_SETTINGS("Bloqueado por OS: requiere confirmación en Ajustes del TV"),
    SKIPPED_SAFE_MODE("Omitido por Modo Verificación Segura (Dry-Run)"),
    FAILED("Error al invocar comando de reseteo del sistema"),
}

data class DeviceTelemetry(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val buildDisplay: String = "",
    val totalStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val appPrivateBytes: Long = 0L,
    val appCacheBytes: Long = 0L,
    val privilegeLevel: PrivilegeLevel = PrivilegeLevel.STANDARD_USER,
    val isAdminActive: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val hasRootSu: Boolean = false,
    val dfOutputSummary: String = "",
)

data class AuthorizedFolder(
    val uriString: String,
    val displayName: String,
)

data class WipeProgressState(
    val isRunning: Boolean = false,
    val overallProgress: Float = 0f,
    val currentPhase: WipePhase = WipePhase.INSPECTION,
    val phaseOutcomes: Map<WipePhase, PhaseOutcome> = WipePhase.entries.associateWith { PhaseOutcome.PENDING },
    val currentPass: Int = 0,
    val totalPasses: Int = 0,
    val bytesOverwritten: Long = 0L,
    val filesPurgedCount: Int = 0,
    val throughputMbPerSec: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val statusHeadline: String = "Preparando motor de sanitización...",
)

data class VerificationReport(
    val reportId: String,
    val timestampIso: String,
    val deviceSummary: String,
    val androidRelease: String,
    val privilegeLevelUsed: PrivilegeLevel,
    val passModeUsed: SanitizationPassMode,
    val safeModeOnly: Boolean,
    val bytesOverwritten: Long,
    val filesShredded: Int,
    val safFoldersPurged: Int,
    val freeStorageBeforeBytes: Long,
    val freeStorageAfterBytes: Long,
    val phaseOutcomes: Map<WipePhase, PhaseOutcome>,
    val factoryResetOutcome: FactoryResetOutcome,
    val detailNote: String,
    val integritySha256: String,
    val commandLogs: List<SystemLogEntry>,
)
