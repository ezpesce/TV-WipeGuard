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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.FactoryResetOutcome
import com.example.domain.PhaseOutcome
import com.example.domain.SystemSanitizerEngine
import com.example.domain.VerificationReport
import com.example.domain.WipePhase
import com.example.ui.components.StatusPill
import com.example.ui.components.TvPrimaryButton
import com.example.ui.components.TvSecondaryButton
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
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
fun VerificationReportScreen(
    report: VerificationReport,
    packageName: String,
    onExportReport: (VerificationReport) -> Unit,
    onOpenSystemResetSettings: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBackToDashboard() }

    val clipboardManager = LocalClipboardManager.current
    var showRawLogs by remember { mutableStateOf(false) }

    val needsOsSettings = report.factoryResetOutcome == FactoryResetOutcome.REQUIRES_OS_SETTINGS
    val isFullyReset = report.factoryResetOutcome == FactoryResetOutcome.DISPATCHED_DEVICE_OWNER ||
        report.factoryResetOutcome == FactoryResetOutcome.DISPATCHED_DEVICE_ADMIN ||
        report.factoryResetOutcome == FactoryResetOutcome.DISPATCHED_ROOT_SHELL

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
            // Certificate Header Card
            TvSurfaceCard(
                accentBorder = when {
                    isFullyReset -> EmeraldVerified
                    needsOsSettings -> AmberWarning
                    else -> CyanTelemetry
                },
                testTag = "verification_certificate_card",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (needsOsSettings || report.safeModeOnly) {
                                Icons.Default.Warning
                            } else {
                                Icons.Default.Verified
                            },
                            contentDescription = null,
                            tint = if (isFullyReset) EmeraldVerified else AmberWarning,
                            modifier = Modifier.size(34.dp),
                        )
                        Column {
                            Text(
                                text = "Registro Final de Verificación",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                            )
                            Text(
                                text = "ID: ${report.reportId}  •  ${report.timestampIso}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanTelemetry,
                            )
                        }
                    }

                    StatusPill(
                        text = if (isFullyReset) "VERIFICADO" else if (report.safeModeOnly) "PRUEBA SEGURA" else "ACCIÓN DEL TV",
                        color = if (isFullyReset) EmeraldVerified else AmberWarning,
                        containerColor = if (isFullyReset) EmeraldContainer else AmberContainer,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = report.detailNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Metrics grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReportMetricBox(
                        label = "SOBRESCRITO",
                        value = SystemSanitizerEngine.formatBytes(report.bytesOverwritten),
                        modifier = Modifier.weight(1f),
                    )
                    ReportMetricBox(
                        label = "ELEMENTOS PURGADOS",
                        value = "${report.filesShredded} archivos",
                        modifier = Modifier.weight(1f),
                    )
                    ReportMetricBox(
                        label = "ESPACIO LIBRE FINAL",
                        value = SystemSanitizerEngine.formatBytes(report.freeStorageAfterBytes),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "FIRMA CRIPTOGRÁFICA DE INTEGRIDAD (SHA-256)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                        Text(
                            text = report.integritySha256,
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldVerified,
                        )
                    }
                }
            }

            // If Android OS requires manual confirmation in System Settings, expose direct honest path
            if (needsOsSettings) {
                TvSurfaceCard(
                    accentBorder = AmberWarning.copy(alpha = 0.7f),
                    containerColor = AmberContainer.copy(alpha = 0.4f),
                    testTag = "os_reset_action_card",
                ) {
                    Text(
                        text = "Último Paso: Confirmar Formateo de Partición /data en el Televisor",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberWarning,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Android TV protege la partición del sistema frente a apps no firmadas como sistema o Device Owner. Pulse el botón inferior para abrir directamente Ajustes de Restablecimiento de Fábrica del televisor, o aprovisione la app como Device Owner vía ADB:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "adb shell dpm set-device-owner $packageName/.admin.TvDeviceAdminReceiver",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanTelemetry,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TvPrimaryButton(
                        text = stringResource(R.string.action_open_system_reset),
                        icon = Icons.Default.Settings,
                        isDestructive = true,
                        onClick = onOpenSystemResetSettings,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_tv_factory_settings_button",
                    )
                }
            }

            // Phase Breakdown Card
            TvSurfaceCard(testTag = "phase_verification_breakdown_card") {
                Text(
                    text = "Auditoría por Fase",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WipePhase.entries.forEach { phase ->
                        val outcome = report.phaseOutcomes[phase] ?: PhaseOutcome.PENDING
                        val (pillColor, pillBg, pillText) = when (outcome) {
                            PhaseOutcome.VERIFIED -> Triple(EmeraldVerified, EmeraldContainer, "VERIFICADO")
                            PhaseOutcome.SKIPPED_SAFE_MODE -> Triple(AmberWarning, AmberContainer, "MODO SEGURO")
                            PhaseOutcome.REQUIRES_MANUAL_SYSTEM_ACTION -> Triple(AmberWarning, AmberContainer, "AJUSTES OS")
                            PhaseOutcome.FAILED -> Triple(CrimsonDestructive, com.example.ui.theme.CrimsonContainer, "ERROR")
                            else -> Triple(TextMuted, ObsidianSurfaceElevated, outcome.name)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${phase.index + 1}. ${phase.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(text = pillText, color = pillColor, containerColor = pillBg)
                        }
                    }
                }
            }

            // Raw Command Log Toggle
            if (report.commandLogs.isNotEmpty()) {
                TvSecondaryButton(
                    text = if (showRawLogs) "Ocultar Consola de Comandos (${report.commandLogs.size})" else "Ver Consola de Comandos (${report.commandLogs.size})",
                    onClick = { showRawLogs = !showRawLogs },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "toggle_raw_logs_button",
                )
                if (showRawLogs) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF05070C),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            report.commandLogs.forEach { entry ->
                                LogLineRow(entry = entry)
                            }
                        }
                    }
                }
            }

            // Export & Navigation Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvSecondaryButton(
                    text = stringResource(R.string.action_export_report),
                    icon = Icons.Default.Download,
                    onClick = { onExportReport(report) },
                    modifier = Modifier.weight(1f),
                    testTag = "export_report_button",
                )
                TvSecondaryButton(
                    text = stringResource(R.string.action_copy_report),
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString("${report.reportId} | SHA256:${report.integritySha256}")
                        )
                        onCopiedNotice("Firma de verificación copiada al portapapeles.")
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "copy_report_button",
                )
            }

            TvPrimaryButton(
                text = "Volver al Panel Principal",
                onClick = onBackToDashboard,
                modifier = Modifier.fillMaxWidth(),
                testTag = "back_to_dashboard_button",
            )
        }
    }
}

@Composable
private fun ReportMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, ObsidianBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
    }
}
