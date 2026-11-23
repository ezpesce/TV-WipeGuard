package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.PrivilegeLevel
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDestructive
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianBorderFocused
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHighlight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun TvSurfaceCard(
    modifier: Modifier = Modifier,
    accentBorder: Color = ObsidianBorder,
    containerColor: Color = ObsidianSurface,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused && onClick != null) ObsidianBorderFocused else accentBorder,
        animationSpec = spring(),
        label = "cardBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused && onClick != null) 1.015f else 1f,
        animationSpec = spring(),
        label = "cardScale",
    )

    val clickModifier = if (onClick != null) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isFocused && onClick != null) 2.dp else 1.dp, borderColor),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

@Composable
fun TvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    testTag: String = "primary_button",
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(),
        label = "btnScale",
    )
    val bgColor = if (isDestructive) CrimsonDestructive else CyanTelemetry
    val fgColor = if (isDestructive) TextPrimary else Color(0xFF05080E)
    val focusStroke = if (isFocused) BorderStroke(2.5.dp, TextPrimary) else null

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .onFocusChanged { isFocused = it.isFocused }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        border = focusStroke,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = fgColor,
            disabledContainerColor = ObsidianSurfaceHighlight,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = CyanTelemetry,
    enabled: Boolean = true,
    testTag: String = "secondary_button",
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(),
        label = "secBtnScale",
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .onFocusChanged { isFocused = it.isFocused }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) accentColor else ObsidianBorder,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isFocused) ObsidianSurfaceElevated else ObsidianSurface,
            contentColor = TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun PrivilegeBadge(level: PrivilegeLevel, modifier: Modifier = Modifier) {
    val (color, bg) = when (level) {
        PrivilegeLevel.DEVICE_OWNER -> EmeraldVerified to EmeraldContainer
        PrivilegeLevel.ROOT_SU -> CyanTelemetry to CyanContainer
        PrivilegeLevel.DEVICE_ADMIN -> AmberWarning to AmberContainer
        PrivilegeLevel.STANDARD_USER -> AmberWarning to AmberContainer
    }
    StatusPill(text = level.label, color = color, containerColor = bg, modifier = modifier)
}

@Composable
fun PinDotIndicators(
    length: Int,
    maxLength: Int = 6,
    hasError: Boolean = false,
    isArmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until maxLength) {
            val filled = i < length
            val dotColor = when {
                isArmed && filled -> EmeraldVerified
                hasError -> CrimsonDestructive
                filled -> CyanTelemetry
                else -> ObsidianBorder
            }
            Box(
                modifier = Modifier
                    .size(if (filled) 18.dp else 14.dp)
                    .clip(CircleShape)
                    .background(if (filled) dotColor else ObsidianSurfaceElevated)
                    .border(
                        width = 1.5.dp,
                        color = dotColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun TvNumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    submitLabel: String = "OK",
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("DEL", "0", "OK"),
    )

    Column(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Zero, Key.NumPad0 -> { onDigit('0'); true }
                Key.One, Key.NumPad1 -> { onDigit('1'); true }
                Key.Two, Key.NumPad2 -> { onDigit('2'); true }
                Key.Three, Key.NumPad3 -> { onDigit('3'); true }
                Key.Four, Key.NumPad4 -> { onDigit('4'); true }
                Key.Five, Key.NumPad5 -> { onDigit('5'); true }
                Key.Six, Key.NumPad6 -> { onDigit('6'); true }
                Key.Seven, Key.NumPad7 -> { onDigit('7'); true }
                Key.Eight, Key.NumPad8 -> { onDigit('8'); true }
                Key.Nine, Key.NumPad9 -> { onDigit('9'); true }
                Key.Backspace -> { onBackspace(); true }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { keyLabel ->
                    KeypadButton(
                        label = if (keyLabel == "OK") submitLabel else keyLabel,
                        isAction = keyLabel == "DEL" || keyLabel == "OK",
                        isConfirm = keyLabel == "OK",
                        enabled = enabled,
                        testTag = "keypad_${keyLabel.lowercase()}",
                        onClick = {
                            when (keyLabel) {
                                "DEL" -> onBackspace()
                                "OK" -> onSubmit()
                                else -> onDigit(keyLabel.first())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    isAction: Boolean,
    isConfirm: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(),
        label = "keyScale",
    )
    val bgColor = when {
        !enabled -> ObsidianSurface
        isConfirm -> CyanContainer
        isFocused -> ObsidianSurfaceHighlight
        else -> ObsidianSurfaceElevated
    }
    val borderColor = when {
        isFocused -> ObsidianBorderFocused
        isConfirm -> CyanTelemetry.copy(alpha = 0.6f)
        else -> ObsidianBorder
    }

    Surface(
        modifier = Modifier
            .size(width = 86.dp, height = 58.dp)
            .scale(scale)
            .minimumInteractiveComponentSize()
            .onFocusChanged { isFocused = it.isFocused }
            .testTag(testTag),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label == "DEL") {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Borrar dígito",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            } else if (isConfirm) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = CyanTelemetry,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanTelemetry,
                    )
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = if (isAction) FontWeight.Medium else FontWeight.Bold,
                    ),
                    color = if (enabled) TextPrimary else TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Botón de seguridad anti-ejecución accidental compatible tanto con pulsación táctil sostenida
 * como con pulsación sostenida o doble confirmación desde control remoto D-pad de Android TV.
 */
@Composable
fun HoldToConfirmDestructiveButton(
    text: String,
    holdDurationMs: Long = 2200L,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "hold_to_confirm_wipe_button",
) {
    var isHolding by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var dpadArmedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val stepMs = 30L
            var elapsed = 0L
            while (isHolding && elapsed < holdDurationMs) {
                delay(stepMs)
                elapsed += stepMs
                progress = (elapsed.toFloat() / holdDurationMs.toFloat()).coerceIn(0f, 1f)
            }
            if (isHolding && progress >= 1f) {
                isHolding = false
                progress = 0f
                onConfirmed()
            }
        } else {
            progress = 0f
        }
    }

    val borderColor = when {
        isFocused -> TextPrimary
        isHolding -> CrimsonBright
        else -> CrimsonDestructive
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CrimsonContainer)
            .border(if (isFocused) 2.5.dp else 1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) dpadArmedOnce = false
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val isSelectKey = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        if (!isHolding) {
                            isHolding = true
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (isHolding && progress < 1f) {
                            isHolding = false
                            // Also allow deliberate double-press on TV remote OK if user taps briefly
                            if (!dpadArmedOnce) {
                                dpadArmedOnce = true
                            } else {
                                dpadArmedOnce = false
                                onConfirmed()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        try {
                            awaitRelease()
                        } finally {
                            isHolding = false
                        }
                    },
                )
            }
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(CrimsonDestructive, CrimsonBright),
                    )
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isHolding) Icons.Default.Fingerprint else Icons.Default.Lock,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    isHolding -> "Mantenga pulsado... ${(progress * 100).toInt()}%"
                    dpadArmedOnce -> "Pulse OK otra vez para confirmar ejecución"
                    else -> text
                },
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
        }
    }
}

@Composable
fun StorageBar(
    usedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val ratio = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { ratio },
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50)),
        color = if (ratio > 0.85f) AmberWarning else CyanTelemetry,
        trackColor = ObsidianSurfaceHighlight,
    )
}
