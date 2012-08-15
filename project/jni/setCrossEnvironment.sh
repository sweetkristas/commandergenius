#!/bin/sh

IFS='
'

MYARCH=linux-x86
if uname -s | grep -i "linux" > /dev/null ; then
	MYARCH=linux-x86
fi
if uname -s | grep -i "darwin" > /dev/null ; then
	MYARCH=darwin-x86
fi
if uname -s | grep -i "windows" > /dev/null ; then
	MYARCH=windows-x86
fi

NDK=`which ndk-build`
NDK=`dirname $NDK`
NDK=`readlink -f $NDK`

#echo NDK $NDK
GCCPREFIX=arm-linux-androideabi
GCCVER=4.4.3
PLATFORMVER=android-8
LOCAL_PATH=`dirname $0`
LOCAL_PATH=`cd $LOCAL_PATH && pwd`
#echo LOCAL_PATH $LOCAL_PATH

CFLAGS="\
-fexceptions -frtti \
-fpic -ffunction-sections -funwind-tables -D__ARM_ARCH_5__ -D__ARM_ARCH_5T__ -D__ARM_ARCH_5E__ -D__ARM_ARCH_5TE__  -Wno-psabi \
-march=armv5te -mtune=xscale -msoft-float -mthumb -Os -fomit-frame-pointer -fno-strict-aliasing -finline-limit=64 \
-isystem$NDK/platforms/$PLATFORMVER/arch-arm/usr/include -Wa,--noexecstack \
-DANDROID \
-DNDEBUG -O2 -g \
-isystem$NDK/sources/cxx-stl/gnu-libstdc++/include \
-isystem$NDK/sources/cxx-stl/gnu-libstdc++/libs/armeabi/include \
$MISSING_INCLUDE $CFLAGS"

SHARED="-shared -Wl,-soname,libapplication.so"
if [ -n "$BUILD_EXECUTABLE" ]; then
	SHARED=
fi
if [ -n "$NO_SHARED_LIBS" ]; then
	APP_SHARED_LIBS=
fi

LDFLAGS="\
-fexceptions -frtti $SHARED \
--sysroot=$NDK/platforms/$PLATFORMVER/arch-arm \
-L$NDK/sources/cxx-stl/gnu-libstdc++/libs/armeabi \
-L$NDK/platforms/$PLATFORMVER/arch-arm/usr/lib \
-lc \
-lm \
-ldl \
-llog \
-lz \
-lgnustl_static \
-lsupc++ \
-Wl,--no-undefined -Wl,-z,noexecstack \
-Wl,-rpath=/system/lib \
-Wl,-rpath-link=$NDK/platforms/$PLATFORMVER/arch-arm/usr/lib \
$MISSING_LIB $LDFLAGS"

#echo env CFLAGS=\""$CFLAGS"\" LDFLAGS=\""$LDFLAGS"\" "$@"

env PATH=$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin:$LOCAL_PATH:$PATH \
CFLAGS="$CFLAGS" \
CXXFLAGS="$CFLAGS" \
LDFLAGS="$LDFLAGS" \
CC="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-gcc" \
CXX="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-g++" \
RANLIB="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-ranlib" \
LD="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-g++" \
AR="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-ar" \
CPP="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-cpp $CFLAGS" \
NM="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-nm" \
AS="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-as" \
STRIP="$NDK/toolchains/$GCCPREFIX-$GCCVER/prebuilt/$MYARCH/bin/$GCCPREFIX-strip" \
"$@"
