# Building Raylib Android with CMake
pretty much the same process as using makefile provided by raylib...
i like cmake so i made one full with cmake lol...

### build desktop
```
cmake -S . -B build 
    -DPlatform=Desktop
```

### build android
```
cmake -S . -B build 
    -DPlatform=Android
    -DCMAKE_TOOLCHAIN_FILE=<android-ndk>/cmake/android.toolchain.cmake
```

#### version im using
- build-tools 29.0.3
- android-ndk 25.1.8937393
- platform-android-21
- jdk 18