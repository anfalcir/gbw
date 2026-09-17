cmake_minimum_required(VERSION 3.22.1)
project(gbw_rubberband LANGUAGES CXX)

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

find_library(log-lib log)
target_link_libraries(gbw_rubberband PRIVATE ${log-lib})
