package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.Spacing

/**
 * A consistent, themed card composable used throughout the app.
 *
 * Uses [MaterialTheme.colorScheme.surfaceVariant] as the default container
 * color, ensuring proper appearance in both light and dark themes.
 *
 * @param modifier Modifier applied to the card.
 * @param cornerRadius Corner radius of the card. Defaults to [BorderRadius.lg] (16dp).
 * @param elevation Default elevation of the card.
 * @param containerColor Background color of the card body.
 * @param gradientColors When provided, renders a gradient header strip above
 *   the card body using the given color stops.
 * @param gradientHeaderContent Optional composable content for the gradient header area.
 * @param content The main card body content.
 */
@Composable
fun TruCallerCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BorderRadius.lg,
    elevation: Dp = 2.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientColors: List<Color>? = null,
    gradientHeaderContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        // Optional gradient header
        if (gradientColors != null && gradientColors.size >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(colors = gradientColors),
                        shape = RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        )
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                gradientHeaderContent?.invoke()
            }
        }

        // Main card body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            content = content
        )
    }
}
