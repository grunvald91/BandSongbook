package com.fithealthzone.bandsongbook.ui.screens

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class SongEditorLayoutRegressionTest {

    @Test
    fun `scrollable editor content does not consume keyboard inset`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongEditorScreen.kt")
            .readText()

        val scrollContentModifier = Regex(
            "\\.fillMaxSize\\(\\)\\s*\\.imePadding\\(\\)\\s*\\.verticalScroll"
        )

        assertFalse(
            "Keyboard padding on the scrollable editor content adds a large blank area and over-scrolls the cursor.",
            scrollContentModifier.containsMatchIn(source)
        )
    }
}
