package dev.mlg.quedalle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    swipeDownNotifications: Boolean,
    onSwipeDownChange: (Boolean) -> Unit,
    hiddenApps: List<AppInfo>,
    onUnhide: (AppInfo) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQuedallePalette.current
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
                onThemeModeChange(ThemeMode.SYSTEM)
            }
            ThemeOption(stringResource(R.string.theme_dark), themeMode == ThemeMode.DARK) {
                onThemeModeChange(ThemeMode.DARK)
            }
            ThemeOption(stringResource(R.string.theme_light), themeMode == ThemeMode.LIGHT) {
                onThemeModeChange(ThemeMode.LIGHT)
            }
        }

        // ── Gestures ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_swipe_down_title),
                    color = palette.textPrimary, fontSize = 14.sp)
                Text(stringResource(R.string.settings_swipe_down_subtitle),
                    color = palette.textMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Switch(checked = swipeDownNotifications, onCheckedChange = onSwipeDownChange)
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
        if (hiddenApps.isEmpty()) {
            Text(stringResource(R.string.settings_hidden_none),
                color = palette.textMuted, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp))
        } else {
            hiddenApps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label, color = palette.textPrimary, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { onUnhide(app) }) {
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
