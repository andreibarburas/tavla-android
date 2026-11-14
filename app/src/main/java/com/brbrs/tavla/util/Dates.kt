package com.brbrs.tavla.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DueState { Overdue, Today, Soon, Later }

fun dueState(dueMs: Long, now: Long = System.currentTimeMillis()): DueState {
    if (dueMs < now) return DueState.Overdue
    val zone = ZoneId.systemDefault()
    val dueDay = Instant.ofEpochMilli(dueMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(today, dueDay)
    return when {
        days <= 0L -> DueState.Today
        days <= 7L -> DueState.Soon
        else       -> DueState.Later
    }
}

/** "Today 18:00", "Tomorrow", "Fri 9 Oct", or "9 Oct 2027" for other years. */
fun formatDue(dueMs: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(dueMs).atZone(zone)
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(today, dt.toLocalDate())
    val time = dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    return when {
        days == 0L  -> "Today $time"
        days == 1L  -> "Tomorrow $time"
        days == -1L -> "Yesterday"
        dt.year == today.year -> dt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        else -> dt.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

fun formatTimestamp(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    val today = LocalDate.now(zone)
    return if (dt.toLocalDate() == today) dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    else dt.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
}

/** "0m", "45m", "1h 05m", "12h 30m" -- compact tracked-time durations. */
fun formatDuration(totalSeconds: Int): String {
    val s = if (totalSeconds < 0) 0 else totalSeconds
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
        minutes > 0 -> "${minutes}m"
        else -> "${s}s"
    }
}

/** "h:mm:ss" live clock for a running timer (elapsed since start). */
fun formatElapsedClock(elapsedMs: Long): String {
    val total = (elapsedMs / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
