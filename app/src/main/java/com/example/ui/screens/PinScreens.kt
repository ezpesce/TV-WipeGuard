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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.MainUiState
import com.example.ui.PinSetupStep
import com.example.ui.components.HoldToConfirmDestructiveButton
import com.example.ui.components.PinDotIndicators
import com.example.ui.components.StatusPill
import com.example.ui.components.TvNumericKeypad
import com.example.ui.components.TvSecondaryButton
import com.example.ui.components.TvSurfaceCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDestructive
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PinSetupScreen(
    uiState: MainUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmitStep: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }

    val stepTitle = when (uiState.pinSetupStep) {
        PinSetupStep.VERIFY_CURRENT -> "Verificar PIN Actual"
        PinSetupStep.ENTER_NEW -> "Crear Nuevo PIN de Seguridad"
        PinSetupStep.CONFIRM_NEW -> "Confirmar Nuevo PIN"
    }
    val stepSubtitle = when (uiState.pinSetupStep) {
        PinSetupStep.VERIFY_CURRENT -> "Ingrese su PIN actual para autorizar el cambio."
        PinSetupStep.ENTER_NEW -> "Elija entre 4 y 6 dígitos. Se almacenará con hash SHA-256 salado."
        PinSetupStep.CONFIRM_NEW -> "Vuelva a introducir el nuevo PIN para confirmar."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        TvSurfaceCard(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            accentBorder = CyanTelemetry.copy(alpha = 0.5f),
            testTag = "pin_setup_card",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = CyanTelemetry,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stepSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))
                PinDotIndicators(
                    length = uiState.pinInput.length,
                    hasError = uiState.pinError != null,
                )

                if (uiState.pinError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.pinError,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonDestructive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pin_error_text"),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                TvNumericKeypad(
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                    onSubmit = onSubmitStep,
                    enabled = uiState.lockoutRemainingSeconds <= 0,
                    submitLabel = "OK",
                )

                Spacer(modifier = Modifier.height(18.dp))
                TvSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pin_setup_cancel_button",
                )
            }
        }
    }
}

@Composable
fun PinVerifyGateScreen(
    uiState: MainUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onVerifyPin: () -> Unit,
    onExecuteArmedWipe: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }

    val isLocked = uiState.lockoutRemainingSeconds > 0
    val isArmed = uiState.isPinArmedForExecution

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        TvSurfaceCard(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            accentBorder = if (isArmed) CrimsonDestructive else CyanTelemetry.copy(alpha = 0.55f),
            testTag = "pin_verify_card",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Default.VerifiedUser else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isArmed) EmeraldVerified else CrimsonDestructive,
                        modifier = Modifier.size(30.dp),
                    )
                    StatusPill(
                        text = if (isArmed) "PIN AUTORIZADO — LISTO" else "AUTORIZACIÓN POR PIN",
                        color = if (isArmed) EmeraldVerified else CyanTelemetry,
                        containerColor = if (isArmed) EmeraldContainer else CyanContainer,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isArmed) {
                        "Confirmación Final Antiacci­dental"
                    } else {
                        "Ingrese su PIN de Seguridad"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isArmed) {
                        "El PIN fue verificado. Mantenga pulsado el botón inferior (o pulse OK dos veces con el control remoto) para iniciar la sanitización y el reseteo."
                    } else {
                        "Este paso impide cualquier borrado accidental desde el control remoto del televisor."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))
                PinDotIndicators(
                    length = uiState.pinInput.length,
                    hasError = uiState.pinError != null,
                    isArmed = isArmed,
                )

                if (isLocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CrimsonContainer,
                        border = BorderStroke(1.dp, CrimsonDestructive),
                    ) {
                        Text(
                            text = "Bloqueo temporal activo: espere ${uiState.lockoutRemainingSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                } else if (uiState.pinError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.pinError,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonDestructive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pin_verify_error_text"),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!isArmed) {
                    TvNumericKeypad(
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        onSubmit = onVerifyPin,
                        enabled = !isLocked,
                        submitLabel = "VALIDAR",
                    )
                } else {
                    HoldToConfirmDestructiveButton(
                        text = "Mantener pulsado para Ejecutar Borrado",
                        onConfirmed = onExecuteArmedWipe,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TvSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pin_verify_cancel_button",
                )
            }
        }
    }
}
