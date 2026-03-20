package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.ui.theme.Spacing

/**
 * A screen header composable with a gradient background, title, optional
 * subtitle, and optional trailing content (e.g. a badge or icon).
 *
 * Colors are derived from [MaterialTheme.colorScheme] so the component
 * works in both light and dark themes.
 *
 * @param title The main header title.
 * @param modifier Modifier applied to the header container.
 * @param subtitle Optional smaller text displayed below the title.
 * @param gradientColors Gradient color stops for the background. Defaults to a
 *   dark gradient from the surface towards the background.
 * @param titleColor Text color for the title. Defaults to primary.
 * @param subtitleColor Text color for the subtitle. Defaults to onSurface at
 *   reduced opacity.
 * @param horizontalPadding Horizontal padding inside the header.
 * @param verticalPadding Vertical padding inside the header.
 * @param trailingContent Optional composable placed at the end of the title row.
 * @param contentDesc Accessibility content description. Defaults to [title].
 */
@Composable
fun TruCallerHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    gradientColors: List<Color>? = null,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    horizontalPadding: Dp = Spacing.lg,
    verticalPadding: Dp = Spacing.md,
    trailingContent: (@Composable () -> Unit)? = null,
    contentDesc: String = title
) {
    val colorScheme = MaterialTheme.colorScheme

    val effectiveGradient = gradientColors ?: listOf(
        colorScheme.surface,
        colorScheme.background
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(colors = effectiveGradient)
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .semantics { this.contentDescription = contentDesc; heading() }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = subtitleColor,
                            fontSize = 13.sp
                        )
                    }
                }
                trailingContent?.invoke()
            }
        }
    }
}
