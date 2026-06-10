package com.byron.trucaller.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.GlassBorder

/**
 * A themed text field composable with consistent styling across light and dark modes.
 *
 * All colors are derived from [MaterialTheme.colorScheme] so the field
 * adapts automatically to the current theme.
 *
 * @param value Current text value.
 * @param onValueChange Callback when the text changes.
 * @param modifier Modifier applied to the text field.
 * @param placeholder Placeholder text displayed when the field is empty.
 * @param label Optional label text displayed above the field.
 * @param leadingIcon Optional leading icon.
 * @param trailingIcon Optional trailing icon. When [showClearButton] is true
 *   and [value] is not empty, a clear button is shown instead.
 * @param trailingContent Optional custom trailing composable, overrides [trailingIcon] when set.
 * @param showClearButton When true and the field has text, a clear-button icon
 *   replaces [trailingIcon].
 * @param onClear Callback invoked when the clear button is pressed.
 * @param prefix Optional prefix composable (e.g. country code for phone fields).
 * @param isSearch When true, applies search-field defaults: search leading icon,
 *   single line, [ImeAction.Search], and a compact 48dp height.
 * @param singleLine Whether the field is single-line.
 * @param maxLines Maximum number of visible lines.
 * @param minLines Minimum number of visible lines.
 * @param enabled Whether the field is enabled.
 * @param readOnly Whether the field is read-only.
 * @param isError Whether the field should display an error state.
 * @param errorMessage Optional error message shown below the field when [isError] is true.
 * @param keyboardType Software keyboard type.
 * @param imeAction IME action button.
 * @param onImeAction Callback invoked when the IME action is triggered.
 * @param visualTransformation Visual transformation (e.g. password masking).
 * @param contentDesc Accessibility content description.
 * @param fieldHeight Optional fixed height. Defaults to 48dp for search fields.
 */
@Composable
fun TruCallerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showClearButton: Boolean = false,
    onClear: (() -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    isSearch: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (isSearch) ImeAction.Search else ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    contentDesc: String = label ?: placeholder,
    fieldHeight: Dp? = if (isSearch) 52.dp else null
) {
    val colorScheme = MaterialTheme.colorScheme

    val effectiveLeadingIcon: ImageVector? = leadingIcon ?: if (isSearch) Icons.Default.Search else null

    val heightModifier = if (fieldHeight != null) Modifier.height(fieldHeight) else Modifier

    val resolvedTrailingIcon: (@Composable () -> Unit)? = when {
        trailingContent != null -> trailingContent
        showClearButton && value.isNotEmpty() -> {
            {
                IconButton(onClick = { onClear?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear text",
                        tint = colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        trailingIcon != null -> {
            {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        else -> null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(heightModifier)
            .semantics { this.contentDescription = contentDesc },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = colorScheme.onSurface.copy(alpha = 0.35f), fontSize = 14.sp) }
        } else null,
        label = if (label != null) {
            { Text(label) }
        } else null,
        leadingIcon = if (effectiveLeadingIcon != null) {
            {
                Icon(
                    imageVector = effectiveLeadingIcon,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else null,
        trailingIcon = resolvedTrailingIcon,
        prefix = prefix,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = if (isError && !errorMessage.isNullOrEmpty()) {
            { Text(errorMessage, color = colorScheme.error, fontSize = 12.sp) }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onSend = { onImeAction?.invoke() }
        ),
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = BrandGold
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = GlassBorder,
            focusedContainerColor = colorScheme.surfaceVariant,
            unfocusedContainerColor = colorScheme.surface,
            cursorColor = colorScheme.primary,
            focusedTextColor = BrandGold,
            unfocusedTextColor = BrandGold,
            errorBorderColor = colorScheme.error,
            errorCursorColor = colorScheme.error,
            disabledBorderColor = colorScheme.outline.copy(alpha = 0.38f),
            disabledTextColor = colorScheme.onSurface.copy(alpha = 0.38f),
            disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}
