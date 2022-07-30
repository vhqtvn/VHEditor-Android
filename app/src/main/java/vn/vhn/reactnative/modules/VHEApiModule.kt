package vn.vhn.reactnative.modules

import com.facebook.react.bridge.*
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.root.NewSessionActivity
import java.io.File

interface VHEApiModuleHandler {
    fun onVHEApiStartSession(command: String, name: String?)
    fun onVHEApiOpenEditorPath(path: String)
}

class VHEApiModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "VHEApi"

    val mDefaultHandler = object : VHEApiModuleHandler {
        override fun onVHEApiStartSession(command: String, name: String?) {
            TODO("Not yet implemented")
        }

        override fun onVHEApiOpenEditorPath(path: String) {
            TODO("Not yet implemented")
        }
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun startSession(config: ReadableMap) {
        if (!config.hasKey("command")) throw RuntimeException("No command defined")
        var handler: VHEApiModuleHandler = mDefaultHandler
        (currentActivity as? VHEApiModuleHandler)?.apply {
            handler = this
        }
        handler.onVHEApiStartSession(
            config.getString("command")!!,
            config.getString("title")
        )
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun openEditor(config: ReadableMap) {
        if (!config.hasKey("path")) throw RuntimeException("No path defined")
        var handler: VHEApiModuleHandler = mDefaultHandler
        (currentActivity as? VHEApiModuleHandler)?.apply {
            handler = this
        }
        handler.onVHEApiOpenEditorPath(
            config.getString("path")!!
        )
    }
}