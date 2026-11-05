cmake_minimum_required(VERSION 3.10)
project(rafiei_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# whisper.cpp (MIT, see whisper/LICENSE.whisper.cpp): CPU backend only, static ggml inside one shared library
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
add_subdirectory(whisper/ggml ggml-build)

add_library(rafiei_whisper SHARED
    whisper/src/whisper.cpp
    whisper/src/aneforge/whisper-aneforge.cpp
    whisper_jni.cpp)

target_compile_definitions(rafiei_whisper PRIVATE GGML_USE_CPU WHISPER_VERSION="1.9.5")
target_include_directories(rafiei_whisper PRIVATE whisper/include whisper/src whisper/ggml/include whisper/ggml/src)

if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(rafiei_whisper ${LOG_LIB} ggml)
    if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
        target_compile_options(rafiei_whisper PRIVATE -O3 -ffunction-sections -fdata-sections)
        target_link_options(rafiei_whisper PRIVATE -Wl,--gc-sections)
    endif ()
else ()
    target_link_libraries(rafiei_whisper ggml)
endif ()
