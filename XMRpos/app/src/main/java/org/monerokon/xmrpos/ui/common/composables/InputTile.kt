package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.monerokon.xmrpos.R

object InputTile {

    /**
     * A base layout for all input tiles, providing a consistent two-column row structure.
     *
     * It establishes a common layout with a
     * slot for content on the left and a slot for content on the right, with each taking up
     * equal space.
     *
     * @param modifier The [Modifier] to be applied to the entire tile.
     * @param contentLeft The composable content for the left-hand side of the tile.
     * @param contentRight The composable content for the right-hand side of the tile.
     */
    @Composable
    fun Base(
        modifier: Modifier = Modifier,
        contentLeft: @Composable () -> Unit,
        contentRight: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    contentLeft()
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    contentRight()
                }
            }
        }
    }

    /**
     * A composable that displays the styled label text for an [InputTile].
     *
     * It standardizes the typography and padding for all tile labels.
     *
     * @param label The string to be displayed as the label.
     * @param modifier The [Modifier] to be applied to the Text composable.
     */
    @Composable
    fun Label(
        label: String,
        modifier: Modifier = Modifier,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = modifier.padding(start = 6.dp))
    }

    /**
     * A styled, low-level text input field used within the [InputTile] ecosystem.
     *
     * This composable is built on [BasicTextField] to provide full control over styling,
     * including background, height, and internal padding, while disabling the default
     * Material component styling. It's designed to be used as the `contentRight` for other tiles.
     *
     * @param value The current text value to be displayed in the text field.
     * @param onValueChange The callback that is triggered when the input service updates the text.
     * @param modifier The [Modifier] to be applied to the text field.
     * @param prefix An optional string to be displayed as a prefix inside the text field.
     * @param visualTransformation An optional [VisualTransformation] to apply to the input, such as for passwords.
     * @param keyboardOptions Software keyboard options for the text field.
     * @param enabled Controls the enabled state of the text field.
     */
    @Composable
    fun TextInput(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        prefix: String?,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        enabled: Boolean = true,
    ) {
        BasicTextField(
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                )
                .height(37.dp),
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textDirection = TextDirection.Rtl,
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.padding(all = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)){
                        if (prefix != null) {
                            Text(
                                text = prefix,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f)
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    innerTextField()
                }
            }
        )
    }

    /**
     * An [InputTile] that combines a label with a text input field.
     *
     * This composable arranges a label on the left and a [TextInput] on the right,
     * creating a complete row for text-based user input.
     *
     * @param value The current text value for the input field.
     * @param onValueChange The callback for when the text value changes.
     * @param label The descriptive label for the input field.
     * @param modifier The [Modifier] to be applied to the entire tile.
     * @param prefix An optional string to be displayed as a prefix inside the text field.
     * @param visualTransformation An optional [VisualTransformation] for the input field.
     * @param keyboardOptions Software keyboard options for the input field.
     * @param enabled Controls the enabled state of the input field.
     */
    @Composable
    fun Text(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        prefix: String?,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        enabled: Boolean = true,
    ) {
        Base(
            modifier = modifier,
            contentLeft = { Label(label = label) },
            contentRight = {
                TextInput(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    prefix = prefix,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    enabled = enabled
                )
            }
        )
    }

    /**
     * A styled, low-level dropdown menu box used within the [InputTile] ecosystem.
     *
     * This composable is built on [ExposedDropdownMenuBox] to provide a custom appearance
     * for the dropdown anchor, including a prefix and the currently selected value.
     * It's designed to be used as the `contentRight` for other tiles.
     *
     * @param value The currently selected item value to be displayed.
     * @param items The list of string items to display in the dropdown menu.
     * @param onItemSelected The callback triggered when an item is selected.
     * @param modifier The [Modifier] to be applied to the dropdown box.
     * @param prefix An optional string prefix to display above the selected value.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownInput(
        value: String,
        items: List<String>,
        onItemSelected: (String) -> Unit,
        modifier: Modifier = Modifier,
        prefix: String?,
        padding: PaddingValues = PaddingValues(10.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {expanded = !expanded}, modifier = modifier) {
            Surface(
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                content = {
                    Box(modifier = Modifier.padding(padding)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                if (prefix != null) {
                                    Text(prefix, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f)))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(value, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onBackground))
                                }
                            }
                            if (expanded) {
                                Icon(painter = painterResource(R.drawable.keyboard_arrow_up_24px), tint = MaterialTheme.colorScheme.onBackground, contentDescription = "Arrow up")
                            } else {
                                Icon(painter = painterResource(R.drawable.keyboard_arrow_down_24px), tint = MaterialTheme.colorScheme.onBackground, contentDescription = "Arrow down")
                            }
                        }
                    }
                }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {expanded = false},
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item, style = MaterialTheme.typography.labelSmall) },
                        onClick = {
                            expanded = false
                            onItemSelected(item)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }

    /**
     * An [InputTile] that combines a label with a dropdown menu for item selection.
     *
     * This composable arranges a label on the left and a [DropdownInput] on the right,
     * creating a complete row for selecting an option from a list.
     *
     * @param value The currently selected item value to be displayed.
     * @param items The list of string items to display in the dropdown menu.
     * @param onItemSelected The callback that is triggered when an item is selected.
     * @param label The descriptive label for the dropdown.
     * @param modifier The [Modifier] to be applied to the entire tile.
     * @param prefix An optional string prefix to display above the selected value in the dropdown box.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Dropdown(
        value: String,
        items: List<String>,
        onItemSelected: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        prefix: String?,
    ) {
        Base(
            modifier = modifier,
            contentLeft = { Label(label = label) },
            contentRight = {
                DropdownInput(
                    value = value,
                    items = items,
                    onItemSelected = onItemSelected,
                    modifier = modifier,
                    prefix = prefix
                )
            }
        )
    }
}
