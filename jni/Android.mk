LOCAL_PATH := $(call my-dir)
#LOCAL_CFLAGS := -Wall -Werror -O0 -ggdb3
LOCAL_CFLAGS := -Wall -Werror -O3

include $(CLEAR_VARS)
LOCAL_MODULE            := posixfile
LOCAL_SRC_FILES         := posixfile.c
LOCAL_C_INCLUDES        := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_LDLIBS            := -llog
include $(BUILD_SHARED_LIBRARY)

