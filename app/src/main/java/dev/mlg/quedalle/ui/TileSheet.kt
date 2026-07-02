package dev.mlg.quedalle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.TEXTURE_NONE
import dev.mlg.quedalle.model.TEXT_COLOR_AUTO
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.QuedalleColors
import dev.mlg.quedalle.ui.theme.Textures
import dev.mlg.quedalle.ui.theme.resolveTileColor
import dev.mlg.quedalle.ui.theme.resolveTileTextColor
import dev.mlg.quedalle.viewmodel.LauncherViewModel

/**
 * The single long-press editor: everything about a tile lives here and
 * every change applies immediately to the real grid behind the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSheet(
    tile: TileItem,
    vm: LauncherViewModel,
    onEnterEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // When the content already fits, a scroll attempt has nothing
                // to consume; without this the leftover upward delta reaches
                // the sheet's drag and it oscillates against its spring.
                // Downward deltas still pass through so swipe-to-dismiss works.
                .nestedScroll(BlockUpwardOverscroll)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tile) {
                is TileItem.App     -> AppSheetContent(tile, vm, onEnterEdit, onDismiss)
                is TileItem.Spacer  -> SpacerSheetContent(tile, vm, onDismiss)
                is TileItem.Divider -> DividerSheetContent(tile, vm, onDismiss)
            }
        }
    }
}

@Composable
private fun AppSheetContent(
    tile: TileItem.App,
    vm: LauncherViewModel,
    onEnterEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    val app = tile.info

    if (app.isPinned) {
        // Live preview of the tile; the name is edited directly on it and
        // any style change below is reflected here immediately.
        var name by remember(tile.id) { mutableStateOf(app.customLabel ?: app.label) }
        val previewBg = resolveTileColor(tile.style.background ?: QuedalleColors.TileAppColor)
        val previewBrush = Textures.brush(tile.style.texture, previewBg)
        val previewText = resolveTileTextColor(tile.style, previewBg)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 148.dp, height = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (previewBrush != null) Modifier.background(previewBrush)
                        else Modifier.background(previewBg)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        vm.renameTile(tile.id, it.takeIf { text -> text.isNotBlank() && text != app.label })
                    },
                    textStyle = TextStyle(
                        color = previewText,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(previewText),
                    maxLines = 2,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (name.isEmpty()) {
                                Text(
                                    app.label,
                                    color = previewText.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
        }
        Text(
            app.packageName,
            color = palette.textMuted, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        AppearanceControls(
            background = tile.style.background ?: QuedalleColors.TileAppColor,
            textColor = tile.style.textColor,
            texture = tile.style.texture,
            showTextSection = true,
            onBackground = { vm.setTileBackground(tile.id, it) },
            onTextColor = { vm.setTileTextColor(tile.id, it ?: TEXT_COLOR_AUTO) },
            onTexture = { vm.setTileTexture(tile.id, it ?: TEXTURE_NONE) },
        )

        TextButton(
            onClick = { vm.resetTileStyle(tile.id) },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        ) { Text(stringResource(R.string.action_reset), fontSize = 12.sp) }

        HorizontalDivider(color = palette.borderIdle)
    } else {
        Text(app.displayLabel, color = palette.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(app.packageName, color = palette.textMuted, fontSize = 10.sp)
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SheetAction(stringResource(if (app.isPinned) R.string.action_unpin else R.string.action_pin)) {
            vm.togglePin(app); onDismiss()
        }
        if (app.isPinned) {
            SheetAction(stringResource(R.string.action_reorder)) { onEnterEdit() }
        } else {
            SheetAction(stringResource(R.string.action_hide)) { vm.hideApp(app); onDismiss() }
        }
        SheetAction(stringResource(R.string.action_app_info)) { vm.openAppInfo(app); onDismiss() }
    }
    if (!app.isSystemApp) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SheetAction(stringResource(R.string.action_uninstall), color = palette.danger) {
                vm.requestUninstall(app); onDismiss()
            }
        }
    }
}

@Composable
private fun SpacerSheetContent(
    tile: TileItem.Spacer,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    Text(stringResource(R.string.dialog_spacer_title), color = palette.textStrong,
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

    AppearanceControls(
        background = tile.color,
        textColor = null,
        texture = tile.texture,
        showTextSection = false,
        onBackground = { vm.setTileBackground(tile.id, it) },
        onTextColor = {},
        onTexture = { vm.setTileTexture(tile.id, it ?: TEXTURE_NONE) },
    )

    HorizontalDivider(color = palette.borderIdle)
    Row(modifier = Modifier.fillMaxWidth()) {
        SheetAction(stringResource(R.string.action_remove), color = palette.danger) {
            vm.removeTile(tile.id); onDismiss()
        }
    }
}

@Composable
private fun DividerSheetContent(
    tile: TileItem.Divider,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    Text(stringResource(R.string.dialog_divider_title), color = palette.textStrong,
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

    ColorPicker(tile.color) { vm.setTileBackground(tile.id, it) }

    HorizontalDivider(color = palette.borderIdle)
    Row(modifier = Modifier.fillMaxWidth()) {
        SheetAction(stringResource(R.string.action_remove), color = palette.danger) {
            vm.removeTile(tile.id); onDismiss()
        }
    }
}

/** Swallows upward scroll leftovers so they never drag the sheet itself. */
private object BlockUpwardOverscroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
}

@Composable
private fun SheetAction(label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val palette = LocalQuedallePalette.current
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            label,
            color = if (color == Color.Unspecified) palette.textPrimary else color,
            fontSize = 13.sp,
        )
    }
}

/**
 * Home menu, opened by long-pressing empty space on the home screen.
 * Grid editing and settings are both top-level from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSheet(
    onReorder: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            HomeSheetOption(stringResource(R.string.action_reorder), onReorder)
            HomeSheetOption(stringResource(R.string.action_settings), onSettings)
        }
    }
}

@Composable
private fun HomeSheetOption(label: String, onClick: () -> Unit) {
    val palette = LocalQuedallePalette.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(label, color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
    }
}
