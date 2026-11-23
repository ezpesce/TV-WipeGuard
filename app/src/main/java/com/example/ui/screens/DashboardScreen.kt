package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.SanitizationPassMode
import com.example.domain.SystemSanitizerEngine
import com.example.ui.MainUiState
import com.example.ui.components.PrivilegeBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.StorageBar
import com.example.ui.components.TvPrimaryButton
import com.example.ui.components.TvSecondaryButton
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDestructive
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onStartDeepWipe: () -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenSettingsHistory: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onPickSafFolder: () -> Unit,
    onRemoveSafFolder: (String) -> Unit,
    onSelectPassMode: (SanitizationPassMode) -> Unit,
    onRefreshTelemetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBg, com.example.ui.theme.ObsidianSurface)
                )
            )
    ) {
        val isWideTvLayout = maxWidth >= 720.dp
        val horizontalPad = if (isWideTvLayout) 32.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPad, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardHeader(
                uiState = uiState,
                onRefreshTelemetry = onRefreshTelemetry,
            )

            if (uiState.settings.safeVerificationMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberContainer,
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Modo prueba segura",
                            tint = AmberWarning,
                        )
                        Text(
                            text = "MODO VERIFICACIÓN SEGURA ACTIVO: La sanitización de bloques y cachés se ejecutará realmente, pero se omitirá el reinicio destructivo de fábrica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                        )
                    }
                }
            }

            if (isWideTvLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DeviceTelemetryCard(uiState = uiState)
                        SanitizationProfileCard(
                            selectedMode = uiState.settings.passMode,
                            onSelectMode = onSelectPassMode,
                        )
                        SafFoldersCard(
                            uiState = uiState,
                            onPickSafFolder = onPickSafFolder,
                            onRemoveSafFolder = onRemoveSafFolder,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SecurityActionPanel(
                            uiState = uiState,
                            onStartDeepWipe = onStartDeepWipe,
                            onOpenPinSetup = onOpenPinSetup,
                            onOpenSettingsHistory = onOpenSettingsHistory,
                            onRequestDeviceAdmin = onRequestDeviceAdmin,
                        )
                    }
                }
            } else {
                DeviceTelemetryCard(uiState = uiState)
                SecurityActionPanel(
                    uiState = uiState,
                    onStartDeepWipe = onStartDeepWipe,
                    onOpenPinSetup = onOpenPinSetup,
                    onOpenSettingsHistory = onOpenSettingsHistory,
                    onRequestDeviceAdmin = onRequestDeviceAdmin,
                )
                SanitizationProfileCard(
                    selectedMode = uiState.settings.passMode,
                    onSelectMode = onSelectPassMode,
                )
                SafFoldersCard(
                    uiState = uiState,
                    onPickSafFolder = onPickSafFolder,
                    onRemoveSafFolder = onRemoveSafFolder,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    uiState: MainUiState,
    onRefreshTelemetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CrimsonContainer,
                border = BorderStroke(1.dp, CrimsonDestructive.copy(alpha = 0.55f)),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CrimsonDestructive,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "Consola de Sanitización y Reseteo Profundo para Android TV",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrivilegeBadge(level = uiState.telemetry.privilegeLevel)
            IconButton(
                onClick = onRefreshTelemetry,
                modifier = Modifier.testTag("refresh_telemetry_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar diagnóstico del sistema",
                    tint = CyanTelemetry,
                )
            }
        }
    }
}

