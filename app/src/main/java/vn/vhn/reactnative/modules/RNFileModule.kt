package vn.vhn.reactnative.modules

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File

class RNFileModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "VHERNFile"

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun readText(name: String): String {
        return File(name).readText(Charsets.UTF_8)
    }

    fun readTextAsync(name: String, promise: Promise) {
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
    fun exists(name: String): Boolean {
        return File(name).exists()
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isDir(name: String): Boolean {
        return File(name).isDirectory
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isFile(name: String): Boolean {
        return File(name).isFile
    }

    fun scanDirAsync(path: String, promise: Promise) {
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