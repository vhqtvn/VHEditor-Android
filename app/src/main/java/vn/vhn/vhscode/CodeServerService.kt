package vn.vhn.vhscode

import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Environment
import android.os.IBinder
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.*
import java.util.zip.GZIPInputStream


class CodeServerService : Service() {

    companion object {
        val ROOT_PATH = "/data/data/vn.vhn.vsc/files"
        val HOME_PATH = ROOT_PATH + "/home"
        val PREFIX_PATH = ROOT_PATH
        val BOOTJS = ".vsboot.js"

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

        fun isServerStarting(): Boolean {
            return isServerStarting
        }

        fun setupIfNeeded(context: Context, whenDone: () -> Unit) {
            val dpkg_path = "$PREFIX_PATH/usr/bin/dpkg"
            val dpkg_orig_path = "$dpkg_path.bin-orig"
            if (!File(dpkg_orig_path).isFile) {
                File(dpkg_path).renameTo(File(dpkg_orig_path))
                copyRawResource(context, R.raw.dpkg, dpkg_path.toString())
                File(dpkg_path).setExecutable(true)
            }
            whenDone()
        }

        suspend fun extractServer(context: Context, progressChannel: Channel<Pair<Int, Int>>) {
            val userService: UserManager =
                context.getSystemService(Context.USER_SERVICE) as UserManager
            val isPrimaryUser =
                userService.getSerialNumberForUser(android.os.Process.myUserHandle()) == 0L
            if (!isPrimaryUser) {
                AlertDialog.Builder(context).setTitle(R.string.error_title)
                    .setMessage(R.string.error_not_primary_user_message)
                    .setOnDismissListener(DialogInterface.OnDismissListener { _: DialogInterface? ->
                        System.exit(
                            0
                        )
                    }).setPositiveButton(android.R.string.ok, null).show()
                return
            }
            copyRawResource(context, R.raw.libcpp, "$ROOT_PATH/libc++_shared.so")
            copyRawResource(context, R.raw.cs, "$ROOT_PATH/cs.tgz")
            val csSourceFile = context.getFileStreamPath("cs.tgz")
            with(context.getFileStreamPath("code-server")) {
                if (exists()) deleteRecursively()
            }
            extractTarGz(
                csSourceFile,
                csSourceFile.parentFile,
                progressChannel
            )
            csSourceFile.delete()
            copyRawResource(context, R.raw.node, "$ROOT_PATH/node")
            context.getFileStreamPath("node").setExecutable(true)
        }

        fun copyRawResource(context: Context, resource_id: Int, output_path: String) {
            val inStream = context.resources.openRawResource(resource_id)
            val outStream = FileOutputStream(File(output_path))

            val bufSize = 4096
            val buffer = ByteArray(bufSize)
            while (true) {
                val cnt = inStream.read(buffer)
                if (cnt <= 0) break;
                outStream.write(buffer, 0, cnt)
            }

            inStream.close()
            outStream.close()
        }

        suspend fun extractTarGz(
            archiveFile: File,
            outputDir: File,
            progressChannel: Channel<Pair<Int, Int>>
        ) {
            val bufSize: Int = 4096
            val buffer = ByteArray(bufSize)

            var total = 0

            var reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
            var currentEntry = reader.nextTarEntry
            while (currentEntry != null) {
                total += 1
                currentEntry = reader.nextTarEntry
            }

            progressChannel.send(Pair(0, total))

            var currentFileIndex = 0
            reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
            currentEntry = reader.nextTarEntry

            while (currentEntry != null) {
                currentFileIndex++
                progressChannel.send(Pair(currentFileIndex, total))
                val outputFile = File(outputDir.absolutePath + "/" + currentEntry.name)
                if (!outputFile.parentFile!!.exists()) {
                    outputFile.parentFile!!.mkdirs()
                }
                if (currentEntry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    val outStream = FileOutputStream(outputFile)
                    while (true) {
                        val size = reader.read(buffer)
                        if (size <= 0) break;
                        outStream.write(buffer, 0, size)
                    }
                    outStream.close()
                }
                currentEntry = reader.nextTarEntry
            }
        }

        fun homePath(ctx: Context): String {
            return ContextCompat.getExternalFilesDirs(ctx, null)[0].absolutePath
        }

        fun getBootjs(ctx: Context): String? {
            val HOME = homePath(ctx)
            val configFile = File("$HOME/$BOOTJS")
            if (!configFile.exists()) {
                copyRawResource(ctx, R.raw.vsboot, configFile.absolutePath)
            }
            val stream = FileInputStream(configFile)
            val data = stream.readBytes()
            stream.close()
            return """
                	(function(){
                        let click = false;
                        let click2 = false;
                        const clickTimer = function(){
                            click=true;click2=true;
                            setTimeout(()=>{click=false;},300)
                        };
                        document.body.addEventListener('click', ()=>{click2=false;});
                        document.body.addEventListener('touchstart', clickTimer);
                        document.body.addEventListener('touchmove', ()=>{click=false;});
                        document.body.addEventListener('touchend', (e)=>{
                            if(click) {
                                setTimeout(()=>{
                                    if(click2) e.target.click();
                                },100);
                            }
                        });
                    })()
            """.trimIndent() + String(data)
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
            runServer(applicationContext)
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

    fun buildEnv(ctx: Context): Array<String> {
        val envHome = ROOT_PATH

        val env = mutableListOf<String>()
        env.add("TERM=xterm-256color")
        env.add("HOME=${homePath(ctx)}")
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

    fun runServer(ctx: Context) {
        if (isServerStarting || (liveServerStarted.value == true)) return
        isServerStarting = true
        liveServerStarted.postValue(null)
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        var logData = "Starting...\n"
        try {
            error = null;
            liveServerStarted.postValue(false)
            process = Runtime.getRuntime().exec(
                arrayOf(
                    applicationContext.getFileStreamPath("node").absolutePath,
                    "${envHome}/code-server/release-static",
                    "--auth",
                    "none"
                ),
                buildEnv(ctx)
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
