package com.byron.trucaller.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.ui.theme.MontserratFontFamily
import com.byron.trucaller.ui.theme.Spacing

@Composable
fun TruCallerHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @Suppress("UNUSED_PARAMETER") gradientColors: List<Color>? = null,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = MaterialTheme.colorScheme.primary,
    horizontalPadding: Dp = Spacing.screenHorizontal,
    verticalPadding: Dp = 24.dp,
    trailingContent: (@Composable () -> Unit)? = null,
    contentDesc: String = title
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .semantics { this.contentDescription = contentDesc; heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = MontserratFontFamily,
                color = titleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontFamily = MontserratFontFamily,
                    color = subtitleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        trailingContent?.invoke()
    }
}
