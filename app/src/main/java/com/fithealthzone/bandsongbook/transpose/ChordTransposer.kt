package com.fithealthzone.bandsongbook.transpose

object ChordTransposer {
    private val sharp = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flat = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val chordRegex = Regex("""\[(.+?)]""")

    fun transposeLyrics(chordProText: String, semitones: Int, preferFlats: Boolean): String {
        return chordRegex.replace(chordProText) { m ->
            val chord = m.groupValues[1]
            "[${transposeChord(chord, semitones, preferFlats)}]"
        }
    }

    fun transposeChord(chord: String, semitones: Int, preferFlats: Boolean): String {
        val parts = chord.split("/")
        val main = transposeOne(parts[0], semitones, preferFlats)
        return if (parts.size > 1) "$main/${transposeOne(parts[1], semitones, preferFlats)}" else main
    }

    private fun transposeOne(one: String, semitones: Int, preferFlats: Boolean): String {
        val m = Regex("^([A-G])([#b]?)(.*)$").find(one) ?: return one
        val root = m.groupValues[1] + m.groupValues[2]
        val suffix = m.groupValues[3]
        val from = if (root.contains('b')) flat else sharp
        val idx = from.indexOf(root)
        if (idx == -1) return one
        val targetMap = if (preferFlats) flat else sharp
        val newIdx = (idx + semitones % 12 + 12) % 12
        return targetMap[newIdx] + suffix
    }
}
