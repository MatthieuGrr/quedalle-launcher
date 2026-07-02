package dev.mlg.quedalle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.TEXTURE_NONE
import dev.mlg.quedalle.model.TEXT_COLOR_AUTO
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.QuedalleColors
import dev.mlg.quedalle.ui.theme.Textures
import dev.mlg.quedalle.ui.theme.resolveTileColor
import dev.mlg.quedalle.ui.theme.resolveTileTextColor
import dev.mlg.quedalle.viewmodel.LauncherViewModel

/**
 * The single long-press sheet: a live preview of the tile with a pencil
 * that unfolds name & style editing, then the tile's actions, then the
 * launcher-wide entries (grid editing, settings). Everything applies
 * immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSheet(
    tile: TileItem,
    vm: LauncherViewModel,
    onEnterEdit: () -> Unit,
    onOpenSettings: () -> Unit,
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
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tile) {
                is TileItem.App     -> AppSheetContent(tile, vm, onDismiss)
                is TileItem.Spacer  -> SpacerSheetContent(tile, vm, onDismiss)
                is TileItem.Divider -> DividerSheetContent(tile, vm, onDismiss)
            }

            // ── Launcher-wide entries ─────────────────────────────────────────
            HorizontalDivider(color = palette.borderIdle)
            SheetOption(stringResource(R.string.action_reorder)) { onEnterEdit() }
            SheetOption(stringResource(R.string.action_settings)) { onOpenSettings() }
        }
    }
}

@Composable
private fun AppSheetContent(
    tile: TileItem.App,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    val app = tile.info

    if (app.isPinned) {
        var editing by remember(tile.id) { mutableStateOf(false) }
        var name by remember(tile.id) { mutableStateOf(app.customLabel ?: app.label) }

        TilePreviewCard(
            background = tile.style.background ?: QuedalleColors.TileAppColor,
            textColor = tile.style.textColor,
            texture = tile.style.texture,
            editing = editing,
            onToggleEditing = { editing = !editing },
        ) { previewText ->
            if (editing) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            } else {
                Text(
                    text = if (name.isBlank()) app.label else name,
                    color = previewText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        Text(
            app.packageName,
            color = palette.textMuted, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (editing) {
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
                onClick = { vm.resetTileStyle(tile.id); name = app.label },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text(stringResource(R.string.action_reset), fontSize = 12.sp) }
        }
    } else {
        Text(app.displayLabel, color = palette.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(app.packageName, color = palette.textMuted, fontSize = 10.sp)
    }

    // ── Tile actions ──────────────────────────────────────────────────────────
    HorizontalDivider(color = palette.borderIdle)
    SheetOption(stringResource(if (app.isPinned) R.string.action_unpin else R.string.action_pin)) {
        vm.togglePin(app); onDismiss()
    }
    if (!app.isPinned) {
        SheetOption(stringResource(R.string.action_hide)) { vm.hideApp(app); onDismiss() }
    }
    SheetOption(stringResource(R.string.action_app_info)) { vm.openAppInfo(app); onDismiss() }
    if (!app.isSystemApp) {
        SheetOption(stringResource(R.string.action_uninstall), color = palette.danger) {
            vm.requestUninstall(app); onDismiss()
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
    var editing by remember(tile.id) { mutableStateOf(false) }

    TilePreviewCard(
        background = tile.color,
        textColor = null,
        texture = tile.texture,
        editing = editing,
        onToggleEditing = { editing = !editing },
    ) { }

    if (editing) {
        AppearanceControls(
            background = tile.color,
            textColor = null,
            texture = tile.texture,
            showTextSection = false,
            onBackground = { vm.setTileBackground(tile.id, it) },
            onTextColor = {},
            onTexture = { vm.setTileTexture(tile.id, it ?: TEXTURE_NONE) },
        )
    }

    HorizontalDivider(color = palette.borderIdle)
    SheetOption(stringResource(R.string.action_remove), color = palette.danger) {
        vm.removeTile(tile.id); onDismiss()
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
    SheetOption(stringResource(R.string.action_remove), color = palette.danger) {
        vm.removeTile(tile.id); onDismiss()
    }
}

/** The live tile preview with the pencil toggle in its corner. */
@Composable
private fun TilePreviewCard(
    background: Int,
    textColor: Int?,
    texture: String?,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    content: @Composable (previewText: Color) -> Unit,
) {
    val base = resolveTileColor(background)
    val brush = Textures.brush(texture, base)
    val previewText = resolveTileTextColor(TileStyle(background, textColor, texture), base)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 168.dp, height = 84.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(base)),
            contentAlignment = Alignment.Center,
        ) {
            content(previewText)

            IconButton(
                onClick = onToggleEditing,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_edit_style),
                    tint = if (editing) MaterialTheme.colorScheme.primary
                           else previewText.copy(alpha = 0.75f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetOption(label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val palette = LocalQuedallePalette.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = if (color == Color.Unspecified) palette.textPrimary else color,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Swallows upward scroll leftovers so they never drag the sheet itself. */
private object BlockUpwardOverscroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
}
