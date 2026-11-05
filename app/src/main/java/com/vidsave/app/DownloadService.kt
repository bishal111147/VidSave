package com.vidsave.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that runs yt-dlp. Keeps downloading with the screen off or the
 * app closed, then moves the finished file into the public gallery (Movies/VidSave).
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger(0)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ID)?.let { id ->
                cancelled += id
                YoutubeDL.getInstance().destroyProcessById(id)
                jobs[id]?.cancel()
            }
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            FG_ID,
            progressNotification("Starting download…", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }

        val id = UUID.randomUUID().toString()
        Downloads.add(DownloadItem(id = id, url = url, title = url))
        active.incrementAndGet()
        jobs[id] = scope.launch {
            try {
                download(id, url)
            } finally {
                jobs.remove(id)
                if (active.decrementAndGet() == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- download

    private suspend fun download(id: String, url: String) {
        val dir = File(cacheDir, "dl/$id").apply { mkdirs() }
        try {
            Downloads.update(id) { it.copy(note = "Preparing engine…") }
            Engine.ensureReady(applicationContext)
            Downloads.update(id) { it.copy(note = "Fetching video…") }

            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                // Best video stream + best audio stream (falls back to best single file).
                addOption("-f", "bv*+ba/b")
                // Highest resolution first, then highest frame rate.
                addOption("-S", "res,fps")
                addOption("--merge-output-format", "mp4")
                addOption("-N", "8") // parallel fragment downloads
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("-o", "${dir.absolutePath}/%(title).80B.%(ext)s")
            }

            val destRegex = Regex("""Destination: (.+)""")
            YoutubeDL.getInstance().execute(request, id) { pct, _, line ->
                destRegex.find(line)?.let { m ->
                    val name = File(m.groupValues[1].trim()).nameWithoutExtension
                        .replace(Regex("""\.f\d+$"""), "")
                        .replace('_', ' ')
                    Downloads.update(id) { it.copy(title = name) }
                }
                if (pct >= 0f) {
                    Downloads.update(id) {
                        it.copy(progress = pct / 100f, note = "Downloading ${pct.toInt()}%")
                    }
                    nm.notify(FG_ID, progressNotification("Downloading ${pct.toInt()}%", pct.toInt(), false))
                }
            }

            Downloads.update(id) { it.copy(progress = 1f, note = "Saving to gallery…") }
            val file = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXT }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("No video file was produced")

            val uri = saveToGallery(file)
            Downloads.update(id) {
                it.copy(status = Status.DONE, progress = 1f, note = "Saved to Movies/VidSave", uri = uri)
            }
            notifyDone(id, titleOf(id), uri)
        } catch (e: Exception) {
            val wasCancelled = id in cancelled || e is CancellationException
            val msg = if (wasCancelled) "Cancelled" else friendly(e)
            Downloads.update(id) { it.copy(status = Status.FAILED, note = msg) }
            if (!wasCancelled) notifyFailed(id, msg)
            if (e is CancellationException) throw e
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun titleOf(id: String) =
        Downloads.items.value.firstOrNull { it.id == id }?.title ?: "Video"

    private fun friendly(e: Exception): String {
        val raw = e.message.orEmpty()
        val line = raw.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: raw.lines().lastOrNull { it.isNotBlank() }
            ?: "Download failed"
        return line.replace(Regex("""^ERROR:\s*(\[[^\]]+]\s*)?"""), "").take(160)
    }

    /** Copies the finished file into the shared Movies/VidSave folder so it shows up in the gallery. */
    private fun saveToGallery(file: File): Uri {
        val mime = when (file.extension.lowercase()) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VidSave")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create gallery entry")
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    // ----------------------------------------------------------- notifications

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(text: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VidSave")
            .setContentText(text)
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun notifyDone(id: String, title: String, uri: Uri) {
        val view = PendingIntent.getActivity(
            this, id.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            id.hashCode(),
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(title)
                .setContentIntent(view)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyFailed(id: String, msg: String) {
        nm.notify(
            id.hashCode(),
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed")
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val FG_ID = 1
        private const val EXTRA_URL = "url"
        private const val EXTRA_ID = "id"
        private const val ACTION_CANCEL = "com.vidsave.app.CANCEL"
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "m4v")

        fun start(ctx: Context, url: String) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, DownloadService::class.java).putExtra(EXTRA_URL, url)
            )
        }

        fun cancel(ctx: Context, id: String) {
            ctx.startService(
                Intent(ctx, DownloadService::class.java)
                    .setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)
            )
        }
    }
}
