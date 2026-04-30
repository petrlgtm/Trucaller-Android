package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import kotlin.math.absoluteValue

/**
 * Indicator type shown as a small dot on the avatar.
 */
enum class AvatarIndicator {
    /** Green online dot. */
    Online,
    /** Category-specific colored dot. */
    Category
}

/**
 * A circle avatar composable that displays initials on a gradient background
 * derived from the name hash, or an image when [imageUri] is provided.
 *
 * An optional indicator dot can be positioned at the bottom-end corner to
 * convey online status or category membership.
 *
 * @param name The display name used to generate initials and gradient colors.
 * @param modifier Modifier applied to the root container.
 * @param size Diameter of the avatar.
 * @param imageUri Optional file path for a contact photo. When non-null and
 *   the file exists, the image is displayed instead of initials.
 * @param fontSize Text size of the initials.
 * @param indicator Optional [AvatarIndicator] type for the corner dot.
 * @param indicatorColor Color of the indicator dot. Defaults to green for
 *   [AvatarIndicator.Online] and primary for [AvatarIndicator.Category].
 * @param contentDesc Accessibility content description.
 */
@Composable
fun TruCallerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    imageUri: String? = null,
    fontSize: TextUnit = (size.value / 2.8f).sp,
    indicator: AvatarIndicator? = null,
    indicatorColor: Color? = null,
    contentDesc: String = "$name avatar"
) {
    val colorScheme = MaterialTheme.colorScheme

    // Deterministic gradient colors derived from name hash
    val gradientPair = remember(name) {
        val backgroundColors = listOf(
            Color(0xFF0A2A4A), Color(0xFF3D0A0A), Color(0xFF0A330A),
            Color(0xFF3D2800), Color(0xFF1A0A3D), Color(0xFF003333),
            Color(0xFF3D3300), Color(0xFF330A3D)
        )
        val foregroundColors = listOf(
            Color(0xFF5DADE2), Color(0xFFE74C3C), Color(0xFF58D68D),
            Color(0xFFF5B041), Color(0xFFBB8FCE), Color(0xFF48C9B0),
            Color(0xFFF4D03F), Color(0xFFD2B4DE)
        )
        val index = name.hashCode().absoluteValue % backgroundColors.size
        Pair(backgroundColors[index], foregroundColors[index])
    }

    val initials = remember(name) {
        val parts = name.trim().split("\\s+".toRegex())
        when {
            parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
            name.isNotBlank() -> {
                val first = name.first()
                if (first.isDigit() || first == '+') "#"
                else first.uppercase()
            }
            else -> "?"
        }
    }

    val indicatorSize = (size.value / 4).dp
    val indicatorOffset = (size.value / 3.2f).dp

    Box(
        modifier = modifier.semantics { this.contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        // Avatar circle
        if (imageUri != null && File(imageUri).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(imageUri))
                    .crossfade(true)
                    .build(),
                contentDescription = contentDesc,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                gradientPair.first.copy(alpha = 0.9f),
                                gradientPair.first
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = gradientPair.second,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                )
            }
        }

        // Indicator dot
        if (indicator != null) {
            val dotColor = indicatorColor ?: when (indicator) {
                AvatarIndicator.Online -> Color(0xFF4CAF50) // Green
                AvatarIndicator.Category -> colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .offset(x = indicatorOffset, y = indicatorOffset)
                    .background(colorScheme.background, CircleShape)
                    .padding(2.dp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}
