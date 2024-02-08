package com.yilmaz.screenrecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class RecorderService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var mVirtualDisplay: VirtualDisplay
    private lateinit var currentVideoUri: Uri

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = applicationContext.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        getNotificationManager()

        when (intent.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stop()
            else -> throw IllegalArgumentException("Unexpected action onStartCommand -> ${intent.action}")
        }
        return START_STICKY
    }

    private fun start(intent: Intent) {
        IS_SERVICE_RUNNING = true
        startForeground(1, buildNotification())
        startRecorder(intent)
    }

    private fun stop() {
        IS_SERVICE_RUNNING = false
        stopRecorder()
    }

    @SuppressLint("Recycle")
    @Suppress("DEPRECATION")
    private fun startRecorder(intent: Intent) {
        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + File.separator + getString(R.string.app_name)
            )
            put(MediaStore.Video.Media.DISPLAY_NAME, "temp_video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        val resolver = applicationContext.contentResolver
        currentVideoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!

        val metrics = DisplayMetrics()
        val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(8 * 1000 * 1000)
            setVideoFrameRate(15)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setOutputFile(
                resolver.openFileDescriptor(
                    currentVideoUri,
                    "w"
                )?.fileDescriptor
            )
        }

        try {
            mediaRecorder.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(
            Activity.RESULT_OK, intent.getParcelableExtra(
                EXTRA_RESULT_DATA
            )!!
        ) as MediaProjection

        mVirtualDisplay = mediaProjection.createVirtualDisplay(
            "MainActivity",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            mediaRecorder.surface,
            null,
            null
        )!!
        mediaRecorder.start()
    }

    private fun stopRecorder() {
        mediaRecorder.stop()
        mediaProjection.stop()
        mediaRecorder.release()
        mVirtualDisplay.release()

        currentVideoUri.let { uri ->
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            applicationContext.contentResolver.update(uri, values, null, null)
        }
        Toast.makeText(this, "Video saved successfully", Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            CHANNEL_ID,
            NOTIFICATION_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            setShowBadge(true)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun getNotificationManager() {
        notificationManager = ContextCompat.getSystemService(
            this,
            NotificationManager::class.java
        )!!
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setContentText("Recording")
            .setSmallIcon(R.drawable.baseline_circle_24)
            .setOnlyAlertOnce(true)
            .setContentIntent(pIntent)
            .build()
    }

    companion object {
        var IS_SERVICE_RUNNING = false
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "RECORDER_SERVICE_CHANNEL_ID"
        const val NOTIFICATION_NAME = "RECORDER_SERVICE_NOTIFICATION_NAME"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }
}