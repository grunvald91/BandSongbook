package com.fithealthzone.bandsongbook.formatting

data class SongFormattingEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

data class SongFormattingSection(
    val markup: String,
    val highlighted: Boolean
)

object SongFormatting {
    private val formattingTags = Regex(
        "(?i)</?(?:b|strong|i|em|u|mark|hl|font|color|c)(?:\\s+[^>]*)?>|<color=[^>]+>|<c\\s+v=[^>]+>"
    )

    fun normalize(raw: String): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = decodeEntities(text)
        text = text
            .replace(Regex("(?is)<div[^>]*>\\s*<br\\s*/?>\\s*</div>"), "\n")
            .replace(Regex("(?is)<p[^>]*>\\s*<br\\s*/?>\\s*</p>"), "\n")
        text = text.replace(
            Regex("(?is)<span\\b[^>]*class=[\"'][^\"']*(?:song-highlight|bb-section-highlight)[^\"']*[\"'][^>]*>(.*?)</span>"),
            "<mark>$1</mark>"
        )
        text = text.replace(
            Regex("(?is)<span\\b[^>]*style=[\"']([^\"']*)[\"'][^>]*>(.*?)</span>")
        ) { match ->
            val color = Regex("(?i)(?:^|;)\\s*color\\s*:\\s*([^;]+)")
                .find(match.groupValues[1])
                ?.groupValues
                ?.get(1)
                ?.trim()
            if (color.isNullOrBlank()) match.groupValues[2] else "<color=${canonicalColor(color)}>${match.groupValues[2]}</color>"
        }
        text = text
            .replace(Regex("(?i)<hl\\b[^>]*>"), "<mark>")
            .replace(Regex("(?i)</hl>"), "</mark>")
            .replace(Regex("(?i)<strong\\b[^>]*>"), "<b>")
            .replace(Regex("(?i)</strong>"), "</b>")
            .replace(Regex("(?i)<em\\b[^>]*>"), "<i>")
            .replace(Regex("(?i)</em>"), "</i>")
            .replace(Regex("(?i)<font\\b[^>]*color\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))[^>]*>")) {
                val color = it.groupValues.drop(1).first { value -> value.isNotEmpty() }
                "<color=${canonicalColor(color)}>"
            }
            .replace(Regex("(?i)</font>"), "</color>")
            .replace(Regex("(?i)<c\\s+v\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))\\s*>")) {
                val color = it.groupValues.drop(1).first { value -> value.isNotEmpty() }
                "<color=${canonicalColor(color)}>"
            }
            .replace(Regex("(?i)</c>"), "</color>")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</div>\\s*<div[^>]*>"), "\n")
            .replace(Regex("(?i)<div[^>]*>"), "\n")
            .replace(Regex("(?i)</div>"), "")
            .replace(Regex("(?i)</p>\\s*<p[^>]*>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "\n")
            .replace(Regex("(?i)</p>"), "")
            .replace(Regex("(?i)</?span\\b[^>]*>"), "")
        text = text.replace(Regex("(?i)<color=([^>]+)>")) {
            "<color=${canonicalColor(it.groupValues[1].trim().trim('\"', '\''))}>"
        }
        return text.replace(Regex("^\\n+|\\n+$"), "")
    }

    fun toggleHighlight(text: String, selectionStart: Int, selectionEnd: Int): SongFormattingEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (start == end) return SongFormattingEdit(text, start, end)

        val enclosingMark = findTagPairs(text, includeMark = true)
            .filter { it.open.name == "mark" }
            .filter { pair ->
                start >= pair.open.start &&
                    end <= pair.closeEndExclusive &&
                    start <= pair.closeStart &&
                    end >= pair.open.endExclusive
            }
            .minByOrNull { it.closeEndExclusive - it.open.start }
        if (enclosingMark != null) {
            val content = text.substring(enclosingMark.open.endExclusive, enclosingMark.closeStart)
            val unwrapped = text.substring(0, enclosingMark.open.start) + content + text.substring(enclosingMark.closeEndExclusive)
            return SongFormattingEdit(
                unwrapped,
                enclosingMark.open.start,
                enclosingMark.open.start + content.length
            )
        }

        return wrapBalanced(text, start, end, "<mark>", "</mark>")
    }

