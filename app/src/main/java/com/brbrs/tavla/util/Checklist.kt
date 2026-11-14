package com.brbrs.tavla.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** #08: resolves a content:// URI's display name (e.g. from a file picker), for naming an upload. */
fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

/**
 * Deck stores checklists as Markdown task items inside the card description:
 * "- [ ] todo" / "- [x] done" (also "*" or "+" bullets). These helpers read and toggle them
 * without touching any other line, so edits made in the Deck web UI round-trip cleanly.
 */
data class ChecklistItem(val lineIndex: Int, val text: String, val done: Boolean)

private val taskRegex = Regex("""^(\s*[-*+]\s+\[)([ xX])(\]\s?)(.*)$""")

fun parseChecklist(description: String): List<ChecklistItem> =
    description.lines().mapIndexedNotNull { index, line ->
        taskRegex.matchEntire(line)?.let { m ->
            ChecklistItem(lineIndex = index, text = m.groupValues[4], done = m.groupValues[2] != " ")
        }
    }

fun toggleChecklistItem(description: String, lineIndex: Int): String {
    val lines = description.lines().toMutableList()
    if (lineIndex !in lines.indices) return description
    val m = taskRegex.matchEntire(lines[lineIndex]) ?: return description
    val newMark = if (m.groupValues[2] == " ") "x" else " "
    lines[lineIndex] = m.groupValues[1] + newMark + m.groupValues[3] + m.groupValues[4]
    return lines.joinToString("\n")
}

fun addChecklistItem(description: String, text: String): String {
    val item = "- [ ] ${text.trim()}"
    if (description.isBlank()) return item
    val lines = description.lines()
    val lastTask = lines.indexOfLast { taskRegex.matches(it) }
    return if (lastTask >= 0) {
        (lines.take(lastTask + 1) + item + lines.drop(lastTask + 1)).joinToString("\n")
    } else {
        description.trimEnd() + "\n\n" + item
    }
}

/** The description with checklist lines removed, for showing the prose part on its own. */
fun descriptionWithoutChecklist(description: String): String =
    description.lines().filterNot { taskRegex.matches(it) }.joinToString("\n").trim()
