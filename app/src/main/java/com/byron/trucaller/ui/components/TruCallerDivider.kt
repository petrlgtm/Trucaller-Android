package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A themed divider line for separating list items or sections.
 *
 * Uses [MaterialTheme.colorScheme.outlineVariant] by default so the divider
 * is visible in both light and dark themes without hard-coded colors.
 *
 * @param modifier Modifier applied to the divider.
 * @param color Divider line color. Defaults to outlineVariant.
 * @param thickness Line thickness. Defaults to 0.5dp for a subtle rule.
 * @param startIndent Leading padding before the divider begins, useful for
 *   indenting to align with content that has a leading avatar or icon.
 * @param contentDesc Accessibility content description.
 */
@Composable
fun TruCallerDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 0.5.dp,
    startIndent: Dp = 0.dp,
    contentDesc: String = "Divider"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(thickness)
            .background(color)
            .semantics { this.contentDescription = contentDesc }
    )
}
