package com.byron.trucaller.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.Spacing

// ── Shimmer Brush ────────────────────────────────────────────────────────────

/**
 * Creates an animated shimmer brush that sweeps from left to right.
 * Uses [rememberInfiniteTransition] for a smooth 60fps animation loop.
 */
@Composable
private fun shimmerBrush(
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    widthPx: Float = 1200f
): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateX by infiniteTransition.animateFloat(
        initialValue = -widthPx,
        targetValue = widthPx * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslateX"
    )

    return Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(x = translateX, y = 0f),
        end = Offset(x = translateX + widthPx, y = 0f)
    )
}

// ── ShimmerLoadingList ───────────────────────────────────────────────────────

/**
 * Shimmer placeholder rows for list loading states (call log, messages, contacts).
 *
 * @param itemCount Number of shimmer placeholder rows to display.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun ShimmerLoadingList(
    modifier: Modifier = Modifier,
    itemCount: Int = 8
) {
    val brush = shimmerBrush()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading content" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        userScrollEnabled = false
    ) {
        items(itemCount) {
            ShimmerListRow(brush = brush)
        }
    }
}

@Composable
private fun ShimmerListRow(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(brush)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(BorderRadius.sm))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            // Subtitle line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(BorderRadius.sm))
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        // Trailing timestamp placeholder
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(BorderRadius.sm))
                .background(brush)
        )
    }
}

// ── ShimmerLoadingCard ───────────────────────────────────────────────────────

/**
 * Single card shimmer placeholder for detail / profile screens.
 *
 * @param modifier Modifier applied to the card container.
 */
@Composable
fun ShimmerLoadingCard(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .clip(RoundedCornerShape(BorderRadius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.md)
            .semantics { contentDescription = "Loading details" }
    ) {
        // Header area with avatar and name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(BorderRadius.sm))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(BorderRadius.sm))
                        .background(brush)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Content lines
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 2) 0.5f else 0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(BorderRadius.sm))
                    .background(brush)
            )
            if (it < 2) {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Action button placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(40.dp)
                .clip(RoundedCornerShape(BorderRadius.md))
                .background(brush)
                .align(Alignment.CenterHorizontally)
        )
    }
}

// ── EmptyStateView ───────────────────────────────────────────────────────────

/**
 * Context-aware icon types for empty states.
 */
enum class EmptyStateIcon {
    CALLS,
    MESSAGES,
    CONTACTS,
    SEARCH,
    GENERIC
}

/**
 * Centered empty-state view with an illustration icon, title, subtitle,
 * and an optional action button.
 *
 * @param title Primary message (e.g., "No calls yet").
 * @param subtitle Secondary explanation text.
 * @param icon Which context icon to display.
 * @param actionLabel Label for the optional action button; null to hide.
 * @param onAction Callback when the action button is tapped.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: EmptyStateIcon = EmptyStateIcon.GENERIC,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imageVector: ImageVector = when (icon) {
        EmptyStateIcon.CALLS -> Icons.Default.Phone
        EmptyStateIcon.MESSAGES -> Icons.Default.Chat
        EmptyStateIcon.CONTACTS -> Icons.Default.People
        EmptyStateIcon.SEARCH -> Icons.Default.SearchOff
        EmptyStateIcon.GENERIC -> Icons.Default.Inbox
    }

    val iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val iconBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = iconTint
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Subtitle
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        // Optional action button
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(BorderRadius.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── ErrorStateView ───────────────────────────────────────────────────────────

/**
 * Error state with an error icon, message, and a Retry button.
 *
 * @param message The error message to display.
 * @param onRetry Callback when the Retry button is tapped.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val errorBgColor = errorColor.copy(alpha = 0.08f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon circle
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(errorBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(44.dp),
                tint = errorColor
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Title
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Error message
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Retry button
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(BorderRadius.lg),
            colors = ButtonDefaults.buttonColors(
                containerColor = errorColor,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(
                text = "Retry",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── LoadingOverlay ───────────────────────────────────────────────────────────

/**
 * Full-screen semi-transparent overlay with a branded spinner and optional message.
 * Placed on top of existing content via a [Box] wrapper.
 *
 * @param message Optional text below the spinner (e.g., "Saving...").
 * @param modifier Modifier applied to the overlay container.
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .semantics { contentDescription = message ?: "Loading" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.size(44.dp)
            )

            if (message != null) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
