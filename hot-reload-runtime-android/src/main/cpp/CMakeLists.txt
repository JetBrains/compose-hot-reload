# Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

cmake_minimum_required(VERSION 3.22.1)
project(composeHotReloadAgent)

# libcomposehotreloadagent.so -- the JVMTI agent used to redefine class bodies on ART.
add_library(composehotreloadagent SHARED agent.cpp)

# jvmti.h comes from the JDK, NDK does not supply it.
# Copy that header into its own directory for the build.
if (NOT DEFINED JDK_INCLUDE_DIR OR NOT EXISTS "${JDK_INCLUDE_DIR}/jvmti.h")
    message(FATAL_ERROR "jvmti.h not found in JDK_INCLUDE_DIR='${JDK_INCLUDE_DIR}' (a full JDK is required)")
endif ()
file(COPY "${JDK_INCLUDE_DIR}/jvmti.h" DESTINATION "${CMAKE_CURRENT_BINARY_DIR}/jvmti")
target_include_directories(composehotreloadagent PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/jvmti")

find_library(log-lib log)
target_link_libraries(composehotreloadagent ${log-lib})
