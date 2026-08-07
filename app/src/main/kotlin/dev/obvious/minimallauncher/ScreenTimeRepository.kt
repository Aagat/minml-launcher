package dev.obvious.minimallauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.Process
import java.util.Calendar
import java.util.concurrent.Executors

data class ScreenStateEvent(
    val timestampMillis: Long,
    val interactive: Boolean,
)

object ScreenTimeCalculator {
    fun calculate(
        dayStartMillis: Long,
        nowMillis: Long,
        currentlyInteractive: Boolean,
        events: List<ScreenStateEvent>,
    ): Long {
        if (nowMillis <= dayStartMillis) return 0L
        val todayEvents = events
            .filter { it.timestampMillis in dayStartMillis..nowMillis }
            .sortedBy { it.timestampMillis }
        var interactive = todayEvents.firstOrNull()?.let { !it.interactive } ?: currentlyInteractive
        var cursor = dayStartMillis
        var total = 0L
        todayEvents.forEach { event ->
            if (interactive) total += event.timestampMillis - cursor
            interactive = event.interactive
            cursor = event.timestampMillis
        }
        if (interactive) total += nowMillis - cursor
        return total.coerceIn(0L, nowMillis - dayStartMillis)
    }
}

object ScreenTimeFormatter {
    fun compact(durationMillis: Long): String {
        val totalMinutes = (durationMillis.coerceAtLeast(0L) / 60_000L).toInt()
        if (totalMinutes == 0) return "<1m"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}

sealed interface ScreenTimeResult {
    data class Available(val durationMillis: Long) : ScreenTimeResult
    data object PermissionRequired : ScreenTimeResult
    data object Unavailable : ScreenTimeResult
}

class ScreenTimeRepository(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val usageStatsManager = applicationContext.getSystemService(UsageStatsManager::class.java)
    private val appOpsManager = applicationContext.getSystemService(AppOpsManager::class.java)
    private val powerManager = applicationContext.getSystemService(PowerManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasUsageAccess(): Boolean = appOpsManager.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        applicationContext.packageName,
    ) == AppOpsManager.MODE_ALLOWED

    fun load(nowMillis: Long = System.currentTimeMillis(), callback: (ScreenTimeResult) -> Unit) {
        executor.execute {
            if (!hasUsageAccess()) {
                callback(ScreenTimeResult.PermissionRequired)
                return@execute
            }
            callback(runCatching {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = calendar.timeInMillis
                val usageEvents = usageStatsManager.queryEvents(dayStart, nowMillis)
                    ?: return@runCatching ScreenTimeResult.Unavailable
                val event = UsageEvents.Event()
                val stateEvents = buildList {
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        when (event.eventType) {
                            UsageEvents.Event.SCREEN_INTERACTIVE -> add(ScreenStateEvent(event.timeStamp, true))
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> add(ScreenStateEvent(event.timeStamp, false))
                        }
                    }
                }
                ScreenTimeResult.Available(
                    ScreenTimeCalculator.calculate(
                        dayStart,
                        nowMillis,
                        powerManager.isInteractive,
                        stateEvents,
                    ),
                )
            }.getOrDefault(ScreenTimeResult.Unavailable))
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
