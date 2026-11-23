package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.LogSeverity
import com.example.domain.PhaseOutcome
import com.example.domain.SystemLogEntry
import com.example.domain.SystemSanitizerEngine
import com.example.domain.WipePhase
import com.example.ui.MainUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDestructive
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ExecutionConsoleScreen(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
) {
    // Prevent accidental remote Back button exit while wiping is active
    BackHandler(enabled = uiState.wipeProgress.isRunning) { }

    val progress = uiState.wipeProgress
    val animatedProgress by animateFloatAsState(
        targetValue = progress.overallProgress,
        label = "wipeProgress",
    )
    val logListState = rememberLazyListState()

    LaunchedEffect(uiState.liveLogs.size) {
        if (uiState.liveLogs.isNotEmpty()) {
            logListState.animateScrollToItem(uiState.liveLogs.lastIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(20.dp),
    ) {
        val isWide = maxWidth >= 720.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top Progress Banner
            TvSurfaceCard(
                accentBorder = CrimsonDestructive.copy(alpha = 0.65f),
                testTag = "execution_progress_card",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EJECUTANDO SANITIZACIÓN Y RESETEO",
                            style = MaterialTheme.typography.labelMedium,
                            color = CrimsonDestructive,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progress.statusHeadline,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = CyanTelemetry,
                        containerColor = CyanContainer,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50)),
                    color = CrimsonDestructive,
                    trackColor = ObsidianSurfaceHighlight,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricBadge(
                        label = "SOBRESCRITO",
                        value = SystemSanitizerEngine.formatBytes(progress.bytesOverwritten),
                    )
                    MetricBadge(
                        label = "VELOCIDAD",
                        value = String.format(Locale.US, "%.1f MB/s", progress.throughputMbPerSec),
                    )
                    MetricBadge(
                        label = "PASADA",
                        value = if (progress.totalPasses > 0) "${progress.currentPass}/${progress.totalPasses}" else "—",
                    )
                    MetricBadge(
                        label = "TIEMPO",
                        value = "${progress.elapsedSeconds}s",
                    )
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PhaseListCard(
                        outcomes = progress.phaseOutcomes,
                        modifier = Modifier.weight(0.85f),
                    )
                    TerminalLogCard(
                        logs = uiState.liveLogs,
                        listState = logListState,
                        modifier = Modifier.weight(1.15f),
                    )
                }
            } else {
                PhaseListCard(outcomes = progress.phaseOutcomes)
                TerminalLogCard(
                    logs = uiState.liveLogs,
                    listState = logListState,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

@Composable
private fun PhaseListCard(
    outcomes: Map<WipePhase, PhaseOutcome>,
    modifier: Modifier = Modifier,
) {
    TvSurfaceCard(modifier = modifier, testTag = "execution_phases_card") {
        Text(
            text = "Pipeline de Ejecución",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WipePhase.entries.forEach { phase ->
                val outcome = outcomes[phase] ?: PhaseOutcome.PENDING
                PhaseRow(phase = phase, outcome = outcome)
            }
        }
    }
}

@Composable
private fun PhaseRow(phase: WipePhase, outcome: PhaseOutcome) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (outcome) {
            PhaseOutcome.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = CyanTelemetry,
            )
            PhaseOutcome.VERIFIED -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verificado",
                tint = EmeraldVerified,
                modifier = Modifier.size(18.dp),
            )
            PhaseOutcome.REQUIRES_MANUAL_SYSTEM_ACTION -> Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Acción del sistema requerida",
                tint = AmberWarning,
                modifier = Modifier.size(18.dp),
            )
            PhaseOutcome.SKIPPED_SAFE_MODE -> Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Omitido en modo seguro",
                tint = AmberWarning,
                modifier = Modifier.size(18.dp),
            )
            PhaseOutcome.FAILED -> Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = CrimsonDestructive,
                modifier = Modifier.size(18.dp),
            )
            PhaseOutcome.PENDING -> Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = "Pendiente",
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "${phase.index + 1}. ${phase.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (outcome == PhaseOutcome.PENDING) TextMuted else TextPrimary,
        )
    }
}

@Composable
private fun TerminalLogCard(
    logs: List<SystemLogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("terminal_log_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF05070C),
        border = BorderStroke(1.dp, ObsidianBorder),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyanTelemetry,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Consola de Comandos del Sistema (Tiempo Real)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanTelemetry,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogLineRow(entry = entry)
                }
            }
        }
    }
}

@Composable
fun LogLineRow(entry: SystemLogEntry) {
    val sevColor = when (entry.severity) {
        LogSeverity.COMMAND -> CyanTelemetry
        LogSeverity.OK -> EmeraldVerified
        LogSeverity.WARN -> AmberWarning
        LogSeverity.ERROR -> CrimsonDestructive
        LogSeverity.INFO -> TextSecondary
    }
    Text(
        text = "[${entry.timestamp}] ${entry.message}",
        style = MaterialTheme.typography.labelSmall,
        color = sevColor,
    )
}
