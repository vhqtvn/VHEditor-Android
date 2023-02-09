package vn.vhn.vhscode.visual

import android.content.Context
import android.graphics.Bitmap
import eightbitlab.com.blurview.BlurAlgorithm

class RenderScriptArcylicBlur constructor(context: Context?) :
    BlurAlgorithm {

    public external fun process(bmp: Bitmap)

    /**
     * @param bitmap     bitmap to blur
     * @param blurRadius blur radius (1..25)
     * @return blurred bitmap
     */
    override fun blur(bitmap: Bitmap, blurRadius: Float): Bitmap {
        return bitmap
    }

    override fun destroy() {
    }

    override fun canModifyBitmap(): Boolean {
        return true
    }

    override fun getSupportedBitmapConfig(): Bitmap.Config {
        return Bitmap.Config.ARGB_8888
    }

    override fun scaleFactor(): Float {
        return 8.0f
    }
}