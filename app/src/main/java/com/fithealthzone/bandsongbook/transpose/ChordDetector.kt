package com.fithealthzone.bandsongbook.transpose

object ChordDetector {
    // Standard chord roots
    private val roots = "[A-H]"
    // Accidentals
    private val accidentals = "[#b]?"
    // Keep the accepted suffix grammar aligned with BandBook-v2/src/utils/chords.ts.
    private val quality = "(?:maj|min|dim|aug|sus|add|m|M|\\+|°|ø)?"
    private val suffix = "(?:$quality\\d{0,2}(?:sus\\d?|add\\d?|maj\\d?|m\\d?)?(?:[#b]\\d{1,2})?)*"
    // Optional bass note
    private val bassNote = "(?:/(?:$roots)$accidentals)?"

    // Full chord pattern — must be at word boundary
    private val chordPattern = Regex(
        "(?:(?<=^)|(?<=\\s)|(?<=\\())($roots$accidentals$suffix$bassNote)(?=\\s|$|\\)|,|;|\\.)",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    // Pattern for already-wrapped chords
    private val alreadyWrapped = Regex("""\[.+?]""")

    /**
     * Detect chords in text and wrap them with [...].
     * Skips chords already inside brackets.
     */
    fun autoWrapChords(text: String): String {
        if (text.isEmpty()) return text

        // Identify regions already inside [...] to skip them
        val protectedRanges = alreadyWrapped.findAll(text).map { it.range }.toList()

        val lines = text.split("\n")
        var globalPos = 0

        return lines.joinToString("\n") { line ->
            val lineStart = globalPos
            globalPos += line.length + 1 // +1 for \n

            processLine(line, lineStart, protectedRanges)
        }
    }

    private fun processLine(line: String, lineStartInText: Int, protectedRanges: List<IntRange>): String {
        if (line.isBlank()) return line

        val tagRegex = Regex("<[^>]+>")
        if (tagRegex.replace(line, "").isBlank()) return line

        val searchableLine = tagRegex.replace(line) { " ".repeat(it.value.length) }
        val matches = chordPattern.findAll(searchableLine).toList()
        if (matches.isEmpty()) return line

        val result = StringBuilder()
        var cursor = 0

        for (match in matches) {
            val matchStartGlobal = lineStartInText + match.range.first
            val matchEndGlobal = lineStartInText + match.range.last

            // Skip if this match is inside an already-wrapped chord
            val isProtected = protectedRanges.any { range ->
                matchStartGlobal >= range.first && matchEndGlobal <= range.last
            }
            if (isProtected) continue

            // Skip if the chord is already inside brackets in the line
            val beforeInLine = line.substring(0, match.range.first)
            val afterInLine = if (match.range.last + 1 < line.length) line.substring(match.range.last + 1) else ""
            if (beforeInLine.endsWith("[") && afterInLine.startsWith("]")) continue

            result.append(line.substring(cursor, match.range.first))
            result.append("[${match.value}]")
            cursor = match.range.last + 1
        }

        result.append(line.substring(cursor))
        return result.toString()
    }

    /**
     * Check if a single token looks like a chord.
     */
    fun isChord(token: String): Boolean {
        return chordPattern.matches(token)
    }
}
