#include <jni.h>
#include <jni.h>

extern jbyte vsc_blob[];
extern jbyte archstr[];
extern int vsc_blob_size;

JNIEXPORT jbyteArray JNICALL
Java_vn_vhn_vhscode_CodeServerService_00024Companion_getZip(JNIEnv *env,
                                                            __attribute__((__unused__)) jobject This) {
    jbyteArray ret = (*env)->NewByteArray(env, vsc_blob_size);
    (*env)->SetByteArrayRegion(env, ret, 0, vsc_blob_size, vsc_blob);
    return ret;
}
