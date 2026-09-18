cmake_minimum_required(VERSION 3.22.1)
project(gbw_native LANGUAGES C CXX)

include(FetchContent)

# Rubber Band Library 4.0.0, official mirror.
# Fixed to the exact v4.0.0 commit for reproducible source selection.
set(GBW_RUBBERBAND_VERSION "4.0.0")
set(GBW_RUBBERBAND_COMMIT "1d95888bec3ae0a17c0c4af791810d5a63f6bc35")

FetchContent_Declare(
    rubberband
    GIT_REPOSITORY https://github.com/breakfastquay/rubberband.git
    GIT_TAG ${GBW_RUBBERBAND_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS FALSE
)

FetchContent_GetProperties(rubberband)
if(NOT rubberband_POPULATED)
    FetchContent_Populate(rubberband)
endif()

add_library(
    gbw_rubberband
    SHARED
    gbw_rubberband_jni.cpp
    ${rubberband_SOURCE_DIR}/single/RubberBandSingle.cpp
)

target_include_directories(
    gbw_rubberband
    PRIVATE
    ${rubberband_SOURCE_DIR}
)

target_compile_features(gbw_rubberband PRIVATE cxx_std_17)
target_compile_options(
    gbw_rubberband
    PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fexceptions
    -frtti
)

target_compile_definitions(
    gbw_rubberband
    PRIVATE
    GBW_RUBBERBAND_VERSION="${GBW_RUBBERBAND_VERSION}"
    GBW_RUBBERBAND_COMMIT="${GBW_RUBBERBAND_COMMIT}"
)

# Demucs.cpp is a C++17 port of HT-Demucs. We intentionally populate the
# exact source revision without adding its desktop-oriented top-level CMake
# project (BLAS/OpenMP/CLI targets are not part of the Android runtime).
set(GBW_DEMUCS_CPP_COMMIT "f1206e9adeea103aef4a636b9e62297cf1f8e34e")
set(GBW_EIGEN_COMMIT "dd8c71e62852b2fe429edb6682ac91fd1c578a26")

FetchContent_Declare(
    demucs_cpp
    GIT_REPOSITORY https://github.com/sevagh/demucs.cpp.git
    GIT_TAG ${GBW_DEMUCS_CPP_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS FALSE
    GIT_SUBMODULES ""
)
FetchContent_GetProperties(demucs_cpp)
if(NOT demucs_cpp_POPULATED)
    FetchContent_Populate(demucs_cpp)
endif()

# demucs.cpp pins Eigen as a submodule. Fetch that same exact revision
# explicitly so the Android build remains reproducible without initializing
# unrelated upstream submodules.
FetchContent_Declare(
    eigen_gbw
    GIT_REPOSITORY https://gitlab.com/libeigen/eigen.git
    GIT_TAG ${GBW_EIGEN_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS FALSE
)
FetchContent_GetProperties(eigen_gbw)
if(NOT eigen_gbw_POPULATED)
    FetchContent_Populate(eigen_gbw)
endif()

# OpenBLAS 0.3.34, pinned to the exact signed release commit. GBW links it
# statically into libgbw_demucs.so so the APK has one native Demucs runtime.
# Eigen's own parallel executor remains disabled: only OpenBLAS may fan out
# inside a single inference, preventing nested/oversubscribed thread pools.
set(GBW_OPENBLAS_COMMIT "e0166008be8e466242aa76b2ff75ce3f0fbf574a")
set(BUILD_STATIC_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(NOFORTRAN ON CACHE BOOL "" FORCE)
set(NO_LAPACK ON CACHE BOOL "" FORCE)
set(NO_LAPACKE ON CACHE BOOL "" FORCE)
set(NO_CBLAS ON CACHE BOOL "" FORCE)
set(DYNAMIC_ARCH OFF CACHE BOOL "" FORCE)
set(TARGET ARMV8 CACHE STRING "" FORCE)
set(USE_THREAD ON CACHE BOOL "" FORCE)
set(NUM_THREADS 4 CACHE STRING "" FORCE)
FetchContent_Declare(
    openblas_gbw
    GIT_REPOSITORY https://github.com/OpenMathLib/OpenBLAS.git
    GIT_TAG ${GBW_OPENBLAS_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS FALSE
)
FetchContent_MakeAvailable(openblas_gbw)
if(NOT TARGET openblas)
    message(FATAL_ERROR "Pinned OpenBLAS target was not created")
endif()

file(GLOB GBW_DEMUCS_SOURCES CONFIGURE_DEPENDS "${demucs_cpp_SOURCE_DIR}/src/*.cpp")
if(NOT GBW_DEMUCS_SOURCES)
    message(FATAL_ERROR "Pinned demucs.cpp source set is empty")
endif()

add_library(
    gbw_demucs
    SHARED
    gbw_demucs_jni.cpp
    ${GBW_DEMUCS_SOURCES}
)

target_include_directories(
    gbw_demucs
    PRIVATE
    ${demucs_cpp_SOURCE_DIR}/src
    ${eigen_gbw_SOURCE_DIR}
)

target_compile_features(gbw_demucs PRIVATE cxx_std_17)
target_compile_options(
    gbw_demucs
    PRIVATE
    -O3
    -DNDEBUG
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fexceptions
    -frtti
)
target_compile_definitions(
    gbw_demucs
    PRIVATE
    EIGEN_DONT_PARALLELIZE=1
    EIGEN_USE_BLAS=1
    GBW_DEMUCS_CPP_COMMIT="${GBW_DEMUCS_CPP_COMMIT}"
    GBW_OPENBLAS_COMMIT="${GBW_OPENBLAS_COMMIT}"
    GBW_EIGEN_COMMIT="${GBW_EIGEN_COMMIT}"
)

find_library(log-lib log)
find_library(math-lib m)
target_link_libraries(gbw_rubberband PRIVATE ${log-lib})
target_link_libraries(gbw_demucs PRIVATE openblas ${log-lib})
