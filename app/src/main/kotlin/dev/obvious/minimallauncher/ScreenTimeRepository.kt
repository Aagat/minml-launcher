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

data class AppUsageDuration(
    val packageName: String,
    val durationMillis: Long,
)

object DetailedUsagePolicy {
    fun rank(
        usage: List<AppUsageDuration>,
        eligiblePackages: Set<String>,
        excludedPackage: String,
        limit: Int = 4,
    ): List<AppUsageDuration> = usage
        .asSequence()
        .filter { it.packageName in eligiblePackages && it.packageName != excludedPackage && it.durationMillis > 0L }
        .groupBy { it.packageName }
        .map { (packageName, entries) -> AppUsageDuration(packageName, entries.sumOf { it.durationMillis }) }
        .sortedWith(compareByDescending<AppUsageDuration> { it.durationMillis }.thenBy { it.packageName })
        .take(limit.coerceAtLeast(0))
}

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

    fun spoken(durationMillis: Long): String {
        val totalMinutes = (durationMillis.coerceAtLeast(0L) / 60_000L).toInt()
        if (totalMinutes == 0) return "less than one minute"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val hourLabel = if (hours == 1) "hour" else "hours"
        val minuteLabel = if (minutes == 1) "minute" else "minutes"
        return when {
            hours == 0 -> "$minutes $minuteLabel"
            minutes == 0 -> "$hours $hourLabel"
            else -> "$hours $hourLabel $minutes $minuteLabel"
        }
    }
}

sealed interface ScreenTimeResult {
    data class Available(
        val durationMillis: Long,
        val topApps: List<AppUsageDuration> = emptyList(),
    ) : ScreenTimeResult
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

    fun load(
        nowMillis: Long = System.currentTimeMillis(),
        detailedUsagePackages: Set<String> = emptySet(),
        callback: (ScreenTimeResult) -> Unit,
    ) {
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
                    durationMillis = ScreenTimeCalculator.calculate(
                        dayStart,
                        nowMillis,
                        powerManager.isInteractive,
                        stateEvents,
                    ),
                    topApps = if (detailedUsagePackages.isEmpty()) {
                        emptyList()
                    } else {
                        DetailedUsagePolicy.rank(
                            usage = usageStatsManager.queryUsageStats(
                                UsageStatsManager.INTERVAL_DAILY,
                                dayStart,
                                nowMillis,
                            ).orEmpty().map { AppUsageDuration(it.packageName, it.totalTimeInForeground) },
                            eligiblePackages = detailedUsagePackages,
                            excludedPackage = applicationContext.packageName,
                        )
                    },
                )
            }.getOrDefault(ScreenTimeResult.Unavailable))
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
