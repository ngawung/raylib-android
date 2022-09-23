cmake_minimum_required(VERSION 3.18)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(DOWNLOAD_EXTRACT_TIMESTAMP ON)

set(PROJECT_NAME raydroid)
set(CMAKE_BUILD_TYPE "Debug" CACHE BOOL "")

if (${PLATFORM} STREQUAL "Android")
    set(ANDROID_OUTPUT "${CMAKE_BINARY_DIR}/apk")
    set(APP_LABEL "Raylib Android")
    set(APP_COMPANY_NAME "melody")
    set(APP_PRODUCT_NAME ${PROJECT_NAME})
    set(APP_PACKAGE "com.${APP_COMPANY_NAME}.${PROJECT_NAME}")
    set(APP_VERSION_CODE "1")
    set(APP_VERSION_NAME "1.0")
    set(APP_KEYSTORE_PASS "password")

    set(JAVA_HOME "/usr/lib/jvm/java-8-openjdk")
    set(ANDROID_PLATFORM "21")
    set(ANDROID_ABI "arm64-v8a")
    set(ANDROID_NDK "/opt/android-sdk/ndk/25.1.8937393")
    set(ANDROID_PLATFORM_PATH "/opt/android-sdk/platforms/android-${ANDROID_PLATFORM}")

    # find_file(CMAKE_TOOLCHAIN_FILE android.toolchain.cmake HINTS ${ANDROID_NDK}/build/cmake)
    # set(CMAKE_TOOLCHAIN_FILE "${ANDROID_NDK}/build/cmake/android.toolchain.cmake")

    if(CMAKE_BUILD_TYPE MATCHES Release)
      set(ANDROID_APK_DEBUGGABLE "false")
    else()
      set(ANDROID_APK_DEBUGGABLE "true")
    endif()
endif()

