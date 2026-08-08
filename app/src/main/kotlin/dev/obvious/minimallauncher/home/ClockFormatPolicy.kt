package dev.obvious.minimallauncher.home

enum class ClockFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }

object ClockFormatPolicy {
    fun uses24Hour(format: ClockFormat, systemUses24Hour: Boolean): Boolean = when (format) {
        ClockFormat.SYSTEM -> systemUses24Hour
        ClockFormat.TWELVE_HOUR -> false
        ClockFormat.TWENTY_FOUR_HOUR -> true
    }

    fun pattern(format: ClockFormat, systemUses24Hour: Boolean): String =
        if (uses24Hour(format, systemUses24Hour)) "HH:mm" else "hh:mm"
}
