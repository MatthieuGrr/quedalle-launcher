package dev.mlg.quedalle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import dev.mlg.quedalle.model.LookPreset
import dev.mlg.quedalle.model.TextCase
import dev.mlg.quedalle.model.TileFont
import dev.mlg.quedalle.model.TileShape
import dev.mlg.quedalle.model.VerticalAnchor
import dev.mlg.quedalle.ui.theme.LocalIsDark
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

private enum class SettingsPage(val title: Int, val summary: Int) {
    THEME(R.string.settings_theme, R.string.settings_theme_summary),
    TILES(R.string.settings_tiles, R.string.settings_tiles_summary),
    TEXT(R.string.settings_tile_text, R.string.settings_text_summary),
    LAYOUTS(R.string.settings_profiles, R.string.settings_profiles_summary),
    GESTURES(R.string.settings_gestures, R.string.settings_gestures_summary),
    APPS(R.string.settings_apps, R.string.settings_apps_summary),
    BACKUP(R.string.settings_backup_title, R.string.settings_backup_subtitle),
}

/**
 * Settings: a short menu, one page per subject. Every change applies
 * immediately; there is nothing to save.
 */
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
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    // Composed inside the launcher's own back handler, so this one wins:
    // back leaves a page for the menu before it leaves the settings.
    BackHandler(enabled = page != null) { page = null }

    // Each page gets its own scroll position.
    key(page) {
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
                TextButton(onClick = { if (page != null) page = null else onBack() }) {
                    Text("‹", color = palette.textStrong, fontSize = 22.sp)
                }
                Text(
                    stringResource(page?.title ?: R.string.settings_title),
                    color = palette.textStrong, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            when (page) {
                null -> {
                    SettingsPage.entries.forEach { entry ->
                        MenuEntry(stringResource(entry.title), stringResource(entry.summary)) { page = entry }
                    }
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        color = palette.textFaint, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
                SettingsPage.THEME -> ThemePage(state, themeMode, vm)
                SettingsPage.TILES -> TilesPage(state, vm)
                SettingsPage.TEXT -> TextPage(state, vm)
                SettingsPage.LAYOUTS -> ProfilesSection(state.profiles, vm)
                SettingsPage.GESTURES -> GesturesSection(state, vm)
                SettingsPage.APPS -> AppsPage(state, vm)
                SettingsPage.BACKUP -> {
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
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(title: String, summary: String, onClick: () -> Unit) {
    val palette = LocalQuedallePalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp)
            Text(summary, color = palette.textMuted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = palette.textMuted, fontSize = 20.sp)
    }
}

// ── Theme ─────────────────────────────────────────────────────────────────────

private val DarkBackgrounds = listOf(
    0xFF000000, 0xFF0B0B0C, 0xFF121212, 0xFF1A1A1A, 0xFF0D1117, 0xFF14110F,
).map { it.toInt() }

private val LightBackgrounds = listOf(
    0xFFFFFFFF, 0xFFFAFAFA, 0xFFECECEC, 0xFFF2EFE9, 0xFFF4EDE4, 0xFFE9EEF2,
).map { it.toInt() }

@Composable
private fun ThemePage(state: LauncherUiState, themeMode: ThemeMode, vm: LauncherViewModel) {
    val palette = LocalQuedallePalette.current
    val dark = LocalIsDark.current
    var confirming by remember { mutableStateOf<LookPreset?>(null) }

    OptionRow(
        stringResource(R.string.theme_system) to (themeMode == ThemeMode.SYSTEM),
        stringResource(R.string.theme_dark) to (themeMode == ThemeMode.DARK),
        stringResource(R.string.theme_light) to (themeMode == ThemeMode.LIGHT),
    ) { vm.setThemeMode(listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)[it]) }

    // ── Screen background, for the theme currently shown ─────────────────────
    SectionTitle(stringResource(R.string.settings_background))
    val current = state.screen.background(dark)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        AutoSwatch(selected = current == null, modifier = Modifier.weight(1f)) {
            vm.setScreenBackground(dark, null)
        }
        (if (dark) DarkBackgrounds else LightBackgrounds).forEach { color ->
            // 1 is never a real color: nothing is selected while "auto" is.
            ColorSwatch(color, current ?: 1, Modifier.weight(1f), onSelect = { vm.setScreenBackground(dark, it) })
        }
    }
    Text(
        stringResource(if (dark) R.string.settings_background_hint_dark else R.string.settings_background_hint_light),
        color = palette.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
    )

    // ── Ready-made looks ─────────────────────────────────────────────────────
    SectionTitle(stringResource(R.string.settings_presets))
    LookPreset.all.forEach { preset ->
        MenuEntry(stringResource(preset.titleRes), stringResource(preset.summaryRes)) { confirming = preset }
    }
    Text(stringResource(R.string.settings_presets_hint), color = palette.textMuted, fontSize = 11.sp)

    confirming?.let { preset ->
        ConfirmDialog(
            title = stringResource(preset.titleRes),
            text = stringResource(R.string.settings_presets_confirm),
            onConfirm = { vm.applyPreset(preset) },
            onDismiss = { confirming = null },
        )
    }
}

private val LookPreset.titleRes: Int
    get() = when (key) {
        LookPreset.PAPER -> R.string.preset_paper
        LookPreset.OUTLINE -> R.string.preset_outline
        LookPreset.MOSAIC -> R.string.preset_mosaic
        else -> R.string.preset_black
    }

private val LookPreset.summaryRes: Int
    get() = when (key) {
        LookPreset.PAPER -> R.string.preset_paper_summary
        LookPreset.OUTLINE -> R.string.preset_outline_summary
        LookPreset.MOSAIC -> R.string.preset_mosaic_summary
        else -> R.string.preset_black_summary
    }

// ── Tiles ─────────────────────────────────────────────────────────────────────

@Composable
private fun TilesPage(state: LauncherUiState, vm: LauncherViewModel) {
    val palette = LocalQuedallePalette.current
    val globalStyle = state.globalStyle
    val shape = state.tileShape
    var showDialog by remember { mutableStateOf(false) }

    TilePreview(
        label = "Quedalle",
        background = globalStyle.background ?: QuedalleColors.TileAppColor,
        textColor = globalStyle.textColor,
        texture = globalStyle.texture,
    )

    SectionTitle(stringResource(R.string.settings_tile_style))
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

    SectionTitle(stringResource(R.string.settings_shape))
    IntSlider(stringResource(R.string.shape_corners), shape.cornerDp, 0..TileShape.MAX_CORNER) {
        vm.setTileShape(shape.copy(cornerDp = it))
    }
    IntSlider(stringResource(R.string.shape_gap), shape.gapDp, 0..TileShape.MAX_GAP) {
        vm.setTileShape(shape.copy(gapDp = it))
    }
    IntSlider(stringResource(R.string.shape_margin), shape.marginDp, 0..TileShape.MAX_MARGIN) {
        vm.setTileShape(shape.copy(marginDp = it))
    }

    SectionTitle(stringResource(R.string.settings_touch))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_haptics), color = palette.textPrimary, fontSize = 13.sp,
            modifier = Modifier.weight(1f))
        Switch(checked = state.screen.haptics, onCheckedChange = vm::setHaptics)
    }
}

