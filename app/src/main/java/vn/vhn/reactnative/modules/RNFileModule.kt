package vn.vhn.reactnative.modules

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import vn.vhn.vhscode.CodeServerService
import java.io.File

class RNFileModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "VHERNFile"

    companion object {
        fun resolveFile(name: String): String {
            var name = name
            if (name.startsWith("~/")) name = CodeServerService.HOME_PATH + "/" + name.substring(2)
            return name
        }
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun readText(name: String): String {
        var name = resolveFile(name)
        return File(name).readText(Charsets.UTF_8)
    }

    fun readTextAsync(name: String, promise: Promise) {
        var name = resolveFile(name)
        lateinit var content: String
        try {
            content = File(name).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            promise.reject(e)
            return
        }
        promise.resolve(content)
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun writeText(name: String, content: String) {
        var name = resolveFile(name)
        File(name).writeText(content, Charsets.UTF_8)
    }

    fun writeTextAsync(name: String, content: String, promise: Promise) {
        var name = resolveFile(name)
        try {
            File(name).writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            promise.reject(e)
            return
        }
        promise.resolve(null)
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun exists(name: String): Boolean {
        var name = resolveFile(name)
        return File(name).exists()
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isDir(name: String): Boolean {
        var name = resolveFile(name)
        return File(name).isDirectory
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isFile(name: String): Boolean {
        var name = resolveFile(name)
        return File(name).isFile
    }

    fun scanDirAsync(path: String, promise: Promise) {
        var name = resolveFile(name)
        lateinit var result: Array<String>
        try {
            result = File(path).list()
        } catch (e: Exception) {
            promise.reject(e)
            return
        }
        promise.resolve(result)
    }
}