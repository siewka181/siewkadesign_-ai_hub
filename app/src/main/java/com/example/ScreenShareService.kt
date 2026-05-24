package com.example

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.OpenClawClient
import java.io.ByteArrayOutputStream

class ScreenShareService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isSharing = false
    private var lastFrameTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 8816
        private const val CHANNEL_ID = "CLAW_SCREEN_SHARE_CHANNEL"

        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA_INTENT = "DATA_INTENT"
        const val EXTRA_WIDTH = "WIDTH"
        const val EXTRA_HEIGHT = "HEIGHT"
        const val EXTRA_DPI = "DPI"
        
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val dataIntent = intent.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)

        if (resultCode == Activity.RESULT_CANCELED || dataIntent == null) {
            Log.e("ScreenShareService", "Invalid Result Code or Data Intent provided.")
            stopSelf()
            return START_NOT_STICKY
        }

        val width = intent.getIntExtra(EXTRA_WIDTH, 720)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1280)
        val dpi = intent.getIntExtra(EXTRA_DPI, 240)

        startForegroundNotification()

        startScreenCapture(resultCode, dataIntent, width, height, dpi)

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val stopIntent = Intent(this, ScreenShareService::class.java).apply {
            action = "STOP_SHARE"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Claw Dynamic Assistant")
            .setContentText("Continuous live screen sharing simulation active.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (e: Exception) {
                Log.e("ScreenShareService", "Error starting media projection foreground service: ${e.localizedMessage}", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenCapture(resultCode: Int, dataIntent: Intent, width: Int, height: Int, dpi: Int) {
        if (isSharing) return

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, dataIntent)

        if (mediaProjection == null) {
            Log.e("ScreenShareService", "MediaProjection was null!")
            stopSelf()
            return
        }

        // Limit maximum size to prevent excessive processing overhead (e.g. 720p limit)
        val targetWidth = if (width > height) 1280 else 720
        val targetHeight = if (width > height) 720 else 1280

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        // Setup ImageReader for screen grabs
        imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenSharingDisp",
            targetWidth, targetHeight, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        isSharing = true

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            val currentTime = System.currentTimeMillis()
            // Throttle to max ~6 FPS to secure transport bandwidth
            if (currentTime - lastFrameTime < 160) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastFrameTime = currentTime

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                var bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // If bitmap contains padding, crop to correct aspect ratios
                if (rowPadding > 0) {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                    bitmap.recycle()
                    bitmap = cropped
                }

                // Compress frames as JPEG 70% Quality for maximum speed
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val jpegBytes = outputStream.toByteArray()
                bitmap.recycle()

                // Dispatch video packet
                OpenClawClient.sendVideoFrame(jpegBytes)

            } catch (e: Exception) {
                Log.e("ScreenShareService", "Error during screen compression: ${e.localizedMessage}")
                image.close()
            }
        }, backgroundHandler)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("ScreenShareService", "MediaProjection stopped externally")
                stopSelf()
            }
        }, backgroundHandler)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Share Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isSharing = false
        isServiceRunning = false
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null

        super.onDestroy()
        Log.d("ScreenShareService", "ScreenShareService stopped completely.")
    }
}
