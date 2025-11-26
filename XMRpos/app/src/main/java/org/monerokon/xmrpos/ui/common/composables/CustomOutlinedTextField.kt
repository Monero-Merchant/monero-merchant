package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private class PlaceholderTransformation(
    private val placeholder: String,
    private val placeholderStyle: TextStyle,
    private val defaultStyle: TextStyle
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return if (text.isEmpty()) {
            TransformedText(
                text = AnnotatedString(placeholder, spanStyle = placeholderStyle.toSpanStyle()),
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = 0
                    override fun transformedToOriginal(offset: Int): Int = 0
                }
            )
        } else {
            TransformedText(
                text = AnnotatedString(text.text, spanStyle = defaultStyle.toSpanStyle()),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }
}

/**
 * A custom styled OutlinedTextField that provides a consistent look and feel across the app.
 *
 * This composable wraps the standard [OutlinedTextField] and includes several key behaviors:
 * - A persistent placeholder that is always visible when the input value is empty,
 *   regardless of focus state.
 * - Consistent styling for colors, shape, and typography based on the [MaterialTheme].
 * - Compatibility with other [VisualTransformation]s, such as for password fields.
 *
 * @param value The input text to be shown in the text field.
 * @param onValueChange The callback that is triggered when the input service updates the text. The
 *   updated text comes as a `String`.
 * @param label The text to be displayed as the label for this text field.
 * @param modifier The [Modifier] to be applied to this text field.
 * @param enabled Controls the enabled state of the [CustomOutlinedTextField]. When `false`, the
 *   text field will not be interactive and will appear disabled.
 * @param keyboardOptions Software keyboard options that contain configuration such as
 *   [androidx.compose.ui.text.input.ImeAction] and [androidx.compose.ui.text.input.KeyboardType].
 * @param placeholder The text to be displayed as a placeholder when the input `value` is empty.
 * @param supportingText The optional supporting text to be displayed below the text field.
 * @param visualTransformation An optional [VisualTransformation] to apply to the input value,
 *   such as [androidx.compose.ui.text.input.PasswordVisualTransformation]. This will be
 *   correctly chained with the internal placeholder transformation.
 */
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val customColors = OutlinedTextFieldDefaults.colors(
        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
        focusedSupportingTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
    )

    val placeholderStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f))
    val defaultTextStyle = MaterialTheme.typography.labelSmall

    val placeholderTransformation = PlaceholderTransformation(
        placeholder = placeholder ?: "",
        placeholderStyle = placeholderStyle,
        defaultStyle = defaultTextStyle
    )

    // Manually create a chained VisualTransformation
    val finalTransformation = VisualTransformation { text ->
        // First, apply the external transformation (e.g., PasswordVisualTransformation)
        val passwordTransformed = visualTransformation.filter(text)
        // Then, apply our placeholder transformation to the result of the first one.
        placeholderTransformation.filter(passwordTransformed.text)
    }

    OutlinedTextField(
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
        textStyle = defaultTextStyle,
        visualTransformation = finalTransformation,
        keyboardOptions = keyboardOptions,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        supportingText = {
            if (!supportingText.isNullOrBlank()) {
                Text(text = supportingText)
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = customColors,
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}
