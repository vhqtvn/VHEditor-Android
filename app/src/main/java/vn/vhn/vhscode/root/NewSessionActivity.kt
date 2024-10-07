package vn.vhn.vhscode.root

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.facebook.hermes.reactexecutor.HermesExecutor
import com.facebook.hermes.reactexecutor.HermesExecutorFactory
import com.facebook.react.PackageList
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactPackage
import com.facebook.react.ReactRootView
import com.facebook.react.common.LifecycleState
import com.facebook.soloader.SoLoader
import com.google.android.material.textfield.TextInputEditText
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.vhn.reactnative.modules.VHEApiModuleHandler
import vn.vhn.reactnative.modules.VHEReactNativePackage
import vn.vhn.vhscode.BuildConfig
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.GithubReleaseInstallActivity
import vn.vhn.vhscode.R
import vn.vhn.vhscode.databinding.ActivityNewSessionBinding
import vn.vhn.vhscode.preferences.EditorHostPrefs
import vn.vhn.vhscode.root.codeserver.CodeServerManager
import vn.vhn.vhscode.service_features.initialSetupIfNeededSync
import vn.vhn.vhscode.service_features.setupIfNeeded
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.URL


class NewSessionActivity : AppCompatActivity(), VHEApiModuleHandler {
    companion object {
        val TAG = "NewSessionActivity"
        val kCurrentServerVersion = "v4.93.1-" + BuildConfig.CS_VERSION

        val kVersionCheckPeriodMilli = 24 * 60 * 60 * 1000; // 1 day

        val kSessionSSL = "ssl"
        val kSessionAllInterfaces = "all-interfaces"
        val kSessionRemotePort = "remote-port"

        val kSessionType = "SESSION_TYPE"
        val kSessionTypeTerminal = "SESSION_TYPE_TERMINAL"

        val kTerminalSessionName = "TERMINAL_SESSION:name"
        val kTerminalExecutable = "TERMINAL_SESSION:executable"
        val kTerminalArguments = "TERMINAL_SESSION:arguments"

        val kSessionTypeCodeEditor = "SESSION_TYPE_CODEEDITOR"
        val kEditorPathToOpen = "EDITOR_SESSION:path"
        val kSessionTypeRemoteCodeEditor =
            "SESSION_TYPE_REMOTE_CODEEDITOR" // this session type just open a browser to host:port
        val kSessionTypeRemoteCodeServer =
            "SESSION_TYPE_REMOTE_CODESERVER" // this session type is managed over ssh

        val kRemoteCodeEditorURL = "REMOTE_CODEEDITOR_URL"

        val kIsInitialStart = "INITIAL_START"
    }

    private lateinit var binding: ActivityNewSessionBinding
    lateinit var preferences: EditorHostPrefs
    private var mCanAutoRun: Boolean = false
    private var mIgnoreAutoRunNotified: Boolean = false

    private var latestRemoteVersion: String? = null

    private lateinit var reactRootView: ReactRootView
    private lateinit var reactInstanceManager: ReactInstanceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        binding = ActivityNewSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mResultSet = false

        CodeServerService.initialSetupIfNeededSync(this)