@Composable
private fun DeviceTelemetryCard(uiState: MainUiState) {
    val t = uiState.telemetry
    TvSurfaceCard(testTag = "device_telemetry_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = CyanTelemetry,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Estado del Dispositivo y Almacenamiento",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            }
            StatusPill(
                text = if (uiState.isRefreshingTelemetry) "ESCANEANDO" else "EN LÍNEA",
                color = CyanTelemetry,
                containerColor = CyanContainer,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TelemetryStat(
                label = "DISPOSITIVO",
                value = "${t.manufacturer} ${t.model}".ifBlank { "Detectando..." },
                modifier = Modifier.weight(1f),
            )
            TelemetryStat(
                label = "SISTEMA OS",
                value = if (t.sdkInt > 0) "Android ${t.androidVersion} (API ${t.sdkInt})" else "—",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Partición Interna (/data)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                text = "${SystemSanitizerEngine.formatBytes(t.usedStorageBytes)} usados / ${SystemSanitizerEngine.formatBytes(t.totalStorageBytes)} total",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        StorageBar(usedBytes = t.usedStorageBytes, totalBytes = t.totalStorageBytes)

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Disponible: ${SystemSanitizerEngine.formatBytes(t.availableStorageBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldVerified,
            )
            Text(
                text = "Caché local: ${SystemSanitizerEngine.formatBytes(t.appCacheBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }

        if (t.dfOutputSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianSurfaceElevated,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$ df -h /data -> ${t.dfOutputSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TelemetryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SanitizationProfileCard(
    selectedMode: SanitizationPassMode,
    onSelectMode: (SanitizationPassMode) -> Unit,
) {
    TvSurfaceCard(testTag = "sanitization_profile_card") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = CyanTelemetry,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Método de Sanitización de Bloques",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SanitizationPassMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                TvSurfaceCard(
                    onClick = { onSelectMode(mode) },
                    accentBorder = if (isSelected) CyanTelemetry else ObsidianBorder,
                    containerColor = if (isSelected) CyanContainer.copy(alpha = 0.55f) else ObsidianSurfaceElevated,
                    testTag = "pass_mode_${mode.name.lowercase()}",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(
                            text = if (isSelected) "ACTIVO" else "${mode.passCount}x",
                            color = if (isSelected) CyanTelemetry else TextMuted,
                            containerColor = if (isSelected) CyanContainer else ObsidianBg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafFoldersCard(
    uiState: MainUiState,
    onPickSafFolder: () -> Unit,
    onRemoveSafFolder: (String) -> Unit,
) {
    TvSurfaceCard(testTag = "saf_folders_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderDelete,
                    contentDescription = null,
                    tint = CyanTelemetry,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = "Directorios Personales Autorizados (SAF)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Opcional: seleccione carpetas externas/USB/Descargas para triturado previo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.settings.authorizedFolders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.settings.authorizedFolders.forEach { folder ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TvSecondaryButton(
                                text = "Quitar",
                                onClick = { onRemoveSafFolder(folder.uriString) },
                                accentColor = CrimsonDestructive,
                                testTag = "remove_saf_folder_button",
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        TvSecondaryButton(
            text = "Vincular Carpeta para Triturado Seguro",
            icon = Icons.Default.FolderDelete,
            onClick = onPickSafFolder,
            modifier = Modifier.fillMaxWidth(),
            testTag = "add_saf_folder_button",
        )
    }
}

@Composable
private fun SecurityActionPanel(
    uiState: MainUiState,
    onStartDeepWipe: () -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenSettingsHistory: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
) {
    val hasPin = uiState.settings.hasPinConfigured

    TvSurfaceCard(
        accentBorder = CrimsonDestructive.copy(alpha = 0.55f),
        testTag = "security_action_panel",
    ) {
        Text(
            text = "Control de Seguridad y Ejecución",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // PIN Status Row
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (hasPin) EmeraldContainer else AmberContainer,
            border = BorderStroke(
                1.dp,
                if (hasPin) EmeraldVerified.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (hasPin) Icons.Default.VerifiedUser else Icons.Default.Key,
                    contentDescription = null,
                    tint = if (hasPin) EmeraldVerified else AmberWarning,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPin) "Protección PIN Activa" else "PIN No Configurado",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (hasPin) {
                            "Verificación SHA-256 obligatoria antes de cualquier borrado."
                        } else {
                            "Cree un PIN de 4–6 dígitos para evitar ejecuciones accidentales."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TvSecondaryButton(
            text = stringResource(
                if (hasPin) R.string.action_change_pin else R.string.action_configure_pin
            ),
            icon = Icons.Default.Key,
            onClick = onOpenPinSetup,
            modifier = Modifier.fillMaxWidth(),
            testTag = "configure_pin_button",
        )

        if (!uiState.telemetry.isAdminActive && !uiState.telemetry.isDeviceOwner) {
            Spacer(modifier = Modifier.height(10.dp))
            TvSecondaryButton(
                text = stringResource(R.string.action_activate_admin),
                icon = Icons.Default.AdminPanelSettings,
                accentColor = AmberWarning,
                onClick = onRequestDeviceAdmin,
                modifier = Modifier.fillMaxWidth(),
                testTag = "activate_device_admin_button",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TvPrimaryButton(
            text = stringResource(R.string.action_prepare_reset),
            icon = Icons.Default.DeleteForever,
            isDestructive = true,
            onClick = onStartDeepWipe,
            modifier = Modifier.fillMaxWidth(),
            testTag = "prepare_deep_wipe_button",
        )

        Spacer(modifier = Modifier.height(10.dp))

        TvSecondaryButton(
            text = "Registros de Verificación y Ajustes (${uiState.settings.verificationHistory.size})",
            icon = Icons.Default.History,
            onClick = onOpenSettingsHistory,
            modifier = Modifier.fillMaxWidth(),
            testTag = "open_history_settings_button",
        )
    }
}
