LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libvsc-bootstrap
LOCAL_SRC_FILES := vsc-bootstrap-zip.S vsc-bootstrap.c
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := libvhcode

LOCAL_SRC_FILES := vhcode.cpp
LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blend.cpp
LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blur.cpp

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_CPPFLAGS := $(LOCAL_CPPFLAGS) -DARCH_ARM_USE_INTRINSICS -DARCH_ARM_HAVE_VFP
    LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blend_neon.S
    LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blur_neon.S
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
	LOCAL_CPPFLAGS := $(LOCAL_CPPFLAGS) -DARCH_ARM_USE_INTRINSICS -DARCH_ARM64_USE_INTRINSICS -DARCH_ARM64_HAVE_NEON
    LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blend_advsimd.S
    LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Blur_advsimd.S
endif
# TODO add also for x86

LOCAL_SRC_FILES := $(LOCAL_SRC_FILES) bitmap/Utils.cpp bitmap/render.cpp bitmap/TaskProcessor.cpp bitmap/RenderScriptToolkit.cpp

LOCAL_STATIC_LIBRARIES:= cpufeatures
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -ljnigraphics

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)
