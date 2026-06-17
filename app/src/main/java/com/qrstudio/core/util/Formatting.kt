package com.qrstudio.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date/time formatting helpers (java.time is available natively from minSdk 26). */
object Formatting {

    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)

    fun timestamp(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(dateTime)

    fun fileSize(bytes: Int): String =
        if (bytes < 1024) "$bytes o"
        else String.format(Locale.FRANCE, "%.1f Ko", bytes / 1024.0)
}
