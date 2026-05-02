package com.fithealthzone.bandsongbook.transpose

import org.junit.Assert.assertEquals
import org.junit.Test

class ChordTransposerTest {

    // --- Базовые ноты ---

    @Test
    fun `transposeChord C plus 2 is D`() {
        assertEquals("D", ChordTransposer.transposeChord("C", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord C minus 2 in sharp mode is A sharp`() {
        assertEquals("A#", ChordTransposer.transposeChord("C", -2, preferFlats = false))
    }

    @Test
    fun `transposeChord C minus 2 in flat mode is B flat`() {
        assertEquals("Bb", ChordTransposer.transposeChord("C", -2, preferFlats = true))
    }

    // --- Минорные аккорды сохраняют суффикс ---

    @Test
    fun `transposeChord Am plus 2 is Bm`() {
        assertEquals("Bm", ChordTransposer.transposeChord("Am", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord F sharp m plus 1 is Gm`() {
        assertEquals("Gm", ChordTransposer.transposeChord("F#m", 1, preferFlats = false))
    }

    // --- Слэш-аккорды (bass note) ---

    @Test
    fun `transposeChord C slash G plus 2 is D slash A`() {
        assertEquals("D/A", ChordTransposer.transposeChord("C/G", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord G slash B minus 2 is F slash A`() {
        assertEquals("F/A", ChordTransposer.transposeChord("G/B", -2, preferFlats = false))
    }

    // --- Расширения (7, maj7, sus4, dim, aug) ---

    @Test
    fun `transposeChord Cmaj7 plus 2 keeps maj7`() {
        assertEquals("Dmaj7", ChordTransposer.transposeChord("Cmaj7", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord C7 plus 2 keeps 7`() {
        assertEquals("D7", ChordTransposer.transposeChord("C7", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord Csus4 plus 2 keeps sus4`() {
        assertEquals("Dsus4", ChordTransposer.transposeChord("Csus4", 2, preferFlats = false))
    }

    @Test
    fun `transposeChord Adim plus 2 keeps dim`() {
        assertEquals("Bdim", ChordTransposer.transposeChord("Adim", 2, preferFlats = false))
    }

    // --- Граничные случаи (обёртка через октаву) ---

    @Test
    fun `transposeChord B plus 1 wraps to C`() {
        assertEquals("C", ChordTransposer.transposeChord("B", 1, preferFlats = false))
    }

    @Test
    fun `transposeChord C minus 1 wraps to B`() {
        assertEquals("B", ChordTransposer.transposeChord("C", -1, preferFlats = false))
    }

    @Test
    fun `transposeChord with semitones larger than octave normalizes correctly`() {
        // +14 == +2 по модулю 12
        assertEquals("D", ChordTransposer.transposeChord("C", 14, preferFlats = false))
    }

    @Test
    fun `transposeChord with zero semitones returns same note`() {
        assertEquals("G", ChordTransposer.transposeChord("G", 0, preferFlats = false))
    }

    // --- Enharmonic spelling: sharp vs flat ---

    @Test
    fun `C sharp normalized to D flat when preferFlats true`() {
        // C# есть в sharp-ряду на индексе 1, targetMap=flat[1] = Db
        assertEquals("Db", ChordTransposer.transposeChord("C#", 0, preferFlats = true))
    }

    @Test
    fun `D flat normalized to C sharp when preferFlats false`() {
        assertEquals("C#", ChordTransposer.transposeChord("Db", 0, preferFlats = false))
    }

    // --- transposeLyrics: аккорды в квадратных скобках ---

    @Test
    fun `transposeLyrics shifts bracketed chords and leaves text intact`() {
        val source = "[C]hello [G]world"
        val expected = "[D]hello [A]world"
        assertEquals(expected, ChordTransposer.transposeLyrics(source, 2, preferFlats = false))
    }

    @Test
    fun `transposeLyrics keeps multi-line structure`() {
        val source = "[Am]line one\n[F]line two\n[C]line three"
        val expected = "[Bm]line one\n[G]line two\n[D]line three"
        assertEquals(expected, ChordTransposer.transposeLyrics(source, 2, preferFlats = false))
    }

    @Test
    fun `transposeLyrics handles slash chords inside brackets`() {
        val source = "[C/G]verse [Am7]chorus"
        val expected = "[D/A]verse [Bm7]chorus"
        assertEquals(expected, ChordTransposer.transposeLyrics(source, 2, preferFlats = false))
    }

    @Test
    fun `transposeLyrics with zero semitones is identity`() {
        val source = "[Am]первая [F]вторая [C]третья [G]четвёртая"
        assertEquals(source, ChordTransposer.transposeLyrics(source, 0, preferFlats = false))
    }

    // --- Некорректный вход возвращается как есть ---

    @Test
    fun `unrecognized token is returned unchanged`() {
        // "H" не входит в A..G; регулярка первой же проверкой вернёт null → возврат исходного
        assertEquals("H", ChordTransposer.transposeChord("H", 2, preferFlats = false))
    }
}
