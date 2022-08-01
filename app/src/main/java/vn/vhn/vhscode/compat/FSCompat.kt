package vn.vhn.vhscode.compat

import android.os.Build
import java.io.File
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isSymbolicLink

class FSCompat {
    companion object {
        fun toPath(file: File): Path {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                file.toPath()
            } else {
                return kotlin.io.path.Path(file.path)
            }
        }

        fun isSymbolicLink(file: File): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                toPath(file).isSymbolicLink()
            } else {
                return file.canonicalPath.equals(file.absolutePath)
            }
        }
    }
}