// ── Text ──────────────────────────────────────────────────────────────────────

@Composable
private fun TextPage(state: LauncherUiState, vm: LauncherViewModel) {
    val text = state.tileText
    val style = state.globalStyle

    TilePreview(
        label = "Quedalle",
        background = style.background ?: QuedalleColors.TileAppColor,
        textColor = style.textColor,
        texture = style.texture,
    )

    IntSlider(stringResource(R.string.text_size), text.sizeSp, TileText.MIN_SIZE_SP..TileText.MAX_SIZE_SP) {
        vm.setTileText(text.copy(sizeSp = it))
    }

    SectionTitle(stringResource(R.string.text_font))
    OptionRow(
        stringResource(R.string.font_system) to (text.font == TileFont.SYSTEM),
        stringResource(R.string.font_serif) to (text.font == TileFont.SERIF),
        stringResource(R.string.font_mono) to (text.font == TileFont.MONO),
    ) { vm.setTileText(text.copy(font = TileFont.entries[it])) }
    OptionRow(
        stringResource(R.string.text_weight_normal) to !text.bold,
        stringResource(R.string.text_weight_bold) to text.bold,
    ) { vm.setTileText(text.copy(bold = it == 1)) }

    SectionTitle(stringResource(R.string.text_case))
    OptionRow(
        stringResource(R.string.case_none) to (text.case == TextCase.NONE),
        stringResource(R.string.case_upper) to (text.case == TextCase.UPPER),
        stringResource(R.string.case_lower) to (text.case == TextCase.LOWER),
    ) { vm.setTileText(text.copy(case = TextCase.entries[it])) }

    SectionTitle(stringResource(R.string.text_position))
    OptionRow(
        stringResource(R.string.text_align_center) to !text.alignStart,
        stringResource(R.string.text_align_start) to text.alignStart,
    ) { vm.setTileText(text.copy(alignStart = it == 1)) }
    OptionRow(
        stringResource(R.string.anchor_top) to (text.vertical == VerticalAnchor.TOP),
        stringResource(R.string.anchor_center) to (text.vertical == VerticalAnchor.CENTER),
        stringResource(R.string.anchor_bottom) to (text.vertical == VerticalAnchor.BOTTOM),
    ) { vm.setTileText(text.copy(vertical = VerticalAnchor.entries[it])) }
}

// ── Apps ──────────────────────────────────────────────────────────────────────

@Composable
private fun AppsPage(state: LauncherUiState, vm: LauncherViewModel) {
    val palette = LocalQuedallePalette.current

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

/** The slider moves freely; the value is only stored when it lands on a new step. */
@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val palette = LocalQuedallePalette.current
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.widthIn(min = 84.dp))
        Slider(
            value = position,
            onValueChange = {
                position = it
                val step = it.roundToInt()
                if (step != value) onChange(step)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text("$value", color = palette.textMuted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 20.dp))
    }
}

/** A row of mutually exclusive text options; [onPick] gets the index tapped. */
@Composable
private fun OptionRow(vararg options: Pair<String, Boolean>, onPick: (Int) -> Unit) {
    val palette = LocalQuedallePalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            TextButton(onClick = { onPick(index) }) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else palette.textMuted,
                )
            }
        }
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
