package dev.resonance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var latestStartId = 0
    @Volatile private var stopping = false
    @Volatile private var activeConnection: HttpURLConnection? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastWakeRenewal = 0L
    private val app
        get() = application as ResonanceApp

    private val notifications
        get() = getSystemService(NotificationManager::class.java)

    private fun text(id: String): String {
        val language =
            resolveLanguage(
                appPreferences()[AppSettings.Language],
                resources.configuration.locales[0],
            )
        return Copy(language)[id]
    }

    override fun onCreate() {
        super.onCreate()
        // A killed Service can leave a running row while the Application survives.
        app.library.recoverDownloads()
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Resonance:offline-download")
                .apply { setReferenceCounted(false) }
        notifications.createNotificationChannel(
            NotificationChannel(
                "downloads",
                text("downloads"),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun notice(title: String, progress: Int = 0): Notification =
        Notification.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_resonance)
            .setContentTitle("Resonance")
            .setContentText(title)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (stopping) {
            if (app.downloadStopReason.value.isEmpty())
                app.pauseDownloadRecovery("download_interrupted")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        startForeground(
            42,
            notice(text("download_queued")),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        startWorker()
        return START_NOT_STICKY
    }

    private fun renewWakeLock() {
        val now = System.nanoTime()
        if (!stopping && (!wakeLock.isHeld || now - lastWakeRenewal >= 30_000_000_000L)) {
            wakeLock.acquire(DownloadPolicy.WAKE_LOCK_TIMEOUT_MS)
            lastWakeRenewal = now
        }
    }

    private fun releaseWakeLock() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    private fun startWorker() {
        if (!stopping && worker?.isActive != true)
            worker = scope.launch {
                try {
                    while (isActive && !stopping) {
                        val entry = app.library.nextDownload() ?: break
                        try {
                            renewWakeLock()
                            download(entry)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            if (stopping || !isActive) break
                            if (app.library.get(entry.id)?.downloadState == "paused") continue
                            val reason =
                                when (error.message) {
                                    "storage_full",
                                    "integrity",
                                    "lan_permission",
                                    "mac_key" -> error.message!!
                                    else -> "download_failed"
                                }
                            if (app.library.get(entry.id)?.downloadState != "paused")
                                app.library.downloadProgress(
                                    entry.id,
                                    "failed",
                                    app.library.get(entry.id)?.downloadBytes ?: 0,
                                    reason,
                                )
                            // One failed connection, rather than one timeout for every queued file.
                            if (
                                error is IOException ||
                                    reason in
                                        listOf(
                                            "download_failed",
                                            "lan_permission",
                                            "mac_key",
                                            "storage_full",
                                        )
                            ) {
                                stopping = true
                                app.pauseDownloadRecovery(reason)
                                break
                            }
                        } finally {
                            releaseWakeLock()
                        }
                    }
                } finally {
                    app.library.recoverDownloads()
                    releaseWakeLock()
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        worker = null
                        // Only a live worker finishing normally can admit a racing new request.
                        if (scope.isActive && !stopping && app.library.nextDownload() != null)
                            startWorker()
                        else {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelfResult(latestStartId)
                        }
                    }
                }
            }
    }

    private fun download(entry: MediaEntry) {
        val db = app.library
        if (db.hasOfflineCopy(entry.id)) {
            db.downloadProgress(entry.id, "complete", entry.bytes)
            return
        }
        require(entry.contentHash.matches(Regex("[a-f0-9]{64}")))
        val directory = File(filesDir, "offline").apply { mkdirs() }
        val part = File(directory, "${entry.contentHash}.part")
        val target = File(directory, "${entry.contentHash}.media")
        if (target.exists()) {
            val hash =
                target.inputStream().use {
                    Fingerprint.read(it) {
                        renewWakeLock()
                        !stopping && worker?.isActive != false
                    }
                }
            if (hash.first == entry.contentHash && hash.second == entry.bytes) {
                db.finishDownload(entry.id, Uri.fromFile(target).toString(), hash.second)
                return
            }
        }
        var offset = part.length()
        if (offset > entry.bytes) {
            RandomAccessFile(part, "rw").use { it.setLength(0) }
            offset = 0
        }
        check(
            StatFs(filesDir.path).availableBytes >
                entry.bytes - offset + DownloadPolicy.STORAGE_RESERVE_BYTES
        ) {
            "storage_full"
        }
        db.downloadProgress(entry.id, "running", offset)
        if (offset < entry.bytes) {
            val connection = app.mac.connection("/media/${entry.contentHash}", offset)
            activeConnection = connection
            try {
                val code = connection.responseCode
                check(code == 200 || code == 206) { "download_failed" }
                if (code == 206)
                    check(
                        connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") ==
                            true
                    ) {
                        "download_failed"
                    }
                if (code == 200 && offset > 0) offset = 0
                RandomAccessFile(part, "rw").use { output ->
                    if (offset == 0L) output.setLength(0)
                    output.seek(offset)
                    var checkpoint = System.nanoTime()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            check(
                                !stopping &&
                                    worker?.isActive != false &&
                                    db.get(entry.id)?.downloadState != "paused"
                            ) {
                                "cancelled"
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(offset + count <= entry.bytes) { "integrity" }
                            output.write(buffer, 0, count)
                            offset += count
                            if (System.nanoTime() - checkpoint > 1_000_000_000) {
                                renewWakeLock()
                                output.fd.sync()
                                db.downloadProgress(entry.id, "running", offset)
                                notifications.notify(
                                    42,
                                    app.library.downloadSummary().let { summary ->
                                        notice(
                                            "${summary.complete}/${summary.total} · ${entry.displayName.title}",
                                            if (summary.totalBytes > 0)
                                                (summary.transferredBytes * 100 /
                                                        summary.totalBytes)
                                                    .toInt()
                                            else 0,
                                        )
                                    },
                                )
                                checkpoint = System.nanoTime()
                            }
                        }
                    }
                    output.fd.sync()
                }
            } finally {
                activeConnection = null
                connection.disconnect()
            }
        }
        check(offset == entry.bytes) { "download_failed" }
        val verified =
            part.inputStream().use {
                Fingerprint.read(it) {
                    renewWakeLock()
                    !stopping &&
                        worker?.isActive != false &&
                        db.get(entry.id)?.downloadState != "paused"
                }
            }
        if (verified.first != entry.contentHash || verified.second != entry.bytes) {
            RandomAccessFile(part, "rw").use { it.setLength(0) }
            db.downloadProgress(entry.id, "running", 0)
            error("integrity")
        }
        check(
            !stopping && worker?.isActive != false && db.get(entry.id)?.downloadState != "paused"
        ) {
            "cancelled"
        }
        if (db.hasOfflineCopy(entry.id)) {
            part.delete()
            db.downloadProgress(entry.id, "complete", entry.bytes)
            return
        }
        check(part.renameTo(target)) { "download_failed" }
        db.finishDownload(entry.id, Uri.fromFile(target).toString(), verified.second)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopping = true
        app.pauseDownloadRecovery("download_timeout")
        activeConnection?.disconnect()
        worker?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        activeConnection?.disconnect()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
