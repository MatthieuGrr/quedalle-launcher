package dev.mlg.quedalle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.ProfileName
import dev.mlg.quedalle.data.ProfileState
import dev.mlg.quedalle.model.Gesture
import dev.mlg.quedalle.model.GestureAction
import dev.mlg.quedalle.viewmodel.LauncherUiState
import dev.mlg.quedalle.viewmodel.LauncherViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.BuildConfig
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.TileText
import kotlin.math.roundToInt
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.QuedalleColors

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    themeMode: ThemeMode,
    vm: LauncherViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQuedallePalette.current
    val globalStyle = state.globalStyle
    val tileText = state.tileText
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹", color = palette.textStrong, fontSize = 22.sp)
            }
            Text(
                stringResource(R.string.settings_title),
                color = palette.textStrong, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // ── Theme ─────────────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.settings_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeOption(stringResource(R.string.theme_system), themeMode == ThemeMode.SYSTEM) {
                vm.setThemeMode(ThemeMode.SYSTEM)
            }
            ThemeOption(stringResource(R.string.theme_dark), themeMode == ThemeMode.DARK) {
                vm.setThemeMode(ThemeMode.DARK)
            }
            ThemeOption(stringResource(R.string.theme_light), themeMode == ThemeMode.LIGHT) {
                vm.setThemeMode(ThemeMode.LIGHT)
            }
        }

        // ── Global tile style ─────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.settings_tile_style))
        TilePreview(
            label = "Aa",
            background = globalStyle.background ?: QuedalleColors.TileAppColor,
            textColor = globalStyle.textColor,
            texture = globalStyle.texture,
        )
        AppearanceControls(
            background = globalStyle.background ?: QuedalleColors.TileAppColor,
            textColor = globalStyle.textColor,
            texture = globalStyle.texture,
            showTextSection = true,
            onBackground = vm::setGlobalBackground,
            onTextColor = vm::setGlobalTextColor,
            onTexture = vm::setGlobalTexture,
        )
        TextButton(onClick = { showDialog = true }) {
            Text(stringResource(R.string.settings_apply_all), fontSize = 12.sp)
        }
        if (showDialog) {
            ConfirmDialog(
                title = stringResource(R.string.settings_apply_all_confirm_title),
                text = stringResource(R.string.settings_apply_all_confirm_content),
                onConfirm = vm::applyGlobalStyleToAllTiles,
                onDismiss = { showDialog = false },
            )
        }
        Text(stringResource(R.string.settings_tile_style_hint),
            color = palette.textMuted, fontSize = 11.sp)

        // ── Tile labels ───────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.settings_tile_text))
        TextSizeSlider(tileText.sizeSp) { vm.setTileText(tileText.copy(sizeSp = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeOption(stringResource(R.string.text_align_center), !tileText.alignStart) {
                vm.setTileText(tileText.copy(alignStart = false))
            }
            ThemeOption(stringResource(R.string.text_align_start), tileText.alignStart) {
                vm.setTileText(tileText.copy(alignStart = true))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeOption(stringResource(R.string.text_weight_normal), !tileText.bold) {
                vm.setTileText(tileText.copy(bold = false))
            }
            ThemeOption(stringResource(R.string.text_weight_bold), tileText.bold) {
                vm.setTileText(tileText.copy(bold = true))
            }
        }

        // ── Layouts ───────────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.settings_profiles))
        ProfilesSection(state.profiles, vm)

        // ── Gestures ──────────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.settings_gestures))
        GesturesSection(state, vm)

        // ── Private space ─────────────────────────────────────────────────────
        state.privateSpaceLocked?.let { locked ->
            SectionTitle(stringResource(R.string.settings_private_space))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (locked) R.string.private_space_locked else R.string.private_space_unlocked
                    ),
                    color = palette.textPrimary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::togglePrivateSpace) {
                    Text(
                        stringResource(if (locked) R.string.action_unlock else R.string.action_lock),
                        fontSize = 12.sp,
                    )
                }
            }
            Text(stringResource(R.string.private_space_hint), color = palette.textMuted, fontSize = 11.sp)
        }

        SectionTitle(stringResource(R.string.settings_backup_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onExport) {
                Text(stringResource(R.string.settings_export), fontSize = 13.sp)
            }
            TextButton(onClick = onImport) {
                Text(stringResource(R.string.settings_import), fontSize = 13.sp)
            }
        }
        Text(stringResource(R.string.settings_backup_subtitle),
            color = palette.textMuted, fontSize = 11.sp)

        SectionTitle(stringResource(R.string.settings_hidden_apps))
        if (state.hiddenApps.isEmpty()) {
            Text(stringResource(R.string.settings_hidden_none),
                color = palette.textMuted, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp))
        } else {
            state.hiddenApps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label, color = palette.textPrimary, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.unhideApp(app) }) {
                        Text(stringResource(R.string.action_unhide), fontSize = 12.sp)
                    }
                }
            }
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = palette.textFaint, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
    }
}

// ── Layouts ───────────────────────────────────────────────────────────────────

private sealed interface ProfileDialog {
    data object Create : ProfileDialog
    data class Rename(val profile: ProfileName) : ProfileDialog
    data class Delete(val profile: ProfileName) : ProfileDialog
}

