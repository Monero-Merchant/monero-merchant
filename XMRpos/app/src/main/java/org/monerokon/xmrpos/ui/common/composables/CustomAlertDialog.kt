package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.monerokon.xmrpos.R

/**
 * A custom AlertDialog with controlled padding that does not fill the screen width.
 *
 * @param onDismissRequest Called when the user requests to dismiss the dialog, such as by
 *   tapping the scrim or pressing the back button.
 * @param onConfirmation Called when the user clicks the confirmation button.
 * @param onDismissButton Called when user presses the dismis button (overrides onDismissRequest for button only)
 * @param dialogTitle The title of the dialog.
 * @param dialogText The main body text of the dialog.
 * @param confirmButtonText The text for the confirmation button. If null, the button is not shown.
 * @param dismissButtonText The text for the dismiss button. If null, the button is not shown.
 * @param confirmButtonLoading If the confirm button is loading
 * @param dismissButtonLoading If the dismis button is loading
 * @param reverseButtonOrder Reverse the order of the buttons
 * @param icon The optional icon to be displayed at the top of the dialog.
 * @param showCloseX Show a clickable X in the upper right corner to make it clear that it can be closed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAlertDialog(
    onDismissRequest: (() -> Unit)? = null,
    onConfirmation: (() -> Unit)? = null,
    onDismissButton: (() -> Unit)? = null,
    dialogTitle: String,
    dialogText: String? = null,
    dialogContent: @Composable (() -> Unit)? = null,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null,
    confirmButtonLoading: Boolean = false,
    dismissButtonLoading: Boolean = false,
    reverseButtonOrder: Boolean = false,
    icon: @Composable (Modifier) -> Unit,
    showCloseX: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismissRequest ?: {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = AlertDialogDefaults.shape,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp).fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        icon(Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(dialogTitle, style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primaryContainer))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (dialogText != null) {
                        Text(dialogText, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)))
                    }

                    if (dialogContent != null) {
                        dialogContent()
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (reverseButtonOrder) {
                            when (LocalLayoutDirection.current) {
                                LayoutDirection.Ltr -> LayoutDirection.Rtl
                                LayoutDirection.Rtl -> LayoutDirection.Ltr
                            }
                        } else {
                            LocalLayoutDirection.current
                        }
                    ) {
                        Row {
                            if (dismissButtonText != null) {
                                Button(
                                    onClick = onDismissButton ?: onDismissRequest ?: {},
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                                    enabled = !dismissButtonLoading
                                ) {
                                    Text(dismissButtonText, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primaryContainer))
                                    AnimatedVisibility(
                                        visible = dismissButtonLoading,
                                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it })
                                    ) {
                                        Row {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (dismissButtonText != null && confirmButtonText != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (confirmButtonText != null) {
                                Button(
                                    onClick = onConfirmation ?: {},
                                    enabled = !confirmButtonLoading
                                ) {
                                    Text(confirmButtonText, style = MaterialTheme.typography.labelSmall)
                                    AnimatedVisibility(
                                        visible = confirmButtonLoading,
                                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it })
                                    ) {
                                        Row {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showCloseX) {
                    IconButton(
                        onClick = onDismissRequest ?: {},
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.close_24px),
                            tint = Color(0x33000000),
                            contentDescription = "Go back to previous screen",
                        )
                    }
                }
            }
        }
    }
}
