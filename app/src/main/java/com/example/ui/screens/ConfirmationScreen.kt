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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.PrivilegeLevel
import com.example.domain.SystemSanitizerEngine
import com.example.ui.MainUiState
import com.example.ui.components.PrivilegeBadge
import com.example.ui.components.TvPrimaryButton
import com.example.ui.components.TvSecondaryButton
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDestructive
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConfirmationScreen(
    uiState: MainUiState,
    onProceedToPin: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }

    val t = uiState.telemetry
    val isSafeMode = uiState.settings.safeVerificationMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        TvSurfaceCard(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            accentBorder = if (isSafeMode) AmberWarning else CrimsonDestructive,
            testTag = "confirmation_warning_card",
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSafeMode) AmberContainer else CrimsonContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSafeMode) Icons.Default.Warning else Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = if (isSafeMode) AmberWarning else CrimsonDestructive,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSafeMode) {
                            "Confirmar Sanitización en Modo Prueba Segura"
                        } else {
                            "Confirmar Reseteo Profundo Irreversible"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = "${t.manufacturer} ${t.model} — Android ${t.androidVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                PrivilegeBadge(level = t.privilegeLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, com.example.ui.theme.ObsidianBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "ALCANCE DE LA OPERACIÓN:",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanTelemetry,
                    )
                    ScopeBullet(
                        "Sobrescritura criptográfica (${uiState.settings.passMode.title}) sobre bloques de almacenamiento interno y verificación por lectura."
                    )
                    ScopeBullet(
                        "Purga de cachés locales (${SystemSanitizerEngine.formatBytes(t.appCacheBytes)}), archivos temporales y buffers de sistema."
                    )
                    if (uiState.settings.authorizedFolders.isNotEmpty()) {
                        ScopeBullet(
                            "Triturado y eliminación de ${uiState.settings.authorizedFolders.size} carpeta(s) SAF autorizada(s)."
                        )
                    }
                    ScopeBullet(
                        when {
                            isSafeMode ->
                                "Modo Prueba Segura activo: NO se ejecutará el reinicio destructivo de fábrica."
                            t.privilegeLevel == PrivilegeLevel.DEVICE_OWNER ||
                                t.privilegeLevel == PrivilegeLevel.DEVICE_ADMIN ->
                                "Ejecución de DevicePolicyManager.wipeData() para borrado profundo de fábrica."
                            t.privilegeLevel == PrivilegeLevel.ROOT_SU ->
                                "Ejecución de comando Root su MASTER_CLEAR / recovery --wipe_data."
                            else ->
                                "Como app estándar, tras la sanitización local se abrirá el acceso directo a Ajustes de Fábrica del TV para confirmar el borrado final de /data."
                        }
                    )
                    ScopeBullet(
                        "Emisión de Registro Final de Verificación con firma criptográfica SHA-256."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    testTag = "confirmation_cancel_button",
                )
                TvPrimaryButton(
                    text = "Continuar y Validar PIN",
                    icon = Icons.Default.DeleteForever,
                    isDestructive = !isSafeMode,
                    onClick = onProceedToPin,
                    modifier = Modifier.weight(1.3f),
                    testTag = "confirmation_proceed_pin_button",
                )
            }
        }
    }
}

@Composable
private fun ScopeBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircleOutline,
            contentDescription = null,
            tint = CyanTelemetry,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
    }
}
