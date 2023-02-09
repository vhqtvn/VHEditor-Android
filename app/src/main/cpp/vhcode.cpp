#include <jni.h>
#include <jni.h>
#include <dlfcn.h>
#include <cassert>

#if defined __i686__
static const char* arch = "x86";
#elif defined __x86_64__
static const char* arch = "x86_64";
#elif defined __aarch64__
static const char* arch = "arm64";
#elif defined __arm__
static const char* arch = "arm";
#else
# error Unsupported arch
#endif

extern "C"
JNIEXPORT jstring
JNICALL
Java_vn_vhn_vhscode_CodeServerService_00024Companion_getArch(JNIEnv *env,
                                                             __attribute__((__unused__)) jobject This) {
    return env->NewStringUTF(arch);
}

extern "C"
JNIEXPORT void
JNICALL
Java_vn_vhn_vhscode_CodeServerService_00024Companion_unloadLibrary(JNIEnv *env,
                                                                   __attribute__((__unused__)) jobject This,
                                                                   jstring library) {
    const char *libraryC = env->GetStringUTFChars(library, 0);

    //dlclose(libraryC); //TODO: how to unload?

    env->ReleaseStringUTFChars(library, libraryC);

}
