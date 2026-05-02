package com.fithealthzone.bandsongbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.fithealthzone.bandsongbook.data.settings.ThemeMode
import com.fithealthzone.bandsongbook.ui.BandSongbookApp
import com.fithealthzone.bandsongbook.ui.theme.BandSongbookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            val display by AppContainer.settingsRepository.displaySettings.collectAsState(
                initial = com.fithealthzone.bandsongbook.data.settings.DisplaySettings(
                    preferFlats = false,
                    themeMode = ThemeMode.DARK,
                    lyricsFontSp = 16,
                    chordsFontSp = 14
                )
            )
            val darkTheme = when (display.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            BandSongbookTheme(darkTheme = darkTheme) {
                BandSongbookApp()
            }
        }
    }
}
