package vn.vhn.vhscode

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.termux.app.TermuxInstaller
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.lang.Exception
import java.net.URL
import java.util.*


class MainActivity : AppCompatActivity() {
    companion object {
        val kCurrentServerVersion = "4.0.2"
        val kPrefHardKeyboard = "hardkb"
        val kPrefKeepScreenAlive = "screenalive"
        val kPrefRemoteServer = "remoteserver"
        val kPrefListenOnAllInterfaces = "listenstar"
        val kPrefUseSSL = "ssl"
        val kPrefUseFullscreen = "fullscreen"
        val kPrefScale = "uiscale"
        val kPrefRequestedPermission = "requestedpermission"
        val kVersionCheckPeriodMilli = 24 * 60 * 60 * 1000; // 1 day
        val kPrefLatestVersionCachedValue = "cached:latestversion:value"
        val kPrefLatestVersionCachedTime = "cached:latestversion:time"
    }

    var startServerObserver: Observer<Int>? = null
    var serverLogObserver: Observer<String>? = null
    var requestedPermission: Boolean = false
    var latestRemoteVersion: String? = null

    private var scaleLabelTextView: TextView? = null

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        configureUI()
        updateUI()
    }

    override fun onDestroy() {
        if (startServerObserver != null) {
            CodeServerService.liveServerStarted.removeObserver(startServerObserver!!)
            startServerObserver = null
        }
        if (serverLogObserver != null) {
            CodeServerService.liveServerLog.removeObserver(serverLogObserver!!)
            serverLogObserver = null
        }
        super.onDestroy()
    }

    fun cleanupStartServerObserver() {
        if (startServerObserver != null) {
            CodeServerService.liveServerStarted.removeObserver(
                startServerObserver!!
            )
            startServerObserver = null
        }
    }

    @Suppress("DEPRECATION")
    suspend fun startServerService() {
        File(CodeServerService.HOME_PATH).mkdirs()
        if (CodeServerService.liveServerStarted.value != 1) {
            if (startServerObserver == null) {
                val progressDialog = ProgressDialog(this)
                progressDialog.setTitle(R.string.starting_server)
                progressDialog.setMessage(getString(R.string.please_wait_starting_server))
                progressDialog.setCancelable(false)
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
                progressDialog.show()
                var skipOnce = false
                startServerObserver = Observer<Int> { value ->
                    if (value == 1) {
                        progressDialog.dismiss()
                        cleanupStartServerObserver()
                        startEditor()
                    } else {
                        if (value == 0 && !skipOnce) {
                            skipOnce = true
                            return@Observer
                        }
                        if (!CodeServerService.isServerStarting()) {
                            progressDialog.dismiss()
                            cleanupStartServerObserver()
                        }
                    }
                }
                CodeServerService.liveServerStarted.observeForever(startServerObserver!!)
                CodeServerService.startService(
                    this,
                    sharedPreferences().getBoolean(kPrefListenOnAllInterfaces, false),
                    sharedPreferences().getBoolean(kPrefUseSSL, true)
                )
            }
        } else {
            startEditor()
        }
    }

    fun stopServerService() {
        if (CodeServerService.liveServerStarted.value == 1) {
            CodeServerService.stopService(this)
        }
    }

    fun startEditor(url: String? = null) {
        val intent = Intent(this, VSCodeActivity::class.java)

        //add sharedPreferences to intent to configure the intent acording to them afterwards
        intent.putExtra(VSCodeActivity.kConfigUseHardKeyboard, chkHardKeyboard.isChecked)
        intent.putExtra(
            VSCodeActivity.kConfigUseFullscreen,
            sharedPreferences().getBoolean(kPrefUseFullscreen, true)
        )
        intent.putExtra(
            VSCodeActivity.kConfigScale,
            sharedPreferences().getInt(kPrefScale, 3)
        )
        if (url != null) intent.putExtra(VSCodeActivity.kConfigUrl, url)
        intent.putExtra(VSCodeActivity.kConfigScreenAlive, chkKeepScreenAlive.isChecked)

        startActivity(intent)
    }

    fun configureUI() {
        serverLogObserver = Observer<String> { txt ->
            runOnUiThread {
                txtServerLog.setTextKeepState(txt)
                if (txtServerLog.layout == null) return@runOnUiThread
                val scrollAmount =
                    txtServerLog.layout.getLineTop(txtServerLog.lineCount) - txtServerLog.height;
                if (scrollAmount > 0)
                    txtServerLog.scrollTo(0, scrollAmount);
                else
                    txtServerLog.scrollTo(0, 0);
            }
        }
        CodeServerService.liveServerLog.observeForever(serverLogObserver!!)
        btnStopCode.isEnabled = CodeServerService.liveServerStarted.value == 1
        CodeServerService.liveServerStarted.observeForever { value ->
            btnStopCode.isEnabled = value == 1
        }

        chkHardKeyboard.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefHardKeyboard, isChecked).apply()
        }
        chkKeepScreenAlive.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefKeepScreenAlive, isChecked).apply()
        }

        editTxtRemoteServer.setText(
            sharedPreferences().getString(
                kPrefRemoteServer,
                "https://127.0.0.1:13337"
            )
        )
        editTxtRemoteServer.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sharedPreferences().edit().putString(kPrefRemoteServer, s.toString()).apply()
            }
        })

        creditLine.movementMethod = LinkMovementMethod.getInstance()
        txtAppVersion.movementMethod = LinkMovementMethod.getInstance()

        checkLatestVersion()
    }

    private fun checkLatestVersion() {
        CoroutineScope(Dispatchers.IO).launch {
            if (sharedPreferences().getLong(
                    kPrefLatestVersionCachedTime,
                    0
                ) >= System.currentTimeMillis() - kVersionCheckPeriodMilli
            ) {
                latestRemoteVersion =
                    sharedPreferences().getString(kPrefLatestVersionCachedValue, "---")
                updateUI()
            }

            try {
                val url = URL("https://github.com/vhqtvn/VHEditor-Android/releases/latest")
                val br = BufferedReader(
                    InputStreamReader(
                        url.openStream()
                    )
                )

                var inputLine: String?
                var version = ""

                val versionExtractor =
                    Regex("\"/vhqtvn/VHEditor-Android/releases/tag/v([\\d\\.]+)\"")

                while (br.readLine().also { inputLine = it } != null) {
                    if (inputLine != null) {
                        val matches = versionExtractor.find(inputLine!!)
                        if (matches != null) {
                            version = matches!!.groupValues[1]
                        }
                    }
                }

                br.close()

                if (version != "" && version != latestRemoteVersion) {
                    latestRemoteVersion = version
                    sharedPreferences().edit()
                        .putLong(kPrefLatestVersionCachedTime, System.currentTimeMillis())
                        .putString(kPrefLatestVersionCachedValue, version)
                        .commit()
                    updateUI()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun updateUI() {
        if (wrongTargetSDK()) {
            val intent = Intent(this, GithubReleaseInstallActivity::class.java)
            startActivity(intent)
            finish()
        }
        CoroutineScope(Dispatchers.Main).launch {
            var txtVersion = "App version: ${BuildConfig.VERSION_NAME}"
            if (latestRemoteVersion != null) {
                if (latestRemoteVersion == BuildConfig.VERSION_NAME) {
                    txtVersion += ", already latest version."
                } else {
                    txtVersion += ", latest version: <a href=\"https://github.com/vhqtvn/VHEditor-Android/releases/latest\">${latestRemoteVersion}</a>"
                }
            }
            txtAppVersion.text = Html.fromHtml(txtVersion, 0)

            sharedPreferences().apply {
                chkHardKeyboard.isChecked = getBoolean(kPrefHardKeyboard, false)
                chkKeepScreenAlive.isChecked = getBoolean(kPrefKeepScreenAlive, false)
            }
            val codeServerVersion =
                withContext(Dispatchers.IO) { getCurrentCodeServerVersion() }
            txtInstalledServerVersion.text = getString(
                R.string.server_version,
                codeServerVersion ?: getString(R.string.not_installed)
            )
            if (codeServerVersion.isNullOrBlank()) {
                btnStartCode.isEnabled = false
                btnInstallServer.text =
                    getString(R.string.install_server)
                btnInstallServer.visibility = View.VISIBLE
                installedRegionGroup.visibility = View.GONE
            } else {
                btnStartCode.isEnabled = true
                if (codeServerVersion != kCurrentServerVersion) {
                    btnInstallServer.text = getString(R.string.update_server)
                    btnInstallServer.visibility = View.VISIBLE
                } else {
                    btnInstallServer.text = getString(R.string.reinstall_server)
                    btnInstallServer.visibility = View.GONE
                }
                installedRegionGroup.visibility = View.VISIBLE
            }

            performRequestPermissions()
        }
    }

    private fun wrongTargetSDK(): Boolean {
        val targetSdkVersion = applicationContext.applicationInfo.targetSdkVersion
        return android.os.Build.VERSION.SDK_INT >= 29 && targetSdkVersion != 28
    }

    private fun performRequestPermissions() {
        CoroutineScope(Dispatchers.Main).launch {
            if (requestedPermission) return@launch
            requestedPermission = true
            sharedPreferences().edit().putBoolean(kPrefRequestedPermission, true).apply()
            val listPermissionsNeeded = mutableListOf<String>()
            for (permission in listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
                if (ContextCompat.checkSelfPermission(this@MainActivity, permission)
                    != PackageManager.PERMISSION_GRANTED
                )
                    listPermissionsNeeded.add(permission)
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    listPermissionsNeeded.toTypedArray(),
                    0
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

    }

    suspend fun getCurrentCodeServerVersion(): String? {
        val node = getFileStreamPath("node")
        if (!node.exists()) return null
        val dataHome = node.parent!!.toString()
        val versionFile = File("$dataHome/code-server/VERSION")
        if (versionFile.exists()) {
            val stream = FileInputStream(versionFile)
            val bytes = stream.readBytes()
            stream.close()
            return String(bytes, 0, bytes.size).trim()
        }
        return ""
    }

    private fun sharedPreferences(): SharedPreferences {
        return getSharedPreferences("main_settings", Context.MODE_PRIVATE)
    }

    fun onInstallServerClick(view: View) {
        CoroutineScope(Dispatchers.Main).launch {
            var progressBar: ProgressBar
            var progressText: TextView
            val dialog = Dialog(this@MainActivity).apply {
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                progressBar = findViewById(R.id.progressBar)
                progressText = findViewById(R.id.progressText)
                setTitle(R.string.extracting)
            }
            var currentProgress: Int = 0
            var progressMax: Int = 0
            var finished = false
            launch {
                var uiProgress: Int = -1
                var uiProgressMax: Int = -1
                while (!finished) {
                    delay(50)
                    if (uiProgress != currentProgress || uiProgressMax != progressMax) {
                        uiProgress = currentProgress
                        uiProgressMax = progressMax
                        withContext(Dispatchers.Main) {
                            progressBar.progress =
                                currentProgress * progressBar.max / maxOf(1, progressMax)
                            progressText.text = "%d/%d".format(uiProgress, uiProgressMax)
                        }
                    }
                }
            }
            val progressChannel = Channel<Pair<Int, Int>>()
            dialog.show()
            dialog.window!!.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            launch {
                delay(1000)
                CodeServerService.extractServer(this@MainActivity, progressChannel)
                updateUI()
                finished = true
                progressChannel.close()
            }
            for ((progress, max) in progressChannel) {
                currentProgress = progress
                progressMax = max
            }
            dialog.hide()
        }
    }

    fun onSettingsClick(view: View) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(R.layout.dialog_settings)
            .setNegativeButton(
                R.string.close
            ) { dialog: DialogInterface?, which: Int ->
                dialog?.dismiss()
            }
            .setOnDismissListener {
                updateUI()
            }
            .show()

        // Set checkboxes to their sharedPreferences setting state
        dialog.findViewById<CheckBox>(R.id.chkListenOnAllInterfaces).isChecked =
            sharedPreferences().getBoolean(kPrefListenOnAllInterfaces, false)
        dialog.findViewById<CheckBox>(R.id.chkUseSSL).isChecked =
            sharedPreferences().getBoolean(kPrefUseSSL, true)
        dialog.findViewById<CheckBox>(R.id.chkIsFullscreen).isChecked =
            sharedPreferences().getBoolean(kPrefUseFullscreen, true)

        // Scaling range 25-300% => 275% seekbar length with 25% steps = 11*25%
        val scalingFactor = sharedPreferences().getInt(kPrefScale, 3)
        scaleLabelTextView = dialog.findViewById(R.id.zoomScaleLabel)
        scaleLabelTextView?.text =
            resources.getString(R.string.zoomValue, (scalingFactor * 25 + 25))
        val seekbar = dialog.findViewById<SeekBar>(R.id.zoomScaleSeekBar)
        seekbar.progress = scalingFactor
        seekbar.setOnSeekBarChangeListener(seekBarEventListener)
    }

    fun onStartRemote(view: View) {
        startEditor(editTxtRemoteServer.text.toString())
    }

    fun onStartCode(view: View) {
        TermuxInstaller.setupIfNeeded(this) {
            CodeServerService.setupIfNeeded(this) {
                CoroutineScope(Dispatchers.Main).launch {
                    startServerService()
                }
            }
        }
    }

    fun onStopCode(view: View) {
        CoroutineScope(Dispatchers.Main).launch {
            stopServerService()
        }
    }

    @Suppress("DEPRECATION")
    fun onResetRootFS(view: View) {
        val progress = ProgressDialog.show(
            this,
            null,
            getString(R.string.deleting),
            true,
            false
        )
        Thread {
            val runtime = Runtime.getRuntime()
            try {
                runtime.exec("rm -rf ${CodeServerService.ROOT_PATH}; mkdir ${CodeServerService.ROOT_PATH}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.reset_root_fs_finished,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                runOnUiThread {
                    progress.dismiss()
                }
            }
        }.start()
    }


    fun onChkListenOnAllInterfacesClick(view: View) {
        sharedPreferences().edit()
            .putBoolean(kPrefListenOnAllInterfaces, (view as CheckBox).isChecked).apply()
    }

    fun onChkUseSSLClick(view: View) {
        sharedPreferences().edit()
            .putBoolean(kPrefUseSSL, (view as CheckBox).isChecked).apply()

    }

    fun onChkIsFullscreen(view: View) {
        sharedPreferences().edit()
            .putBoolean(kPrefUseFullscreen, (view as CheckBox).isChecked).apply()
    }

    val seekBarEventListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(p0: SeekBar?) {}

        override fun onProgressChanged(view: SeekBar, progress: Int, userMade: Boolean) {
            scaleLabelTextView?.text = resources.getString(R.string.zoomValue, (progress * 25 + 25))
        }

        override fun onStopTrackingTouch(view: SeekBar) {
            sharedPreferences().edit()
                .putInt(kPrefScale, view.progress).apply()
        }
    }
}
