// https://github.com/android/renderscript-intrinsics-replacement-toolkit/blob/main/renderscript-toolkit/src/main/cpp/JniEntryPoints.cpp
#include <jni.h>
#include <jni.h>
#include <dlfcn.h>
#include <cassert>
#include <android/bitmap.h>

class BitmapGuard {
private:
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info;
    int bytesPerPixel;
    void* bytes;
    bool valid;

public:
    BitmapGuard(JNIEnv* env, jobject jBitmap) : env{env}, bitmap{jBitmap}, bytes{nullptr} {
        valid = false;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
//            ALOGE("AndroidBitmap_getInfo failed");
            return;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            info.format != ANDROID_BITMAP_FORMAT_A_8) {
//            ALOGE("AndroidBitmap in the wrong format");
            return;
        }
        bytesPerPixel = info.stride / info.width;
        if (bytesPerPixel != 1 && bytesPerPixel != 4) {
//            ALOGE("Expected a vector size of 1 or 4. Got %d. Extra padding per line not currently "
//                  "supported",
//                  bytesPerPixel);
            return;
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &bytes) != ANDROID_BITMAP_RESULT_SUCCESS) {
//            ALOGE("AndroidBitmap_lockPixels failed");
            return;
        }
        valid = true;
    }
    ~BitmapGuard() {
        if (valid) {
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }
    uint8_t* get() const {
        assert(valid);
        return reinterpret_cast<uint8_t*>(bytes);
    }
    int width() const { return info.width; }
    int height() const { return info.height; }
    int vectorSize() const { return bytesPerPixel; }
};


extern "C"
JNIEXPORT void JNICALL
Java_vn_vhn_vhscode_visual_RenderScriptArcylicBlur_process(JNIEnv *env, jobject /*thiz*/, jobject jbmp) {
    BitmapGuard bmp{env, jbmp};

}