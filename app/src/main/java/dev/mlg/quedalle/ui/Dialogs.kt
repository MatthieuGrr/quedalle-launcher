package dev.mlg.quedalle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.ui.theme.QuedalleColors

@Composable
fun AppOptionsDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: (() -> Unit)?,
    onReorder: (() -> Unit)?,
    onHide: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
    onAppInfo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(app.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(app.packageName, fontSize = 10.sp, color = QuedalleColors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DialogOption(stringResource(if (app.isPinned) R.string.action_unpin else R.string.action_pin), onTogglePin)
                if (onRename != null) DialogOption(stringResource(R.string.action_rename), onRename)
                if (onReorder != null) DialogOption(stringResource(R.string.action_reorder), onReorder)
                if (onHide != null) DialogOption(stringResource(R.string.action_hide), onHide)
                DialogOption(stringResource(R.string.action_app_info), onAppInfo)
                if (onUninstall != null) {
                    DialogOption(stringResource(R.string.action_uninstall), onUninstall,
                        color = QuedalleColors.Danger)
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = QuedalleColors.Surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun RenameDialog(
    current: String,
    original: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_title), color = Color.White, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(original, color = QuedalleColors.TextMuted, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        focusedBorderColor   = QuedalleColors.BorderFocused,
                        unfocusedBorderColor = QuedalleColors.BorderIdle,
                        cursorColor          = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.rename_hint), color = QuedalleColors.TextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = QuedalleColors.Surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun TileColorDialog(
    titleRes: Int,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorPicker(selected) { selected = it }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.action_remove), color = QuedalleColors.Danger, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = QuedalleColors.Surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorSwatch(QuedalleColors.TileTransparent, selected, onSelect, transparent = true)
            ColorSwatch(QuedalleColors.TileAppColor, selected, onSelect)
        }
        QuedalleColors.TilePresets.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { color -> ColorSwatch(color, selected, onSelect) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Int, onSelect: (Int) -> Unit, transparent: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .then(
                if (transparent) Modifier.drawBehind {
                    val cell = size.width / 4
                    for (row in 0..3) for (col in 0..3) drawRect(
                        color     = if ((row + col) % 2 == 0) QuedalleColors.CheckerDark else QuedalleColors.CheckerLight,
                        topLeft   = Offset(col * cell, row * cell),
                        size      = Size(cell, cell),
                    )
                } else Modifier.background(Color(color))
            )
            .then(
                if (color == selected)
                    Modifier.border(2.dp, Color.White, shape)
                else Modifier
            )
            .clickable { onSelect(color) }
    )
}

@Composable
internal fun DialogOption(label: String, onClick: () -> Unit, color: Color = Color.White) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}
