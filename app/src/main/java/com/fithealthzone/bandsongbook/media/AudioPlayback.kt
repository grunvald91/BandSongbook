package com.fithealthzone.bandsongbook.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

private const val AUDIO_CACHE_DIR = "audio_stream_cache"
private const val AUDIO_CACHE_BYTES = 500L * 1024L * 1024L

@UnstableApi
object AudioPlaybackCache {
    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    private fun getOrCreate(context: Context): SimpleCache {
        cache?.let { return it }
        val dir = File(context.cacheDir, AUDIO_CACHE_DIR).apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_BYTES)
        val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
        val created = SimpleCache(dir, evictor, dbProvider)
        cache = created
        return created
    }

    fun buildPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        val simpleCache = getOrCreate(appContext)

        val upstream = DefaultDataSource.Factory(
            appContext,
            DefaultHttpDataSource.Factory()
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
