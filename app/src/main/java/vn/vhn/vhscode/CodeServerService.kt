package vn.vhn.vhscode

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*


class CodeServerService : Service() {

    companion object {
        val ROOT_PATH = "/data/data/vn.vhn.vhscode/files"
        val HOME_PATH = ROOT_PATH + "/home"
        val PREFIX_PATH = ROOT_PATH

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

        public fun isServerStarting(): Boolean {
            return isServerStarting
        }
    }

    var started = false
    var process: Process? = null
    var error: String? = null;

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
        Thread {
            runServer()
            started = false
        }.start()
        updateNotification()
    }

    private fun stopForegroundService() {
        if (!started) return
        started = false
        process?.destroy()
        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
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

        var status: String = ""
        if (isServerStarting) {
            if (liveServerStarted.value == true) status = "running";
            else status = "starting"
        } else {
            status = "not running"
        }
        if (error != null) {
            status += ", error: $error"
        }

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VSCode Server is ${status}.")
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
    }

    private fun addToEnvIfPresent(
        environment: MutableList<String>,
        name: String
    ) {
        val value = System.getenv(name)
        if (value != null) {
            environment.add("$name=$value")
        }
    }

    fun buildEnv(): Array<String> {
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath

        val env = mutableListOf<String>()
        env.add("TERM=xterm-256color")
        env.add("HOME=${envHome}/home")
        env.add("LD_LIBRARY_PATH=${envHome}:${envHome}/usr/lib")
        env.add("PATH=${envHome}/usr/bin:${envHome}/usr/bin/applets")

        env.add("BOOTCLASSPATH=" + System.getenv("BOOTCLASSPATH"))
        env.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"))
        env.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"))
        env.add("EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE"))
        addToEnvIfPresent(env, "ANDROID_RUNTIME_ROOT")
        addToEnvIfPresent(env, "ANDROID_TZDATA_ROOT")
        env.add("LANG=en_US.UTF-8")
        env.add("TMPDIR=${PREFIX_PATH}/tmp")

        env.add("PORT=13337")

        return env.toTypedArray()
    }

    fun runServer() {
        if (isServerStarting || (liveServerStarted.value == true)) return
        isServerStarting = true
        liveServerStarted.postValue(null)
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        var logData = "Starting...\n"
        try {
            error = null;
            liveServerStarted.postValue(false)
            buildEnv()
            process = Runtime.getRuntime().exec(
                arrayOf(
                    applicationContext.getFileStreamPath("node").absolutePath,
                    "${envHome}/code-server/release-static",
                    "--auth",
                    "none"
                ),
                buildEnv()
            )
            val stream = DataInputStream(process!!.inputStream);
            val bufSize = kConfigStreamBuferSize
            val buffer = ByteArray(bufSize)
            var outputBuffer = ""
            var serverStarted = false
            updateNotification()
            liveServerLog.postValue(logData)
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
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000)
                            updateNotification()
                        }
                    }
                }
                logData += currentBuffer
                liveServerLog.postValue(logData)
            }
            liveServerStarted.postValue(false)
        } catch (e: Exception) {
            error = e.toString()
            logData += "\nException: ${error}"
            liveServerLog.postValue(logData)
            liveServerStarted.postValue(false)
        } finally {
            isServerStarting = false
            updateNotification()
            logData += "Finished.\n"
            liveServerLog.postValue(logData)
        }
    }
}
