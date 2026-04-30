package com.byron.trucaller.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.Spacing

@Composable
fun TruCallerCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 4.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    gradientColors: List<Color>? = null,
    gradientHeaderContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            border = BorderStroke(0.5.dp, GlassBorder)
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
                            topEnd = cornerRadius
                        )
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                gradientHeaderContent?.invoke()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            content = content
        )
    }
}
