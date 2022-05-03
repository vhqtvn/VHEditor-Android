package vn.vhn.vhscode

import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.*
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream


class CodeServerService : Service() {
    companion object {
        val TAG = "CodeServerService"
        val BASE_PATH = "/data/data/vn.vhn.vsc"
        val ROOT_PATH = "${BASE_PATH}/files"
        val HOME_PATH = "${ROOT_PATH}/home"
        val PREFIX_PATH = ROOT_PATH
        val BOOTJS = ".vsboot.js"
        val ASSET_PREFIX = "/vscode_local_asset/"

        val kActionStartService = "ACTION_START_SERVICE"
        val kActionStopService = "ACTION_STOP_SERVICE"
        val channelId = "VSCodeServer"
        val channelName = "VSCodeServer"
        var instance: CodeServerService? = null
        val liveServerStarted: MutableLiveData<Int> = MutableLiveData<Int>().apply { postValue(0) }
        val liveServerLog: MutableLiveData<String> =
            MutableLiveData<String>().apply { postValue("") }
        private var isServerStarting = false
        private val kPrefListenOnAllInterfaces = "listenstar"
        private val kPreUseSSL = "ssl"

        fun startService(
            context: Context,
            listenOnAllInterface: Boolean = false,
            useSSL: Boolean = true
        ) {
            val intent = Intent(context, CodeServerService::class.java)
            intent.action = kActionStartService
            intent.putExtra(kPrefListenOnAllInterfaces, listenOnAllInterface)
            intent.putExtra(kPreUseSSL, useSSL)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CodeServerService::class.java)
            intent.action = kActionStopService
            context.stopService(intent)
        }

        fun isServerStarting(): Boolean {
            return isServerStarting
        }

        // https://stackoverflow.com/questions/813710/java-1-6-determine-symbolic-links
        private fun isSymlink(file: File): Boolean {
            val canon: File
            canon = if (file.parent == null) {
                file
            } else {
                val canonDir = file.parentFile.canonicalFile
                File(canonDir, file.name)
            }
            return canon.canonicalFile != canon.absoluteFile
        }

