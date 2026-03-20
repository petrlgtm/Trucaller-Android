package com.byron.trucaller.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon

/**
 * Button style variants for [TruCallerButton].
 */
enum class TruCallerButtonStyle {
    /** Filled button with primary (yellow) background. */
    Primary,
    /** Outlined button with primary-colored border and text. */
    Secondary,
    /** Filled button with error (red) background for destructive actions. */
    Danger,
    /** Filled button with amber/orange background for warning-level actions. */
    Warning
}

/**
 * A reusable, themed button composable with support for loading state,
 * leading/trailing icons, and a press-scale animation.
 *
 * @param text The button label.
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier Modifier applied to the button.
 * @param style The visual style variant: [TruCallerButtonStyle.Primary],
 *   [TruCallerButtonStyle.Secondary], or [TruCallerButtonStyle.Danger].
 * @param enabled Whether the button is enabled.
 * @param isLoading When true, shows a progress indicator instead of the label.
 * @param leadingIcon An optional icon displayed before the text.
 * @param trailingIcon An optional icon displayed after the text.
 * @param iconSize Size of the leading/trailing icon.
 * @param contentDesc Accessibility content description. Defaults to [text].
 */
@Composable
fun TruCallerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TruCallerButtonStyle = TruCallerButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconSize: Dp = 18.dp,
    contentDesc: String = text
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "buttonScale"
    )

    val colorScheme = MaterialTheme.colorScheme

    val containerColor: Color
    val contentColor: Color
    val disabledContainerColor: Color
    val disabledContentColor: Color

    when (style) {
        TruCallerButtonStyle.Primary -> {
            containerColor = colorScheme.primary
            contentColor = colorScheme.onPrimary
            disabledContainerColor = colorScheme.primary.copy(alpha = 0.38f)
            disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.38f)
        }
        TruCallerButtonStyle.Danger -> {
            containerColor = colorScheme.error
            contentColor = colorScheme.onError
            disabledContainerColor = colorScheme.error.copy(alpha = 0.38f)
            disabledContentColor = colorScheme.onError.copy(alpha = 0.38f)
        }
        TruCallerButtonStyle.Warning -> {
            containerColor = Color(0xFFFF8F00) // Amber 800
            contentColor = Color.White
            disabledContainerColor = Color(0xFFFF8F00).copy(alpha = 0.38f)
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        }
        TruCallerButtonStyle.Secondary -> {
            // Handled by OutlinedButton below; values unused but required by exhaustive when.
            containerColor = Color.Transparent
            contentColor = colorScheme.primary
            disabledContainerColor = Color.Transparent
            disabledContentColor = colorScheme.primary.copy(alpha = 0.38f)
        }
    }

    val buttonModifier = modifier
        .scale(scale)
        .height(48.dp)
        .semantics { this.contentDescription = contentDesc }

    @Composable
    fun ButtonContent() {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = if (style == TruCallerButtonStyle.Secondary) colorScheme.primary else contentColor
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }

    if (style == TruCallerButtonStyle.Secondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.38f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colorScheme.primary,
                disabledContentColor = disabledContentColor
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            interactionSource = interactionSource
        ) {
            ButtonContent()
        }
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            interactionSource = interactionSource
        ) {
            ButtonContent()
        }
    }
}
