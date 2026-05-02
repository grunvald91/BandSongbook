package com.fithealthzone.bandsongbook.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/**
 * Глобальный [SnackbarHostState] приложения.
 *
 * В корне [BandSongbookApp] создаётся один экземпляр, вешается в Scaffold.snackbarHost
 * и провайдится сюда. Любой composable вниз по дереву может схватить его и показать
 * сообщение — не плодя отдельные хосты в каждом экране.
 *
 * По умолчанию бросает ошибку, если кто-то попытается использовать его вне [BandSongbookApp].
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState is not provided. Wrap the screen with BandSongbookApp.")
}
