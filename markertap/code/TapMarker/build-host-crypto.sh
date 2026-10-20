#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TASK_SPAKE_SOURCE=third-party/spake2-java-2.2.1/android/src/main/cpp
mkdir -p build/test-native
cc -O2 -fPIC -I"$TASK_SPAKE_SOURCE/spake2-c/include" -c "$TASK_SPAKE_SOURCE/spake2-c/sha512.c" -o build/test-native/sha512.o
cc -O2 -fPIC -I"$TASK_SPAKE_SOURCE/spake2-c/include" -c "$TASK_SPAKE_SOURCE/spake2-c/spake2.c" -o build/test-native/spake2.o
c++ -std=c++17 -O2 -fPIC -shared -Itoolchain/host-jni -I"$TASK_SPAKE_SOURCE/spake2-c/include" "$TASK_SPAKE_SOURCE/spake2_jni.cpp" build/test-native/sha512.o build/test-native/spake2.o -o build/test-native/libspake2.so
