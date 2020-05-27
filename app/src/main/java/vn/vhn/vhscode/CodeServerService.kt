package vn.vhn.vhscode

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.lang.Exception


class CodeServerService : Service() {

    companion object {
        val kActionStartService = "ACTION_START_SERVICE"
        val kActionStopService = "ACTION_STOP_SERVICE"
        val channelId = "VSCodeServer"
        val channelName = "VSCodeServer"
        var instance: CodeServerService? = null
        val liveServerStarted: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
        val liveServerLog: MutableLiveData<String> by lazy { MutableLiveData<String>() }
        private var isServerStarting = false

        fun startService(context: Context) {
            val intent = Intent(context, CodeServerService::class.java)
            intent.action = kActionStartService
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CodeServerService::class.java)
            context.stopService(intent)
        }
    }

    var started = false
    var process: Process? = null

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        process?.destroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            Log.d("VH", "Handle intent " + intent.action)
            when (intent.action) {
                kActionStartService -> startForegroundService()
                kActionStopService -> stopForegroundService()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundService() {
        if (started) return
        started = true
        createNotificationChannel()
    }

    private fun stopForegroundService() {
        if (!started) return
        started = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val resultIntent = Intent(this, MainActivity::class.java)
        val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(resultIntent)
        val resultPendingIntent: PendingIntent =
            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, CodeServerService::class.java)
        stopIntent.action = kActionStopService
        val pendingStopStackBuilder = TaskStackBuilder.create(this)
        pendingStopStackBuilder.addNextIntent(stopIntent)
        val pendingStopIntent =
            pendingStopStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val chan =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VSCode Server is running")
            .setPriority(NotificationManager.IMPORTANCE_MAX)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(resultPendingIntent)
//            .addAction(
//                R.drawable.ic_launcher_foreground,
//                getString(R.string.stop_server),
//                pendingStopIntent
//            )
            .build()
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(1, notificationBuilder.build())
        startForeground(1, notification)
        Thread {
            runServer()
        }.start()
    }

    fun runServer() {
        if (isServerStarting || (liveServerStarted.value == true)) return
        isServerStarting = true
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        try {
            liveServerStarted.postValue(false)
            process = Runtime.getRuntime().exec(
                arrayOf(
                    applicationContext.getFileStreamPath("node").absolutePath,
                    "${envHome}/code-server/release-static",
                    "--auth",
                    "none"
                ),
                arrayOf(
                    "HOME=${envHome}",
                    "LD_LIBRARY_PATH=${envHome}",
                    "PORT=13337"
                )
            )
            val stream = DataInputStream(process!!.inputStream);
            val bufSize = kConfigStreamBuferSize
            val buffer = ByteArray(bufSize)
            var outputBuffer = ""
            var serverStarted = false
            while (process?.isAlive == true) {
                val size = stream.read(buffer)
                if (size <= 0) continue
                val currentBuffer = String(buffer, 0, size)
                Log.d("VHSServerOutput", currentBuffer)
                if (!serverStarted) {
                    outputBuffer += currentBuffer
                    if (outputBuffer.indexOf("HTTP server listening on") >= 0) {
                        serverStarted = true
                        outputBuffer = ""
                        liveServerStarted.postValue(true)
                    }
                }
//                liveServerLog.value += currentBuffer
            }
            liveServerStarted.postValue(false)
        } catch (e: Exception) {
            liveServerStarted.postValue(false)
        }
        isServerStarting = false
    }
}
