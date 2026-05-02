package com.fithealthzone.bandsongbook.transpose

data class RenderLine(val chordLine: String, val lyricLine: String)

object ChordRenderer {
    private val chordRegex = Regex("""\[(.+?)]""")

    fun renderLines(chordProText: String): List<RenderLine> {
        return chordProText.lines().map { line ->
            val chordLine = StringBuilder()
            val lyricLine = StringBuilder()
            var i = 0
            chordRegex.findAll(line).forEach { m ->
                val start = m.range.first
                val textBefore = line.substring(i, start)
                lyricLine.append(textBefore)
                chordLine.append(" ".repeat(textBefore.length))
                val chord = m.groupValues[1]
                chordLine.append(chord)
                i = m.range.last + 1
            }
            val rest = line.substring(i)
            lyricLine.append(rest)
            chordLine.append(" ".repeat(rest.length))
            RenderLine(chordLine.toString().trimEnd(), lyricLine.toString())
        }
    }
}