        SoLoader.init(this, false)
        reactRootView = ReactRootView(this)
        val packages: List<ReactPackage> = PackageList(application).packages
        HermesExecutor.loadLibrary()
        reactInstanceManager = ReactInstanceManager.builder()
            .setApplication(application)
            .setCurrentActivity(this)
            .setBundleAssetName("loader.bundled.js")
            .setJSMainModulePath("loader")
            .addPackages(packages)
            .addPackage(VHEReactNativePackage())
            .setUseDeveloperSupport(false && BuildConfig.DEBUG)
            .setJavaScriptExecutorFactory(HermesExecutorFactory())
            .setInitialLifecycleState(LifecycleState.RESUMED)
            .build()
        reactRootView.startReactApplication(reactInstanceManager, "VHERoot", Bundle().also {
            it.putString("mod", "${CodeServerService.VHEMOD_PATH}/new-session")
        })
        binding.customTerminalReactHost.addView(reactRootView)

    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND
        preferences = EditorHostPrefs(this)
        configureUI()
    }

    private fun configureUI() {
        binding.txtAppVersion.movementMethod = LinkMovementMethod.getInstance()
        binding.editTxtRemoteServer.setText(preferences.defaultRemoteEditorURL)
        checkLatestVersion()
    }

    private fun ensureSetup(whenDone: () -> Unit) {
        TermuxInstaller.setupIfNeeded(this) {
            CodeServerService.setupIfNeeded(this, whenDone)
        }
    }

    fun onNewTerminal(view: View) {
        ensureSetup {
            CoroutineScope(Dispatchers.Main).launch {
                setResult(RESULT_OK, Intent().putExtra(kSessionType, kSessionTypeTerminal))
                finish()
            }
        }
    }

    private var mResultSet = false

    fun returnResult(builder: () -> Intent) {
        if (mResultSet) return
        mResultSet = true
        ensureSetup {
            CoroutineScope(Dispatchers.Main).launch {
                setResult(
                    RESULT_OK,
                    builder()
                )
                finish()
            }
        }
    }

    fun onStartCode(view: View) {
        returnResult {
            Intent()
                .putExtra(kSessionType, kSessionTypeCodeEditor)
                .putExtra(kSessionSSL, preferences.editorUseSSL)
                .putExtra(kSessionAllInterfaces, preferences.editorListenAllInterfaces)
        }
    }

    fun onStartRemote(view: View) {
        returnResult {
            val remote = binding.editTxtRemoteServer.text.toString()
            preferences.defaultRemoteEditorURL = remote
            Intent()
                .putExtra(kSessionType, kSessionTypeRemoteCodeEditor)
                .putExtra(kRemoteCodeEditorURL, remote)
        }
    }

    private fun checkLatestVersion() {
        Log.d(TAG, "checkLatestVersion")
        CoroutineScope(Dispatchers.IO).launch {
            if (preferences.latestVersionCheckTime >= System.currentTimeMillis() - kVersionCheckPeriodMilli
            ) {
                latestRemoteVersion = preferences.latestVersion
                updateUI()
                //return?
            }

            try {
                val url = URL("https://github.com/vhqtvn/VHEditor-Android/releases/latest")
                val br = BufferedReader(InputStreamReader(url.openStream()))

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
                    preferences.latestVersion = version
                    updateUI()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check latest version exception", e)
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
            Log.d(TAG, "updateUI: latestRemoteVersion=$latestRemoteVersion")
            var canAutoRun = true
            var txtVersion =
                "App version: ${BuildConfig.VERSION_NAME}, arch: ${CodeServerService.getArch()}"
            if (latestRemoteVersion != null) {
                if (latestRemoteVersion == BuildConfig.VERSION_NAME) {
                    txtVersion += ", already latest version."
                } else {
                    txtVersion += ", latest version: <a href=\"https://github.com/vhqtvn/VHEditor-Android/releases/latest\">${latestRemoteVersion}</a>"
                    txtVersion += " (<a href=\"https://github.com/vhqtvn/VHEditor-Android/blob/master/CHANGELOG.md\">ChangeLog</a>)"
                    canAutoRun = false
                }
            }
            binding.txtAppVersion.text = Html.fromHtml(txtVersion, 0)

            val codeServerVersion =
                withContext(Dispatchers.IO) { getCurrentCodeServerVersion() }
            binding.txtInstalledServerVersion.text = getString(
                R.string.server_version,
                codeServerVersion ?: getString(R.string.not_installed)
            )
            var isInstalled = !codeServerVersion.isNullOrBlank()
            if (isInstalled) {
                binding.btnStartCode.isEnabled = true
                if (codeServerVersion != kCurrentServerVersion) {
                    binding.btnInstallServer.text =
                        getString(R.string.update_server) + " (" + kCurrentServerVersion + ")"
                    binding.btnInstallServer.visibility = View.VISIBLE
                    canAutoRun = false
                } else {
                    binding.btnInstallServer.text = getString(R.string.reinstall_server)
                    binding.btnInstallServer.visibility = View.GONE
                }
            } else {
                binding.btnStartCode.isEnabled = false
                binding.btnInstallServer.text =
                    getString(R.string.install_server)
                binding.btnInstallServer.visibility = View.VISIBLE
                canAutoRun = false
            }
//            for (v in listOf(binding.installedRegionGroup)) {
//                v.visibility = if (isInstalled) View.VISIBLE else View.GONE
//            }

            mCanAutoRun = canAutoRun
            performRequestPermissions()
        }
    }

    private fun wrongTargetSDK(): Boolean {
        val targetSdkVersion = applicationContext.applicationInfo.targetSdkVersion
        return android.os.Build.VERSION.SDK_INT >= 29 && targetSdkVersion != 28
    }

    private fun checkAndAutoRun() {
        if (intent.getBooleanExtra(kIsInitialStart, false))
            when (preferences.startupTool) {
                EditorHostPrefs.StartupTool.EDITOR -> {
                    if (!mCanAutoRun) {
                        if (!mIgnoreAutoRunNotified) {
                            mIgnoreAutoRunNotified = true
                            Toast.makeText(baseContext,
                                R.string.ignore_auto_start_notification,
                                Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    onStartCode(binding.root)
                }
                else -> {}
            }
    }

    var requestedPermission: Boolean = false
    private fun performRequestPermissions() {
        CoroutineScope(Dispatchers.Main).launch {
            if (requestedPermission) {
                checkAndAutoRun()
                return@launch
            }
            requestedPermission = true
            preferences.requestedPermissions = true
            val listPermissionsNeeded = mutableListOf<String>()
            for (permission in listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
                if (ContextCompat.checkSelfPermission(this@NewSessionActivity, permission)
                    != PackageManager.PERMISSION_GRANTED
                )
                    listPermissionsNeeded.add(permission)
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(
                    this@NewSessionActivity,
                    listPermissionsNeeded.toTypedArray(),
                    0
                )
            } else {
                checkAndAutoRun()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAndAutoRun()
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
            var csVersion = String(bytes, 0, bytes.size).trim()
            val buildFile = File("$dataHome/code-server/CSBUILD_VERSION")
            if (buildFile.exists()) {
                val stream = FileInputStream(buildFile)
                val bytes = stream.readBytes()
                stream.close()
                var csBuildVersion = String(bytes, 0, bytes.size).trim()
                csVersion += "-$csBuildVersion"
            }
            return csVersion
        }
        return ""
    }

    fun onChkFullScreenClick(view: View) {
        preferences.fullScreen = (view as CheckBox).isChecked
    }

    fun onChkListenOnAllInterfacesClick(view: View) {
        preferences.editorListenAllInterfaces = (view as CheckBox).isChecked
    }

    fun onChkUseSSLClick(view: View) {
        preferences.editorUseSSL = (view as CheckBox).isChecked
    }

    fun onCheckEditorVerbose(view: View) {
        preferences.editorVerbose = (view as CheckBox).isChecked
    }

    fun onChkInitialStartEditorClick(view: View) {
        preferences.startupTool =
            if ((view as CheckBox).isChecked) EditorHostPrefs.StartupTool.EDITOR
            else EditorHostPrefs.StartupTool.NONE
    }

    fun onSettingsClick(view: View) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(R.layout.dialog_settings)
            .setNegativeButton(
                R.string.close
            ) { dialog: DialogInterface?, _: Int ->
                dialog?.dismiss()
            }
            .setOnDismissListener {
                updateUI()
            }
            .show()

        // Set checkboxes to their sharedPreferences setting state
        dialog.findViewById<CheckBox>(R.id.chkFullScreen)?.isChecked =
            preferences.fullScreen
        dialog.findViewById<CheckBox>(R.id.chkListenOnAllInterfaces)?.isChecked =
            preferences.editorListenAllInterfaces
        dialog.findViewById<CheckBox>(R.id.chkUseSSL)?.isChecked =
            preferences.editorUseSSL
        dialog.findViewById<CheckBox>(R.id.chkEditorVerbose)?.isChecked =
            preferences.editorVerbose
        dialog.findViewById<CheckBox>(R.id.chkInitialStartEditor)?.isChecked =
            preferences.startupTool == EditorHostPrefs.StartupTool.EDITOR
        dialog.findViewById<TextInputEditText>(R.id.txtSettingsLocalServerListenPort)?.apply {
            setText(preferences.editLocalServerListenPort)
            doOnTextChanged { text, start, count, after ->
                preferences.editLocalServerListenPort = text.toString()
            }
        }
    }


    fun onResetRootFS(v: View) {
        AlertDialog.Builder(this)
            .setCancelable(true)
            .setMessage(R.string.confirm_reset_rootfs)
            .setPositiveButton(android.R.string.ok) { dialog, id ->
                dialog.dismiss()
                CodeServerManager.onResetRootFS(this) {
                    updateUI()
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog, id ->
                dialog.dismiss()
            }
            .show()
    }

    fun onInstallServerClick(v: View) {
        val run = {
            CodeServerManager.runInstallServer(this) {
                TermuxInstaller.setupIfNeeded(this) {
                    updateUI()
                }
            }
        }
        if (v.id == R.id.btnSettingsInstallServer) {
            AlertDialog.Builder(this)
                .setCancelable(true)
                .setMessage(R.string.confirm_reinstall_server)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    run()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            run()
        }
    }

    override fun onVHEApiStartSession(command: String, name: String?) {
        returnResult {
            Intent().putExtra(kSessionType, kSessionTypeTerminal).also { intent ->
                name?.also { intent.putExtra(kTerminalSessionName, it) }
                intent.putExtra(kTerminalArguments,
                    listOf<String>("-c", command).toTypedArray()
                )
            }
        }
    }

    override fun onVHEApiStartRemoteCodeServerSession(
        command: String,
        arguments: ArrayList<String>,
        name: String?,
        folder: String?,
        paths: List<String>,
    ) {
        returnResult {
            val intent = Intent()
                .putExtra(kTerminalSessionName, name ?: "remote:${arguments.joinToString(" ")}")
                .putExtra(kSessionType, kSessionTypeRemoteCodeServer)
                .putExtra(kSessionSSL, preferences.editorUseSSL)
                .putExtra(kTerminalExecutable, command)
                .putStringArrayListExtra(kTerminalArguments, arguments)
            if (folder != null || paths.isNotEmpty()) {
                val pathsToOpen = mutableListOf<String>()
                if (folder != null) pathsToOpen.add(folder)
                pathsToOpen.addAll(paths)
                intent.putExtra(kEditorPathToOpen, pathsToOpen.toTypedArray())
            }
            intent
        }
    }

    override fun onVHEApiOpenEditorPath(folder: String?, paths: List<String>) {
        returnResult {
            val intent = Intent()
                .putExtra(kSessionType, kSessionTypeCodeEditor)
                .putExtra(kSessionSSL, preferences.editorUseSSL)
                .putExtra(kSessionAllInterfaces, preferences.editorListenAllInterfaces)
            if (folder != null || paths.isNotEmpty()) {
                val pathsToOpen = mutableListOf<String>()
                if (folder != null) pathsToOpen.add(folder)
                pathsToOpen.addAll(paths)
                intent.putExtra(kEditorPathToOpen, pathsToOpen.toTypedArray())
            }
            intent
        }
    }
}