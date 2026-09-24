package com.fithealthzone.bandsongbook.transpose

object ChordTransposer {
    private val sharp = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flat = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val noteIndex = mapOf(
        "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
        "E" to 4, "Fb" to 4, "E#" to 5, "F" to 5, "F#" to 6, "Gb" to 6,
        "G" to 7, "G#" to 8, "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10,
        "B" to 11, "Cb" to 11, "B#" to 0, "H" to 11, "H#" to 0
    )
    private val chordRegex = Regex("""\[(.+?)]""")

    fun transposeLyrics(chordProText: String, semitones: Int, preferFlats: Boolean): String {
        return chordRegex.replace(chordProText) { m ->
            val chord = m.groupValues[1]
            if (ChordDetector.isChord(chord)) {
                "[${transposeChord(chord, semitones, preferFlats)}]"
            } else {
                m.value
            }
        }
    }

    fun transposeChord(chord: String, semitones: Int, preferFlats: Boolean): String {
        val parts = chord.split("/")
        val main = transposeOne(parts[0], semitones, preferFlats)
        return if (parts.size > 1) "$main/${transposeOne(parts[1], semitones, preferFlats)}" else main
    }

    private fun transposeOne(one: String, semitones: Int, preferFlats: Boolean): String {
        val m = Regex("^([A-Ha-h])([#b]?)(.*)$").find(one) ?: return one
        val root = m.groupValues[1].uppercase() + m.groupValues[2]
        val suffix = m.groupValues[3]
        val idx = noteIndex[root] ?: return one
        val targetMap = if (preferFlats) flat else sharp
        val newIdx = (idx + semitones % 12 + 12) % 12
        return targetMap[newIdx] + suffix
    }
}
