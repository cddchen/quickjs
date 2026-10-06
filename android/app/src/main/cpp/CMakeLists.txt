cmake_minimum_required(VERSION 3.22.1)
project(quickjs_app)

add_library(quickjs_bridge SHARED
    quickjs_wrapper.cpp
    quickjs/cutils.c
    quickjs/dtoa.c
    quickjs/libregexp.c
    quickjs/libunicode.c
    quickjs/quickjs.c
)

target_compile_options(quickjs_bridge PRIVATE -O3 -fwrapv)
target_link_libraries(quickjs_bridge android log m)
target_compile_definitions(quickjs_bridge PRIVATE _GNU_SOURCE CONFIG_VERSION="2024-01-13")