APP_PROJECT_PATH := $(call my-dir)/..

APP_STL := gnustl_static
APP_CFLAGS := -O2 -DNDEBUG -g # arm-linux-androideabi-4.4.3 crashes in -O0 mode on SDL sources
APP_PLATFORM := android-9

include jni/Settings.mk

