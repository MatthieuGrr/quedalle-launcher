package dev.mlg.quedalle.ui

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.InfoKind
import java.util.Date
import java.util.Locale

/**
 * The live text of an information tile. It follows the system broadcasts
 * that announce a change (minute tick, battery level, next alarm) instead
 * of polling, and is read again whenever the launcher comes back.
 */
@Composable
fun infoText(kind: InfoKind): String {
    val context = LocalContext.current
    var text by remember(kind) { mutableStateOf(readInfo(context, kind)) }

    DisposableEffect(kind, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                text = readInfo(context, kind)
            }
        }
        val filter = IntentFilter().apply { actionsOf(kind).forEach(::addAction) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        text = readInfo(context, kind)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { text = readInfo(context, kind) }

    return text
}

private val clockActions = listOf(
    Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
)

private fun actionsOf(kind: InfoKind): List<String> = when (kind) {
    InfoKind.CLOCK, InfoKind.DATE -> clockActions
    InfoKind.BATTERY -> listOf(Intent.ACTION_BATTERY_CHANGED)
    InfoKind.ALARM -> clockActions + AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED
}

private fun readInfo(context: Context, kind: InfoKind): String = try {
    val locale = Locale.getDefault()
    when (kind) {
        InfoKind.CLOCK -> DateFormat.getTimeFormat(context).format(Date())
        InfoKind.DATE ->
            DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), Date()).toString()
        InfoKind.BATTERY -> {
            val battery = context.getSystemService(BatteryManager::class.java)
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery.isCharging) "$level % ⚡" else "$level %"
        }
        InfoKind.ALARM -> {
            val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock
            if (next == null) context.getString(R.string.info_no_alarm)
            else {
                val skeleton = if (DateFormat.is24HourFormat(context)) "EEEHm" else "EEEhma"
                DateFormat.format(DateFormat.getBestDateTimePattern(locale, skeleton), Date(next.triggerTime))
                    .toString()
            }
        }
    }
} catch (_: Exception) {
    "—"
}
