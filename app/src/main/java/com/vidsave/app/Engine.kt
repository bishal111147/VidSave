package com.vidsave.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Wraps yt-dlp + ffmpeg: one-time init and periodic self-update so extractors stay fresh. */
object Engine {
    private val mutex = Mutex()
    @Volatile private var ready = false
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun initIfNeeded(ctx: Context) {
        if (!ready) {
            YoutubeDL.getInstance().init(ctx)
            FFmpeg.getInstance().init(ctx)
            ready = true
        }
    }

    suspend fun ensureReady(ctx: Context) {
        mutex.withLock {
            initIfNeeded(ctx)
            val prefs = ctx.getSharedPreferences("engine", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("lastUpdate", 0L) > DAY_MS) {
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(ctx) }
                    .onSuccess { prefs.edit().putLong("lastUpdate", now).apply() }
            }
        }
    }

    suspend fun forceUpdate(ctx: Context): String = mutex.withLock {
        runCatching {
            initIfNeeded(ctx)
            val status = YoutubeDL.getInstance().updateYoutubeDL(ctx)
            ctx.getSharedPreferences("engine", Context.MODE_PRIVATE)
                .edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
            "Engine: ${status ?: "up to date"}"
        }.getOrElse { "Update failed: ${it.message?.take(100)}" }
    }
}
