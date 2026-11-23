package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.SystemSanitizerEngine
import com.example.domain.VerificationReport
import com.example.ui.MainUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.TvSecondaryButton
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
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
fun SettingsAndHistoryScreen(
    uiState: MainUiState,
    packageName: String,
    onToggleSafeMode: (Boolean) -> Unit,
    onOpenReport: (VerificationReport) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Historial y Configuración Avanzada",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                TvSecondaryButton(
                    text = stringResource(R.string.action_back),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    testTag = "settings_back_button",
                )
            }

            // Safe Verification Mode Switch
            TvSurfaceCard(
                accentBorder = if (uiState.settings.safeVerificationMode) AmberWarning else ObsidianBorder,
                testTag = "safe_mode_settings_card",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Modo Verificación Segura (Dry-Run)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ejecuta la sobrescritura criptográfica real de bloques temporales y genera el registro SHA-256 sin invocar el reinicio destructivo de fábrica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = uiState.settings.safeVerificationMode,
                        onCheckedChange = onToggleSafeMode,
                        modifier = Modifier.testTag("safe_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = AmberWarning,
                        ),
                    )
                }
            }

            // Device Owner ADB Provisioning Guide
            TvSurfaceCard(testTag = "device_owner_guide_card") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CyanTelemetry,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Aprovisionamiento Device Owner para Reseteo Desatendido",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para que Android TV permita ejecutar DevicePolicyManager.wipeData() sin intervención en los menús del sistema, aprovisione la app vía ADB en un TV recién inicializado:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "adb shell dpm set-device-owner $packageName/.admin.TvDeviceAdminReceiver",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanTelemetry,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Verification History List
            TvSurfaceCard(testTag = "verification_history_card") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CyanTelemetry,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Registros de Verificación Recientes",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.settings.verificationHistory.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aún no hay registros de verificación guardados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.settings.verificationHistory.forEach { report ->
                            TvSurfaceCard(
                                onClick = { onOpenReport(report) },
                                containerColor = ObsidianSurfaceElevated,
                                testTag = "history_item_${report.reportId}",
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${report.reportId} • ${report.deviceSummary}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                        )
                                        Text(
                                            text = "${report.timestampIso} • ${SystemSanitizerEngine.formatBytes(report.bytesOverwritten)} sobrescritos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                        )
                                    }
                                    StatusPill(
                                        text = if (report.safeModeOnly) "PRUEBA" else "REAL",
                                        color = if (report.safeModeOnly) AmberWarning else EmeraldVerified,
                                        containerColor = if (report.safeModeOnly) AmberContainer else EmeraldContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
