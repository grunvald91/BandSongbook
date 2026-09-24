package com.fithealthzone.bandsongbook.formatting

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFormattingTest {
    @Test
    fun `website markup is normalized to the android canonical format`() {
        val raw = "<hl class=\"bb-section-highlight\"><strong>Припев</strong><br>Строка&nbsp;2</hl>\n<font color=\"#84adff\">цвет</font>"

        assertEquals(
            "<mark><b>Припев</b>\nСтрока 2</mark>\n<color=#84adff>цвет</color>",
            SongFormatting.normalize(raw)
        )
    }

    @Test
    fun `website rgb colors and legacy color tags remain visible without literal html`() {
        val raw = "<span style=\"color: rgb(132, 173, 255); background-color: yellow\">Синий</span> <c v=\"rgb(255, 113, 108)\">Красный</c>"

        assertEquals(
            "<color=#84ADFF>Синий</color> <color=#FF716C>Красный</color>",
            SongFormatting.normalize(raw)
        )
    }

    @Test
    fun `highlight wraps only the exact selected chorus and preserves surrounding blank lines`() {
        val text = "Куплет\n\nПрипев 1\nПрипев 2\n\nКуплет 2"
        val start = text.indexOf("Припев")
        val end = text.indexOf("\n\nКуплет 2")

        val edit = SongFormatting.toggleHighlight(text, start, end)

        assertEquals("Куплет\n\n<mark>Припев 1\nПрипев 2</mark>\n\nКуплет 2", edit.text)
        assertEquals(start + "<mark>".length, edit.selectionStart)
        assertEquals(end + "<mark>".length, edit.selectionEnd)
    }

    @Test
    fun `highlight toggles off when selection is anywhere inside existing chorus block`() {
        val text = "Куплет\n<mark>Припев 1\nПрипев 2</mark>\nКуплет 2"
        val start = text.indexOf("Припев 2")
        val end = start + "Припев 2".length

        val edit = SongFormatting.toggleHighlight(text, start, end)

        assertEquals("Куплет\nПрипев 1\nПрипев 2\nКуплет 2", edit.text)
        assertEquals(text.indexOf("<mark>"), edit.selectionStart)
        assertEquals(text.indexOf("<mark>") + "Припев 1\nПрипев 2".length, edit.selectionEnd)
    }

    @Test
    fun `full reset removes android and website formatting but preserves lyrics chords and line breaks`() {
        val raw = "<mark><b>Припев [Am]</b></mark><br><font color=\"#fff\"><i>Строка</i></font>"

        assertEquals("Припев [Am]\nСтрока", SongFormatting.clearAll(raw))
    }

    @Test
    fun `full reset preserves literal angle bracket lyrics`() {
        assertEquals("Love <you> forever", SongFormatting.clearAll("Love &lt;you&gt; <b>forever</b>"))
    }

    @Test
    fun `empty website div produces one line break not two`() {
        assertEquals("A\nB", SongFormatting.normalize("A<div><br></div>B"))
        assertEquals("line\nNext", SongFormatting.normalize("line<div>Next</div>"))
        assertEquals("First\nSecond", SongFormatting.normalize("<div>First</div><div>Second</div>"))
    }

    @Test
    fun `highlight expands across formatting tags instead of creating crossing markup`() {
        val edit = SongFormatting.toggleHighlight("<b>x</b>", 0, 4)

        assertEquals("<mark><b>x</b></mark>", edit.text)
    }

    @Test
    fun `highlight toggles off when visible selection begins before hidden opening mark`() {
        val edit = SongFormatting.toggleHighlight("<mark>abc</mark>", 0, 9)

        assertEquals("abc", edit.text)
    }

    @Test
    fun `generic formatting wrapper balances tags touched by hidden editor offsets`() {
        val edit = SongFormatting.wrapBalanced("<i>abc</i>", 0, 4, "<b>", "</b>")

        assertEquals("<b><i>abc</i></b>", edit.text)
        assertEquals(
            "<b><mark>abc</mark></b>",
            SongFormatting.wrapBalanced("<mark>abc</mark>", 0, 7, "<b>", "</b>").text
        )
    }

    @Test
    fun `css alpha hex stays in web order and converts only for android parser`() {
        assertEquals("<color=#11223380>x</color>", SongFormatting.normalize("<span style=\"color: rgba(17, 34, 51, 0.5)\">x</span>"))
        assertEquals("#80112233", SongFormatting.toAndroidColor("#11223380"))
    }

    @Test
    fun `highlight sections keep one multi line chorus block`() {
        val sections = SongFormatting.sections("Куплет\n\n<mark>Припев 1\nПрипев 2</mark>\n\nКуплет 2")

        assertEquals(3, sections.size)
        assertEquals(false, sections[0].highlighted)
        assertEquals("Куплет\n\n", sections[0].markup)
        assertEquals(true, sections[1].highlighted)
        assertEquals("Припев 1\nПрипев 2", sections[1].markup)
        assertEquals(false, sections[2].highlighted)
        assertEquals("\n\nКуплет 2", sections[2].markup)
    }

    @Test
    fun `formatting remains active when a highlighted section is nested inside it`() {
        val sections = SongFormatting.sections("<b>До <mark>Припев</mark> после</b>")

        assertEquals(
            listOf(
                SongFormattingSection("<b>До ", false),
                SongFormattingSection("<b>Припев", true),
                SongFormattingSection("<b> после</b>", false)
            ),
            sections
        )
    }

    @Test
    fun `editor exposes an explicit full song formatting reset`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongEditorScreen.kt").readText()

        assertTrue(source.contains("SongFormatting.clearAll(lyricsField.text)"))
        assertTrue(source.contains("contentDescription = \"Полностью сбросить форматирование песни\""))
        assertTrue(source.contains("SongFormatting.wrapBalanced("))
    }

    @Test
    fun `saving does not persist heuristic wrapping of ordinary lyric words`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/viewmodel/SongEditorViewModel.kt").readText()

        assertTrue(!source.contains("ChordDetector.autoWrapChords"))
    }
}
