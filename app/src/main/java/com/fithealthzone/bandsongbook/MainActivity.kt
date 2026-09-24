package com.fithealthzone.bandsongbook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fithealthzone.bandsongbook.data.settings.ThemeMode
import com.fithealthzone.bandsongbook.ui.BandSongbookApp
import com.fithealthzone.bandsongbook.ui.theme.BandSongbookTheme
import com.fithealthzone.bandsongbook.update.EXTRA_CHECK_UPDATES
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val updateCheckRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUpdateIntent(intent)
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
            val externalUpdateCheckRequest by updateCheckRequests.collectAsState()
            val darkTheme = when (display.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            BandSongbookTheme(darkTheme = darkTheme) {
                BandSongbookApp(externalUpdateCheckRequest = externalUpdateCheckRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CHECK_UPDATES, false) == true) {
            intent.removeExtra(EXTRA_CHECK_UPDATES)
            updateCheckRequests.value += 1
        }
    }
}
