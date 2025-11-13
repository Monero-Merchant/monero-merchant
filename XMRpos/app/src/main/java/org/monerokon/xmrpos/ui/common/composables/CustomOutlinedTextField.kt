// In /app/src/main/java/org/monerokon/xmrpos/ui/common/composables/CustomTextField.kt

package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A custom styled OutlinedTextField that provides a consistent look and feel across the app.
 *
 * This composable wraps the standard OutlinedTextField and applies a default set of colors
 * and shapes defined by the app's design system.
 *
 * @param value The input text to be shown in the text field.
 * @param onValueChange The callback that is triggered when the input service updates the text.
 * @param modifier The [Modifier] to be applied to this text field.
 * @param label The optional label to be displayed inside the text field container.
 * @param placeholder The optional placeholder to be displayed when the text field is in focus and the input text is empty.
 * @param supportingText The optional supporting text to be displayed below the text field.
 */
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    supportingText: String,
) {
    // Define the custom colors in a stable, reusable way inside the composable
    val customColors = OutlinedTextFieldDefaults.colors(
        // Use theme colors for adaptability (light/dark mode)
        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.20f), // Use a slightly higher alpha for better visibility
        focusedSupportingTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.labelSmall,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        supportingText = {Text(supportingText)},
        shape = MaterialTheme.shapes.medium,
        colors = customColors,
        modifier = modifier.fillMaxWidth() // Apply fillMaxWidth by default
    )
}
