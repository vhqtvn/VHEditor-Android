package vn.vhn.vhscode

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import james.crasher.Crasher
import kotlinx.coroutines.channels.Channel
import okio.internal.commonToUtf8String
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import vn.vhn.vhscode.root.EditorHostActivity
import vn.vhn.vhscode.root.terminal.GlobalSessionsManager
import vn.vhn.vhscode.service_features.SessionsHost
import java.io.*
import java.util.zip.GZIPInputStream


class CodeServerService() : Service() {
    companion object {
        const val kActionStopService = "stop_service"
        const val kActionAcquireWakeLock = "acquire_wake_lock"
        const val kActionReleaseWakeLock = "release_wake_lock"

        const val TAG = "CodeServerService"

        init {
            System.loadLibrary("vhcode")
        }

        @SuppressLint("SdCardPath")
        const val BASE_PATH = "/data/data/vn.vhn.vsc"
        const val ROOT_PATH = "${BASE_PATH}/files"
        const val HOME_PATH = "${ROOT_PATH}/home"
        const val VHEMOD_PATH = "${HOME_PATH}/vhe-modules/"
        const val TMP_PATH = "${ROOT_PATH}/tmp"
        const val PREFIX_PATH = ROOT_PATH
        const val BOOTJS = ".vsboot.js"
        const val ASSET_PREFIX = "/vscode_local_asset/"

        const val channelId = "VSCodeServer"
        const val channelName = "VSCodeServer"
        var instance: CodeServerService? = null

        // region CodeServer setup

        suspend fun loadZipBytes(f: suspend (content: ByteArray?) -> Unit): Unit {
            System.loadLibrary("vsc-bootstrap")
            try {
                f(getZip())
            } finally {
                unloadLibrary("vsc-bootstrap")
            }
        }

        external fun getZip(): ByteArray?
        external fun getArch(): String
        public external fun unloadLibrary(library: String)

        suspend fun extractServer(
            context: Context,
            progressChannel: Channel<Pair<Pair<Int, Int>?, String?>>,
        ) {
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
            progressChannel.send(Pair(Pair(0, 1), "Removing old installation..."))
            with(context.getFileStreamPath("code-server")) {
                if (exists()) deleteRecursively()
            }
            for (file in listOf("libc_android24++_shared.so", "node")) {
                with(context.getFileStreamPath(file)) {
                    if (exists()) delete()
                }
            }
            loadZipBytes {
                extractTarGz(
                    ByteArrayInputStream(it),
                    File(ROOT_PATH),
                    progressChannel
                )
            }

            //ensure exec permission of bash scripts in code-server
            context.getFileStreamPath("code-server")
                .walk()
                .filter { it.name.matches(".+\\.sh\$".toRegex()) }
                .forEach { it.setExecutable(true) }
            context.getFileStreamPath("node").setExecutable(true)
            File(context.getFileStreamPath("code-server"), "CSBUILD_VERSION")
                .writeText(BuildConfig.CS_VERSION)
        }

        fun copyRawResource(context: Context, resource_id: Int, output_path: String) {
            val inStream = context.resources.openRawResource(resource_id)
            val targetFile = File(output_path)
            if (targetFile.parentFile?.exists() == false) {
                targetFile.parentFile?.mkdirs2()
            }
            val outStream = FileOutputStream(targetFile)

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

        fun readRawResourceString(context: Context, resource_id: Int): String {
            val inStream = context.resources.openRawResource(resource_id)
            val outStream = ByteArrayOutputStream()

            val bufSize = 4096
            val buffer = ByteArray(bufSize)
            while (true) {
                val cnt = inStream.read(buffer)
                if (cnt <= 0) break;
                outStream.write(buffer, 0, cnt)
            }

            inStream.close()
            val content = outStream.toByteArray()
            outStream.close()

            return String(content)
        }

        suspend fun extractTarGz(
            archiveFile: ByteArrayInputStream,
            outputDir: File,
            progressChannel: Channel<Pair<Pair<Int, Int>?, String?>>,
        ) {
            val bufSize: Int = 4096
            val buffer = ByteArray(bufSize)

            var total = 0

            progressChannel.send(Pair(Pair(0, 1), "Preparing..."))
            archiveFile.mark(0)
            var reader = TarArchiveInputStream(GZIPInputStream(archiveFile))
            var currentEntry = reader.nextTarEntry
            while (currentEntry != null) {
                total += 1
                currentEntry = reader.nextTarEntry
            }

            progressChannel.send(Pair(Pair(0, total), "Total entry: $total"))

            var currentFileIndex = 0
            archiveFile.reset()
            reader = TarArchiveInputStream(GZIPInputStream(archiveFile))
            currentEntry = reader.nextTarEntry
            val links: ArrayList<Pair<String, String>> = ArrayList(50)

            while (currentEntry != null) {
                currentFileIndex++
                progressChannel.send(Pair(Pair(currentFileIndex, total), null))
                val outputFile = File(outputDir.absolutePath + "/" + currentEntry.name)
                if (!outputFile.parentFile!!.exists()) {
                    val result = outputFile.parentFile!!.mkdirs2()
                    progressChannel.send(Pair(null,
                        "RDIRECTORY ${outputFile.parentFile}: $result -> ${outputFile.parentFile!!.exists()}"))
                }
                if (currentEntry.isDirectory) {
                    val result = outputFile.mkdirs2()
                    progressChannel.send(Pair(null,
                        "DIRECTORY $outputFile: $result -> ${outputFile.exists()}"))
                } else if (currentEntry.isSymbolicLink) {
                    Log.d("SYMLINK", currentEntry.linkName + " <- " + outputFile.absolutePath)
                    progressChannel.send(Pair(null,
                        "SYMLINK ${currentEntry.linkName} <- ${outputFile.absolutePath}"))
                    Os.symlink(currentEntry.linkName, outputFile.absolutePath)
                } else if (currentEntry.isLink) {
                    progressChannel.send(Pair(null,
                        "LINK ${currentEntry.linkName} <- ${outputFile.absolutePath}"))
                    links.add(
                        Pair(
                            outputDir.absolutePath + "/" + currentEntry.linkName,
                            outputFile.absolutePath
                        )
                    )
                } else {
                    progressChannel.send(Pair(null,
                        "FILE ${outputFile.absolutePath}"))
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
            for (link in links) {
                Log.d("Link", link.first + " >> " + link.second)
                File(link.first).copyTo(File(link.second))
            }
        }
        // endregion

        fun homePath(ctx: Context): String {
            return ContextCompat.getExternalFilesDirs(ctx, null)[0].absolutePath
        }


        private fun addToEnvIfPresent(
            environment: MutableList<String>,
            name: String,
        ) {
            val value = System.getenv(name)
            if (value != null) {
                environment.add("$name=$value")
            }
        }

        fun buildEnv(vararg customEnvs: String): Array<String> {
            val envHome = ROOT_PATH

            val env = mutableListOf<String>()
            env.add("TERM=xterm-256color")
            env.add("HOME=${HOME_PATH}")
            env.add("LD_LIBRARY_PATH=${envHome}:${envHome}/usr/lib")
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                env.add("LD_PRELOAD=${envHome}/android_23.so")
            }
            env.add("PATH=${envHome}/bin:${envHome}/usr/bin:${envHome}/usr/bin/applets")
            env.add("NODE_OPTIONS=\"--require=${envHome}/globalinject.js\"")

            env.add("BOOTCLASSPATH=" + System.getenv("BOOTCLASSPATH"))
            env.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"))
            env.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"))
            env.add("EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE"))
            addToEnvIfPresent(env, "ANDROID_RUNTIME_ROOT")
            addToEnvIfPresent(env, "ANDROID_TZDATA_ROOT")
            env.add("LANG=en_US.UTF-8")
            env.add("TMPDIR=${PREFIX_PATH}/tmp")
            env.add("PREFIX=${PREFIX_PATH}")
            env.add("SHELL=${PREFIX_PATH}/usr/bin/bash")
            env.add("TERMUX_PKG_NO_MIRROR_SELECT=1")
            env.addAll(customEnvs)

            Log.d(TAG, "env = " + env.toString())

            return env.toTypedArray()
        }
    }

    private val mGlobalSessionsManager = GlobalSessionsManager()
    val globalSessionsManager = mGlobalSessionsManager
    private val mSessionHost = SessionsHost(this, mGlobalSessionsManager)
    val sessionsHost = mSessionHost

    /** This service is only bound from inside the same process and never uses IPC.  */
    internal class LocalBinder(self: CodeServerService) : Binder() {
        val service = self
    }

    private val mBinder: IBinder = LocalBinder(this)

    override fun onBind(intent: Intent): IBinder {
        mSessionHost.onBinding()
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mSessionHost.onUnbinding()
        mGlobalSessionsManager.activity = null
        return false
    }

    override fun onCreate() {
        if (!BuildConfig.GOOGLEPLAY_BUILD)
            Crasher(applicationContext)
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mSessionHost.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.apply {
            when (action) {
                kActionStopService -> actionStopService()
                kActionAcquireWakeLock -> sessionsHost.enableWakeLock(true)
                kActionReleaseWakeLock -> sessionsHost.enableWakeLock(false)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun actionStopService() {
        mSessionHost.mWantsToStop = true
        mSessionHost.killAllSessions()
        stopForeground(true)
        stopSelf()
    }

    private fun plural(cnt: Int?, singular: String, plural: String): String {
        cnt?.also {
            if (it >= 2) return it.toString() + plural
        }
        return cnt.toString() + singular
    }

    fun updateNotification() {
        val resultIntent = Intent(this, EditorHostActivity::class.java)
        val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(resultIntent)
        val resultPendingIntent: PendingIntent =
            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        var ext = ""
        if (mSessionHost.hasWakeLock) ext += "with wake-lock"
        if (mSessionHost.hasWifiLock) {
            if (ext.isNotEmpty()) ext += ", "
            ext += "with wifi-lock"
        }
        if (ext.isNotEmpty()) ext = ", $ext"

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
        @Suppress("DEPRECATION") val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.logo_b)
            .setContentTitle("VHEditor: ${
                if (mGlobalSessionsManager.sessionsHost?.mCodeServerLocalService?.hasStarted == true)
                    "editor service running, "
                else ""
            }${
                plural(mGlobalSessionsManager.sessionsHost?.mCodeServerSessions?.size,
                    " editor",
                    " editors")
            }"
                    + ", ${
                plural(mGlobalSessionsManager.sessionsHost?.mTermuxSessions?.size,
                    " terminal",
                    " terminals")
            }${
                ext
            }.")
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(resultPendingIntent)
            .addAction(
                R.drawable.icon_trash,
                getString(R.string.stop_server),
                PendingIntent.getService(this,
                    0,
                    Intent(this, CodeServerService::class.java).also {
                        it.action = kActionStopService
                    },
                    0)
            )
            .let {
                if (sessionsHost.hasWakeLock)
                    it.addAction(
                        R.drawable.icon_stop,
                        getString(R.string.release_wake_lock),
                        PendingIntent.getService(this,
                            0,
                            Intent(this, CodeServerService::class.java).also {
                                it.action = kActionReleaseWakeLock
                            },
                            0)
                    )
                else
                    it.addAction(
                        R.drawable.icon_vscode,
                        getString(R.string.acquire_wake_lock),
                        PendingIntent.getService(this,
                            0,
                            Intent(this, CodeServerService::class.java).also {
                                it.action = kActionAcquireWakeLock
                            },
                            0)
                    )
            }
            .build()
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(1, notificationBuilder.build())
        startForeground(1, notification)
    }
}


fun File.mkdirs2(): Boolean {
    if (exists()) {
        if (!canonicalFile.isDirectory) throw IOException("Trying to create directory ${path} (${canonicalPath}), it exists but is not a directory?")
        return true
    }
    if (parentFile?.mkdirs2() == false) return false
    if (!mkdir()) {
        throw IOException("Failed to create directory $path")
    }
    return true
}