        fun clearCacheFolder(dir: File?): Int {
            var deletedFiles = 0
            if (dir != null && dir.isDirectory) {
                try {
                    for (child in dir.listFiles()) {
                        if (child.isDirectory) {
                            deletedFiles += clearCacheFolder(child)
                        }

                        if (child.delete()) {
                            deletedFiles++
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Log.e(
                        TAG,
                        String.format("Failed to clean the cache, error %s", e.message)
                    )
                }
            }
            return deletedFiles
        }


        fun setupIfNeeded(context: Context, whenDone: () -> Unit) {
            // region home symlink
            HOME_PATH.apply {
                val homeFile = File(this)
                if (homeFile.canonicalFile.equals(homeFile.canonicalFile)) {
                    homeFile.delete()
                    homeFile.mkdir()
                }
                File("$this/storage").apply {
                    if (!exists()) {
                        val oldHome = homePath(context)
                        if (File(oldHome).exists()) {
                            Runtime.getRuntime()
                                .exec("mv $oldHome/* $oldHome/.[!.]* $oldHome/..?* $HOME_PATH/")
                                .waitFor()
                        }
                        Os.symlink(oldHome, absolutePath)
                    }
                }
            }
            // region

            // region setup certs
            if (true || !File("$HOME_PATH/cert.cert").exists()) {
                copyRawResource(context, R.raw.cert_crt, "$HOME_PATH/cert.cert")
                copyRawResource(context, R.raw.cert_key, "$HOME_PATH/cert.key")
            }
            // region

            // region setup trusted gpg
            "$PREFIX_PATH/usr/etc/apt/trusted.gpg.d/vhnvn.gpg".apply {
                copyRawResource(context, R.raw.vhnvn, this)
            }
            // endregion

            // region patch apt sources lists
            listOf(File("$PREFIX_PATH/usr/etc/apt/sources.list")).forEach {
                it.bufferedWriter()
                    .use { it.write("deb https://vsc.vhn.vn/termux-packages-24/ stable main") }
            }
            // endregion

            // region patch for code-server 4.3.0, adding without-connection-token option
            do {
                val util_path = "$PREFIX_PATH/code-server/release-standalone/out/node/cli.js"
                val util_orig_path =
                    "$PREFIX_PATH/code-server/release-standalone/out/node/cli_orig_vhcode.js"
                val util = File(util_path)
                if (!util.exists()) break
                if (util.readText().contains("without-connection-token")) break
                try {
                    File(util_orig_path).delete()
                } catch (_: Exception) {
                }
                util.renameTo(File(util_orig_path))
                File(util_path).writeText("""
                    module.exports = require('./cli_orig_vhcode')
                    Object.assign(
                        module.exports.options,
                        {
                            'without-connection-token': {type: "boolean",desc:'zzz'},
                            'connection-token': {type: "string",desc:'zzz1'},
                            'connection-token-file': {type: "string",desc:'zzz2'},
                        }
                    )
                """)
            } while (false)
            // endregion

            "$PREFIX_PATH/boot.sh".apply {
                copyRawResource(context, R.raw.boot, this)
                File(this).setExecutable(true)
            }

            File("$PREFIX_PATH/usr/lib/libc++_shared.so").copyTo(
                File("$ROOT_PATH/libc++_shared.so"),
                true
            )

            // region global inject
            "$PREFIX_PATH/globalinject.js".apply {
                copyRawResource(context, R.raw.globalinject, this)
            }
            // endregion
            whenDone()
        }

        fun loadZipBytes(): ByteArray? {
            System.loadLibrary("vsc-bootstrap")
            return getZip()
        }

        external fun getZip(): ByteArray?

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
            with(context.getFileStreamPath("code-server")) {
                if (exists()) deleteRecursively()
            }
            for (file in listOf("libc_android24++_shared.so", "node")) {
                with(context.getFileStreamPath(file)) {
                    if (exists()) delete()
                }
            }
            extractTarGz(
                ByteArrayInputStream(loadZipBytes()),
                File(ROOT_PATH),
                progressChannel
            )

            //ensure exec permission of bash scripts in code-server
            context.getFileStreamPath("code-server")
                .walk()
                .filter { it.name.matches(".+\\.sh\$".toRegex()) }
                .forEach { it.setExecutable(true) }
            context.getFileStreamPath("node").setExecutable(true)
        }

        fun copyRawResource(context: Context, resource_id: Int, output_path: String) {
            val inStream = context.resources.openRawResource(resource_id)
            val targetFile = File(output_path)
            if (targetFile.parentFile?.exists() == false) {
                targetFile.parentFile?.mkdirs()
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

        suspend fun extractTarGz(
            archiveFile: ByteArrayInputStream,
            outputDir: File,
            progressChannel: Channel<Pair<Int, Int>>
        ) {
            val bufSize: Int = 4096
            val buffer = ByteArray(bufSize)

            var total = 0

            archiveFile.mark(0)
            var reader = TarArchiveInputStream(GZIPInputStream(archiveFile))
            var currentEntry = reader.nextTarEntry
            while (currentEntry != null) {
                total += 1
                currentEntry = reader.nextTarEntry
            }

            progressChannel.send(Pair(0, total))

            var currentFileIndex = 0
            archiveFile.reset()
            reader = TarArchiveInputStream(GZIPInputStream(archiveFile))
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
            val fontname = "firacode" //TODO: add more fonts and let user configure
            val HOME = HOME_PATH
            val configFile = File("$HOME/$BOOTJS")
            if (!configFile.exists()) {
                copyRawResource(ctx, R.raw.vsboot, configFile.absolutePath)
            }
            val stream = FileInputStream(configFile)
            var windowScriptBytes = stream.readBytes()
            stream.close()
            var windowScript = """
                	(function(){
                        let click = false;
                        let click2 = false;
                        const clickTimer = function(){
                            click=true;click2=true;
                            setTimeout(()=>{click=false;},300)
                        };
                        const dispatchMouseUp = function(e) {
                            var ev = document.createEvent ('MouseEvents');
                            ev.initEvent ('mouseup', true, true);
                            e.dispatchEvent(ev);
                            ev = document.createEvent ('MouseEvents');
                            ev.initEvent ('click', true, true);
                            e.dispatchEvent(ev);
                        };
                        document.body.addEventListener('click', ()=>{click2=false;});
                        document.body.addEventListener('touchstart', clickTimer);
                        document.body.addEventListener('touchmove', ()=>{click=false;});
                        const classLists = [
                            'monaco-list-row',
                            'monaco-select-box'
                        ];
                        document.body.addEventListener('touchend', (e) =>{
                            var node = e.target;
                            if(node) {
                                if(classLists.some(x=>node.classList.contains(x))) {
                                    dispatchMouseUp(node);
                                    return;
                                } else if(node.parentNode && classLists.every(x=>node.parentNode.classList.contains(x))) {
                                    dispatchMouseUp(node.parentNode);
                                    return;
                                }
                            }
                            if(click) {
                                setTimeout(()=>{
                                    if(click2 && ["A"].indexOf(e.target.tagName)==-1) {
                                        if(node) node.click();
                                    }
                                },100);
                            }
                        });
                    })();
                    (function(){
                        if (document.querySelector("#vscode_font_css")) return;
                        var link = document.createElement( "link" );
                        link.href = "${ASSET_PREFIX}fonts/$fontname/font.css";
                        link.type = "text/css";
                        link.rel = "stylesheet";
                        link.id = "vscode_font_css";
                        link.media = "screen,print";
                        document.getElementsByTagName( "head" )[0].appendChild( link );
                    })();
            """.trimIndent() + String(windowScriptBytes)
            windowScript += """
                (function(){
                    if(!window.vscodeOrigKeyboardEventDescriptorShiftKey) window.vscodeOrigKeyboardEventDescriptorShiftKey = Object.getOwnPropertyDescriptor(window.KeyboardEvent.prototype, 'shiftKey');
                    var shiftGetter = window.vscodeOrigKeyboardEventDescriptorShiftKey.get;
                    Object.defineProperty(window.KeyboardEvent.prototype, 'shiftKey', {
                        get(){
                            let orig = shiftGetter.apply(this);
                            if (orig) return true;
                            if (typeof this.cached_shift_pressed === 'undefined') {
                                this.cached_shift_pressed = _vn_vhn_vscjs_.isShiftKeyPressed()
                            }
                            return this.cached_shift_pressed;
                        }
                    });
                })()
            """.trimIndent()
            return """
                (function(){
                    var single_window_apply = function(window){
                        if(window.__vscode_boot_included__) return;
                        window.__vscode_boot_included__ = true;
                        var document = window.document;
                        var local_apply = function(){
                            if(!document.body) {
                                setTimeout(local_apply, 100);
                                return;
                            }
                            if(typeof _vn_vhn_vscjs_!=='undefined') {
                                var mkstring;
                                mkstring = async function(x) {
                                    let t = typeof x;
                                    if (t==="undefined") return "";
                                    if (t!=="object") return t.toString();
                                    if (Array.isArray(x)) {
                                        let result = "";
                                        for(const i of x) result += await mkstring(i);
                                        return result;
                                    }
                                    if(x instanceof ClipboardItem) {
                                        return await a.getType("text/plain");
                                    } else {
                                        return x.toString();
                                    }
                                 }
                                Object.defineProperty(window.navigator,'clipboard',{value:{
                                    write: async function(data) {
                                        return _vn_vhn_vscjs_.copyToClipboard(await mkstring(data));
                                    },
                                    writeText: function(txt) {
                                        return _vn_vhn_vscjs_.copyToClipboard(txt);
                                    },
                                    readText: function() {
                                        return _vn_vhn_vscjs_.getClipboard();
                                    }
                                }, writable: false});
                            }
                            Object.defineProperty(window.navigator,'onLine',{value:true, writable: false});
                            $windowScript
                        };
                        local_apply();
                	}
                	single_window_apply(window);
                	var vscode_boot_frames_inner = function(window){
                	    for(var i=0;i<window.frames.length;i++) {
                            single_window_apply(window.frames[i]);
                            vscode_boot_frames_inner(window.frames[i]);
                        }
                	}
                	window.vscode_boot_frames = function(){
                	    vscode_boot_frames_inner(window);
                	}
                	window.vscode_boot_frames();
                })()
            """.trimIndent()
        }
    }

    var started = false
    var process: Process? = null
    var stderrThread: Thread? = null
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
                kActionStartService -> {
                    started = true
                    Thread {
                        runServer(
                            applicationContext,
                            intent.getBooleanExtra(kPrefListenOnAllInterfaces, false),
                            intent.getBooleanExtra(kPreUseSSL, true)
                        )
                        started = false
                    }.start()
                    startForegroundService()
                }
                kActionStopService -> stopForegroundService()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun startForegroundService() {
        if (started) return
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
        if (instance == null) return
        val resultIntent = Intent(this, MainActivity::class.java)
        val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(resultIntent)
        val resultPendingIntent: PendingIntent =
            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, CodeServerService::class.java)
        stopIntent.action = kActionStopService
        val pendingStopStackBuilder = TaskStackBuilder.create(this)
        pendingStopStackBuilder.addNextIntent(stopIntent)
//        val pendingStopIntent =
//            pendingStopStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT
            )
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        var status: String
        if (isServerStarting) {
            if (liveServerStarted.value == 1) status = "running";
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
            .setSmallIcon(R.drawable.logo_b)
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

    fun buildEnv(ctx: Context, listenOnAllInterface: Boolean): Array<String> {
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

        Log.d(TAG, "env = " + env.toString())

        return env.toTypedArray()
    }

    fun runServer(ctx: Context, listenOnAllInterface: Boolean, useSSL: Boolean) {
        if (isServerStarting || (liveServerStarted.value != 0)) return
        isServerStarting = true
        liveServerStarted.postValue(-1)
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        var logData = "Starting... listenOnAllInterface=${listenOnAllInterface}\n"
        try {
            error = null;
            liveServerStarted.postValue(0)
            val codeServerRoot = listOf(
                "${envHome}/code-server/release-standalone",
                "${envHome}/code-server/release-static"
            ).reduce { x, y -> if (File(y).exists()) y else x }

            try {
                Runtime.getRuntime().exec("killall node").inputStream.read()
            }catch (_: java.lang.Exception){}

            val cmd = arrayOf(
                "${ROOT_PATH}/usr/bin/bash",
                applicationContext.getFileStreamPath("boot.sh").absolutePath,
                codeServerRoot,
                "--disable-telemetry",
                "--disable-update-check"
            ) + (if (useSSL) arrayOf(
                "--cert", "$HOME_PATH/cert.cert",
                "--cert-key", "$HOME_PATH/cert.key"
            ) else arrayOf()) +
                    arrayOf(
                        "--auth",
                        "none",
                        "--host",
                        if (listenOnAllInterface) "0.0.0.0" else "127.0.0.1",
                        "--port",
                        "13337",
                        "--without-connection-token"
                    )
            val env = buildEnv(ctx, listenOnAllInterface)
            File("$PREFIX_PATH/run-vs-code.sh").bufferedWriter().use {
                for (e in env) it.write("$e ")
                for (c in cmd) it.write("$c ")
            }
            process = Runtime.getRuntime().exec(cmd, env)
            val stream = DataInputStream(process!!.inputStream);
            val errStream = DataInputStream(process!!.errorStream);
            val bufSize = kConfigStreamBuferSize
            val buffer = ByteArray(bufSize)
            var outputBuffer = ""
            var serverStarted = false
            updateNotification()
            liveServerLog.postValue(logData)
            stderrThread = Thread {
                while (true) {
                    try {
                        process?.exitValue()
                        break
                    } catch (e: IllegalThreadStateException) {
                    }
                    try {
                        val size = errStream.read(buffer)
                        if (size <= 0) continue
                        val currentBuffer = String(buffer, 0, size)
                        logData += currentBuffer
                        liveServerLog.postValue(logData)
                    } catch (e: Exception) {
                    }
                }
            }
            stderrThread?.start()
            while (true) {
                try {
                    process?.exitValue()
                    break
                } catch (e: IllegalThreadStateException) {
                }

                try {
                    val size = stream.read(buffer)
                    if (size <= 0) continue
                    val currentBuffer = String(buffer, 0, size)
                    Log.d("VHSServerOutput", currentBuffer)
                    if (!serverStarted) {
                        outputBuffer += currentBuffer
                        if (outputBuffer.indexOf("HTTPS server listening on") >= 0) {
                            serverStarted = true
                            outputBuffer = ""
                            liveServerStarted.postValue(1)
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(1000)
                                updateNotification()
                            }
                        }
                    }
                    logData += currentBuffer
                    liveServerLog.postValue(logData)
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
            error = e.toString()
            logData += "\nException: ${error}"
            liveServerLog.postValue(logData)
        } finally {
            isServerStarting = false
            updateNotification()
            logData += "Finished.\n"
            liveServerLog.postValue(logData)
            liveServerStarted.postValue(0)
            try {
                stderrThread?.interrupt()
            } catch (e: Exception) {
            }
            stderrThread = null
        }
    }
}