message(STATUS "Downloading raylib library")
include(FetchContent)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_GAMES    OFF CACHE BOOL "" FORCE)
FetchContent_Declare(raylib URL https://github.com/raysan5/raylib/archive/refs/tags/4.2.0.zip)
FetchContent_MakeAvailable(raylib)

# yes... im using glob... dont judge me....
file(GLOB PROJECT_SOURCES CONFIGURE_DEPENDS
    "src/*.cpp"
    "src/**/*.cpp")

set(PROJECT_INCLUDE "src")
set(PROJECT_LIB raylib)

project(${PROJECT_NAME} VERSION 1.0)

if (${PLATFORM} STREQUAL "Android")
    set(${CMAKE_C_FLAGS}, "${CMAKE_C_FLAGS}")
    add_library(native_app_glue STATIC "${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -u ANativeActivity_onCreate")

    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -Wall -Werror")
    add_library(${PROJECT_NAME} SHARED ${PROJECT_SOURCES})
    set_property(TARGET ${PROJECT_NAME} PROPERTY LIBRARY_OUTPUT_DIRECTORY ${ANDROID_OUTPUT}/lib/${ANDROID_ABI})

    set(PROJECT_INCLUDE ${PROJECT_INCLUDE} "${ANDROID_NDK}/sources/android/native_app_glue")
    set(PROJECT_LIB ${PROJECT_LIB} native_app_glue log android EGL GLESv2 OpenSLES)
else()
    add_executable(${PROJECT_NAME} ${PROJECT_SOURCES})
endif()

target_include_directories(${PROJECT_NAME} PRIVATE ${PROJECT_INCLUDE})
target_link_libraries(${PROJECT_NAME} PUBLIC ${PROJECT_LIB})

target_compile_definitions(${PROJECT_NAME} PUBLIC RESOURCE_PATH="${CMAKE_CURRENT_SOURCE_DIR}/resource/" "ANDROID")
#target_compile_definitions(${PROJECT_NAME} PUBLIC RESOURCE_PATH="./resource/")

if (${PLATFORM} STREQUAL "Android")
    # create keystore
    if (NOT EXISTS ${CMAKE_SOURCE_DIR}/key.keystore)
        execute_process(COMMAND keytool -genkeypair -validity 1000 -dname "CN=${APP_COMPANY_NAME},O=Android,C=ES" -keystore ${CMAKE_SOURCE_DIR}/key.keystore -storepass ${APP_KEYSTORE_PASS} -keypass ${APP_KEYSTORE_PASS} -alias ${PROJECT_NAME}Key -keyalg RSA)
    endif()

    # generate .xml
    file(GLOB_RECURSE RES_FILES RELATIVE "${CMAKE_SOURCE_DIR}/android" "android/res/**.xml" "android/*.xml")
    foreach(FILE IN LISTS RES_FILES)
        configure_file(${CMAKE_SOURCE_DIR}/android/${FILE} ${ANDROID_OUTPUT}/config_bak/${FILE})
    endforeach()

    # configure NativeLoader.java
    configure_file(
        "${CMAKE_SOURCE_DIR}/android/NativeLoader.java"
        "${ANDROID_OUTPUT}/src/com/${APP_COMPANY_NAME}/${APP_PRODUCT_NAME}/NativeLoader.java")

    add_custom_command(TARGET ${PROJECT_NAME} PRE_BUILD
        # prepare directory
        COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_OUTPUT}

        COMMAND ${CMAKE_COMMAND} -E remove_directory ${ANDROID_OUTPUT}/bin
        COMMAND ${CMAKE_COMMAND} -E remove_directory ${ANDROID_OUTPUT}/lib
        COMMAND ${CMAKE_COMMAND} -E remove_directory ${ANDROID_OUTPUT}/res
        COMMAND ${CMAKE_COMMAND} -E remove_directory ${ANDROID_OUTPUT}/assets

        COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_OUTPUT}/bin
        COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_OUTPUT}/lib/${ANDROID_ABI}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_OUTPUT}/res
        COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_OUTPUT}/obj)

    add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
        # copy resource
        COMMAND ${CMAKE_COMMAND} -E copy_directory "${CMAKE_SOURCE_DIR}/android/res" "res"
        COMMAND ${CMAKE_COMMAND} -E copy_directory "${CMAKE_SOURCE_DIR}/resource" "assets/resource"

        # copy config_bak
        COMMAND ${CMAKE_COMMAND} -E copy_directory "config_bak" "."
        COMMAND ${CMAKE_COMMAND} -E remove_directory "config_bak"

        # generate R.java
        COMMAND aapt ARGS package -f -m
            -S "res"
            -J "src"
            -M "AndroidManifest.xml"
            -I "${ANDROID_PLATFORM_PATH}/android.jar"

        # compile java to class
        COMMAND javac ARGS -verbose -source 1.7 -target 1.7
            -d "${ANDROID_OUTPUT}/obj"
            -bootclasspath "${JAVA_HOME}/jre/lib/rt.jar"
            -classpath "${ANDROID_PLATFORM_PATH}/android.jar"
            -sourcepath "${ANDROID_OUTPUT}/src"
                        "${ANDROID_OUTPUT}/src/com/${APP_COMPANY_NAME}/${APP_PRODUCT_NAME}/R.java"
                        "${ANDROID_OUTPUT}/src/com/${APP_COMPANY_NAME}/${APP_PRODUCT_NAME}/NativeLoader.java"

        # compile .class to .dex
        COMMAND dx ARGS --verbose --dex --output="${ANDROID_OUTPUT}/bin/classes.dex" "${ANDROID_OUTPUT}/obj"

        # pack apk
        COMMAND aapt ARGS package -f
            -M "AndroidManifest.xml"
            -S "res"
            -A "${ANDROID_OUTPUT}/assets"
            -I "${ANDROID_PLATFORM_PATH}/android.jar"
            -F ${ANDROID_OUTPUT}/bin/${PROJECT_NAME}.unsigned.apk
            ${ANDROID_OUTPUT}/bin

        COMMAND aapt ARGS add ${ANDROID_OUTPUT}/bin/${PROJECT_NAME}.unsigned.apk "lib/${ANDROID_ABI}/$<TARGET_FILE_NAME:${PROJECT_NAME}>"

        # sign apk
        COMMAND jarsigner ARGS
            -keystore ${CMAKE_SOURCE_DIR}/key.keystore
            -storepass ${APP_KEYSTORE_PASS}
            -keypass ${APP_KEYSTORE_PASS}
            -signedjar ${ANDROID_OUTPUT}/bin/${PROJECT_NAME}.signed.apk
            ${ANDROID_OUTPUT}/bin/${PROJECT_NAME}.unsigned.apk ${PROJECT_NAME}Key

        COMMAND zipalign ARGS -f 4
            ${ANDROID_OUTPUT}/bin/${PROJECT_NAME}.signed.apk
            ${PROJECT_NAME}.apk

        WORKING_DIRECTORY ${ANDROID_OUTPUT})
endif()