@Composable
private fun ProfilesSection(profiles: ProfileState, vm: LauncherViewModel) {
    val palette = LocalQuedallePalette.current
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }

    profiles.profiles.forEach { profile ->
        val active = profile.id == profiles.activeId
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                profileLabel(profile.name),
                color = if (active) MaterialTheme.colorScheme.primary else palette.textPrimary,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.switchProfile(profile.id) }
                    .padding(vertical = 10.dp),
            )
            TextButton(onClick = { dialog = ProfileDialog.Rename(profile) }) {
                Text(stringResource(R.string.action_rename), fontSize = 12.sp)
            }
            if (profiles.profiles.size > 1) {
                TextButton(onClick = { dialog = ProfileDialog.Delete(profile) }) {
                    Text(stringResource(R.string.action_remove), fontSize = 12.sp, color = palette.danger)
                }
            }
        }
    }
    if (profiles.profiles.size < AppPreferences.MAX_PROFILES) {
        TextButton(onClick = { dialog = ProfileDialog.Create }) {
            Text(stringResource(R.string.action_new_profile), fontSize = 12.sp)
        }
    }
    Text(stringResource(R.string.settings_profiles_hint), color = palette.textMuted, fontSize = 11.sp)

    when (val current = dialog) {
        ProfileDialog.Create -> NameDialog(
            title = stringResource(R.string.action_new_profile),
            initial = "",
            onConfirm = vm::createProfile,
            onDismiss = { dialog = null },
        )
        is ProfileDialog.Rename -> NameDialog(
            title = stringResource(R.string.action_rename),
            initial = current.profile.name,
            onConfirm = { vm.renameProfile(current.profile.id, it) },
            onDismiss = { dialog = null },
        )
        is ProfileDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.profile_delete_title, profileLabel(current.profile.name)),
            text = stringResource(R.string.profile_delete_content),
            onConfirm = { vm.deleteProfile(current.profile.id) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

// ── Gestures ──────────────────────────────────────────────────────────────────

@Composable
private fun GesturesSection(state: LauncherUiState, vm: LauncherViewModel) {
    val palette = LocalQuedallePalette.current
    var picking by remember { mutableStateOf<Gesture?>(null) }

    Gesture.entries.forEach { gesture ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { picking = gesture }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(gesture.labelRes), color = palette.textPrimary, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            Text(
                gestureActionLabel(state.gesture(gesture), state.apps),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Text(stringResource(R.string.settings_gestures_hint), color = palette.textMuted, fontSize = 11.sp)

    picking?.let { gesture ->
        GestureActionDialog(
            title = stringResource(gesture.labelRes),
            state = state,
            onPick = { vm.setGesture(gesture, it); picking = null },
            onDismiss = { picking = null },
        )
    }
}

private val Gesture.labelRes: Int
    get() = when (this) {
        Gesture.SWIPE_DOWN -> R.string.gesture_swipe_down
        Gesture.SWIPE_UP -> R.string.gesture_swipe_up
        Gesture.DOUBLE_TAP -> R.string.gesture_double_tap
    }

@Composable
private fun gestureActionLabel(action: GestureAction, apps: List<AppInfo>): String = when (action) {
    GestureAction.None -> stringResource(R.string.gesture_none)
    GestureAction.Notifications -> stringResource(R.string.gesture_notifications)
    GestureAction.QuickSettings -> stringResource(R.string.gesture_quick_settings)
    GestureAction.Search -> stringResource(R.string.gesture_search)
    GestureAction.NextProfile -> stringResource(R.string.gesture_next_profile)
    GestureAction.PrivateSpace -> stringResource(R.string.gesture_private_space)
    is GestureAction.LaunchApp ->
        apps.firstOrNull { it.key == action.appKey }?.label ?: stringResource(R.string.gesture_app_missing)
}

/** Lists the possible actions; "open an app" swaps the list for the installed apps. */
@Composable
private fun GestureActionDialog(
    title: String,
    state: LauncherUiState,
    onPick: (GestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    var choosingApp by remember { mutableStateOf(false) }
    val actions = listOfNotNull(
        GestureAction.None,
        GestureAction.Notifications,
        GestureAction.QuickSettings,
        GestureAction.Search,
        GestureAction.NextProfile,
        GestureAction.PrivateSpace.takeIf { state.privateSpaceLocked != null },
    )
    AlertDialog(
        containerColor = palette.surface,
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(title, color = palette.textStrong, fontSize = 16.sp) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                if (choosingApp) {
                    items(state.apps, key = { it.key }) { app ->
                        DialogOption(app.label) { onPick(GestureAction.LaunchApp(app.key)) }
                    }
                } else {
                    items(actions) { action ->
                        DialogOption(gestureActionLabel(action, state.apps)) { onPick(action) }
                    }
                    item {
                        DialogOption(stringResource(R.string.gesture_open_app)) { choosingApp = true }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DialogOption(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = LocalQuedallePalette.current.textPrimary, fontSize = 14.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
internal fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalQuedallePalette.current
    AlertDialog(
        containerColor = palette.surface,
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(title, color = palette.textStrong) },
        text = { Text(text, color = palette.textPrimary) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalQuedallePalette.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        containerColor = palette.surface,
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(title, color = palette.textStrong, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.label_name)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onDismiss(); onConfirm(name) },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_NAME_LENGTH = 24

/** The slider moves freely; the size is only stored when it lands on a new step. */
@Composable
private fun TextSizeSlider(sizeSp: Int, onChange: (Int) -> Unit) {
    val palette = LocalQuedallePalette.current
    var position by remember(sizeSp) { mutableFloatStateOf(sizeSp.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.text_size), color = palette.textPrimary, fontSize = 13.sp)
        Slider(
            value = position,
            onValueChange = {
                position = it
                val step = it.roundToInt()
                if (step != sizeSp) onChange(step)
            },
            valueRange = TileText.MIN_SIZE_SP.toFloat()..TileText.MAX_SIZE_SP.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text("$sizeSp", color = palette.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalQuedallePalette.current
    TextButton(onClick = onClick) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else palette.textMuted,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = LocalQuedallePalette.current.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}