    fun wrapBalanced(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        openTag: String,
        closeTag: String
    ): SongFormattingEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (start == end) return SongFormattingEdit(text, start, end)
        val balancedRange = expandAcrossTouchedFormattingTags(text, start, end)
        val balancedStart = balancedRange.first
        val balancedEnd = balancedRange.last + 1
        val selected = text.substring(balancedStart, balancedEnd)
        return SongFormattingEdit(
            text = text.substring(0, balancedStart) + openTag + selected + closeTag + text.substring(balancedEnd),
            selectionStart = balancedStart + openTag.length,
            selectionEnd = balancedEnd + openTag.length
        )
    }

    fun clearAll(raw: String): String = normalize(raw)
        .replace(formattingTags, "")
        .replace(Regex("(?i)</?span\\b[^>]*>"), "")

    fun toAndroidColor(raw: String): String {
        val value = raw.trim()
        val cssHex = Regex("^#([0-9a-fA-F]{8})$").matchEntire(value) ?: return value
        val rgba = cssHex.groupValues[1]
        return "#${rgba.substring(6, 8)}${rgba.substring(0, 6)}".uppercase()
    }

    fun sections(raw: String): List<SongFormattingSection> {
        val text = normalize(raw)
        if (text.isEmpty()) return listOf(SongFormattingSection("", false))
        val result = mutableListOf<SongFormattingSection>()
        val tokenRegex = Regex("(?i)</?(?:b|i|u|mark|color(?:=[^>]+)?)>")
        val activeTags = mutableListOf<Pair<String, String>>()
        var cursor = 0
        var highlightDepth = 0
        var highlighted = false
        val buffer = StringBuilder()

        fun flush() {
            val markup = buffer.toString()
            if (markup.replace(formattingTags, "").isNotEmpty()) {
                result += SongFormattingSection(markup, highlighted)
            }
            buffer.clear()
        }

        fun reopenActiveTags() {
            activeTags.forEach { (_, openingTag) -> buffer.append(openingTag) }
        }

        tokenRegex.findAll(text).forEach { match ->
            buffer.append(text, cursor, match.range.first)
            val token = match.value
            val closing = token.startsWith("</")
            val tagName = token
                .removePrefix("<")
                .removePrefix("/")
                .substringBefore('=')
                .substringBefore('>')
                .lowercase()

            if (tagName == "mark") {
                if (!closing) {
                    if (highlightDepth == 0) {
                        flush()
                        highlighted = true
                        reopenActiveTags()
                    }
                    highlightDepth++
                } else {
                    highlightDepth = (highlightDepth - 1).coerceAtLeast(0)
                    if (highlightDepth == 0) {
                        flush()
                        highlighted = false
                        reopenActiveTags()
                    }
                }
            } else {
                buffer.append(token)
                if (!closing) {
                    activeTags += tagName to token
                } else {
                    val activeIndex = activeTags.indexOfLast { it.first == tagName }
                    if (activeIndex >= 0) activeTags.removeAt(activeIndex)
                }
            }
            cursor = match.range.last + 1
        }
        buffer.append(text, cursor, text.length)
        flush()
        return result.ifEmpty { listOf(SongFormattingSection("", false)) }
    }

    private fun decodeEntities(raw: String): String = raw
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&#160;", " ", ignoreCase = true)
        .replace("&#xA0;", " ", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&", ignoreCase = true)

    private fun canonicalColor(raw: String): String {
        val value = raw.trim()
        val rgb = Regex("(?i)^rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*([01](?:\\.\\d+)?))?\\s*\\)$")
            .matchEntire(value)
            ?: return value
        val red = rgb.groupValues[1].toInt().coerceIn(0, 255)
        val green = rgb.groupValues[2].toInt().coerceIn(0, 255)
        val blue = rgb.groupValues[3].toInt().coerceIn(0, 255)
        val alpha = rgb.groupValues[4].takeIf { it.isNotBlank() }?.toDoubleOrNull()
        return if (alpha == null || alpha >= 1.0) {
            "#%02X%02X%02X".format(red, green, blue)
        } else {
            val alphaByte = kotlin.math.round(alpha.coerceIn(0.0, 1.0) * 255.0).toInt()
            "#%02X%02X%02X%02X".format(red, green, blue, alphaByte)
        }
    }

    private data class OpenTag(val name: String, val start: Int, val endExclusive: Int)
    private data class TagPair(val open: OpenTag, val closeStart: Int, val closeEndExclusive: Int)

    private fun findTagPairs(text: String, includeMark: Boolean): List<TagPair> {
        val stack = mutableListOf<OpenTag>()
        val pairs = mutableListOf<TagPair>()
        val tagNames = if (includeMark) "b|i|u|mark|color(?:=[^>]+)?" else "b|i|u|color(?:=[^>]+)?"
        Regex("(?i)</?(?:$tagNames)>").findAll(text).forEach { match ->
            val token = match.value
            val closing = token.startsWith("</")
            val name = token.removePrefix("<").removePrefix("/").substringBefore('=').substringBefore('>').lowercase()
            if (!closing) {
                stack += OpenTag(name, match.range.first, match.range.last + 1)
            } else {
                val openIndex = stack.indexOfLast { it.name == name }
                if (openIndex >= 0) {
                    pairs += TagPair(stack.removeAt(openIndex), match.range.first, match.range.last + 1)
                }
            }
        }
        return pairs
    }

    private fun expandAcrossTouchedFormattingTags(text: String, initialStart: Int, initialEnd: Int): IntRange {
        val pairs = findTagPairs(text, includeMark = true)
        var start = initialStart
        var end = initialEnd
        var changed: Boolean
        do {
            changed = false
            pairs.forEach { pair ->
                val touchesOpen = start < pair.open.endExclusive && end > pair.open.start
                val touchesClose = start < pair.closeEndExclusive && end > pair.closeStart
                if (touchesOpen || touchesClose) {
                    val expandedStart = minOf(start, pair.open.start)
                    val expandedEnd = maxOf(end, pair.closeEndExclusive)
                    if (expandedStart != start || expandedEnd != end) {
                        start = expandedStart
                        end = expandedEnd
                        changed = true
                    }
                }
            }
        } while (changed)
        return start until end
    }
}
