package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.ui.theme.BorderRadius

/**
 * Pre-defined badge types with color semantics.
 */
enum class BadgeType {
    /** Spam score badge in error/red tones. */
    Spam,
    /** Success or verification badge in green tones. */
    Success,
    /** Warning badge in primary/yellow tones. */
    Warning,
    /** Informational badge in surface-variant tones. */
    Info,
    /** Count badge with primary-tinted background. */
    Count,
    /** Custom — uses [color] and [backgroundColor] parameters directly. */
    Custom
}

/**
 * A pill-shaped badge composable for displaying spam scores, categories,
 * counts, or status labels.
 *
 * Colors adapt automatically based on [type], using [MaterialTheme.colorScheme]
 * tokens. For fully custom colors pass [BadgeType.Custom] with explicit
 * [color] and [backgroundColor].
 *
 * @param text The badge label.
 * @param modifier Modifier applied to the badge.
 * @param type Semantic type controlling the default color scheme.
 * @param icon Optional leading icon displayed before the text.
 * @param color Explicit text/icon color. Overrides the type default when set.
 * @param backgroundColor Explicit background color. Overrides the type default when set.
 * @param contentDesc Accessibility content description. Defaults to [text].
 */
@Composable
fun TruCallerBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: BadgeType = BadgeType.Info,
    icon: ImageVector? = null,
    color: Color? = null,
    backgroundColor: Color? = null,
    contentDesc: String = text
) {
    val colorScheme = MaterialTheme.colorScheme

    val resolvedColor = color ?: when (type) {
        BadgeType.Spam -> colorScheme.error
        BadgeType.Success -> Color(0xFF4CAF50)
        BadgeType.Warning -> colorScheme.primary
        BadgeType.Info -> colorScheme.onSurface.copy(alpha = 0.7f)
        BadgeType.Count -> colorScheme.primary
        BadgeType.Custom -> colorScheme.onSurface
    }

    val resolvedBg = backgroundColor ?: when (type) {
        BadgeType.Spam -> colorScheme.error.copy(alpha = 0.12f)
        BadgeType.Success -> Color(0xFF4CAF50).copy(alpha = 0.12f)
        BadgeType.Warning -> colorScheme.primary.copy(alpha = 0.15f)
        BadgeType.Info -> colorScheme.surfaceVariant
        BadgeType.Count -> colorScheme.primary.copy(alpha = 0.2f)
        BadgeType.Custom -> colorScheme.surfaceVariant
    }

    Row(
        modifier = modifier
            .background(resolvedBg, RoundedCornerShape(BorderRadius.full))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { this.contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = resolvedColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}
