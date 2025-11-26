package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A custom styled [Switch] that provides a consistent look and feel across the app.
 *
 * This composable wraps the standard Material 3 [Switch] and applies a default set of colors
 * to match the app's specific design system for both checked and unchecked states.
 * It also applies a default size.
 *
 * @param checked whether or not this switch is checked.
 * @param onCheckedChange a callback to be invoked when the user toggles the switch.
 *   The new checked state is passed as a boolean.
 * @param modifier the [Modifier] to be applied to this switch.
 */
@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.surface,
            checkedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
            uncheckedThumbColor = MaterialTheme.colorScheme.secondaryContainer,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            uncheckedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
        ),
        modifier = modifier.width(56.dp).height(32.dp)
    )
}