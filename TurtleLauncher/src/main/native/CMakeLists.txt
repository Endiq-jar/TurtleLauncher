cmake_minimum_required(VERSION 3.22.1)
# SFPEW's build uses CMAKE_PROJECT_NAME for its library target.
project(SimpleFPEWrapper LANGUAGES C CXX)
include(FetchContent)
# Pin the renderer and its submodules, never a floating release/nightly.
FetchContent_Declare(sfpew
    GIT_REPOSITORY https://github.com/MobileGL-Dev/SimpleFPEWrapper.git
    GIT_TAG d5d1580ce938acea3e7e4d4361fc14564575cb13
    GIT_SUBMODULES 3rdparty/glm 3rdparty/glslang 3rdparty/SPIRV-Cross 3rdparty/googletest
)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(SFPEW_LTO OFF CACHE BOOL "" FORCE)
FetchContent_GetProperties(sfpew)
if(NOT sfpew_POPULATED)
    FetchContent_Populate(sfpew)
endif()
# Upstream builds as a standalone project. Keep its include paths scoped to its source tree.
file(READ "${sfpew_SOURCE_DIR}/CMakeLists.txt" sfpew_build)
string(REPLACE "\${CMAKE_SOURCE_DIR}" "\${CMAKE_CURRENT_SOURCE_DIR}" sfpew_build "${sfpew_build}")
file(WRITE "${sfpew_SOURCE_DIR}/CMakeLists.txt" "${sfpew_build}")
add_subdirectory("${sfpew_SOURCE_DIR}" "${sfpew_BINARY_DIR}" EXCLUDE_FROM_ALL)
add_library(turtlefiledialogs SHARED file_dialog_bridge.c)
target_link_libraries(turtlefiledialogs dl)
add_library(lwjgl_nfd SHARED nfd_loader.c)
target_link_libraries(lwjgl_nfd turtlefiledialogs)
add_library(lwjgl_nfd_portal SHARED nfd_loader.c)
target_link_libraries(lwjgl_nfd_portal turtlefiledialogs)

# Only the Discord SDK is imported; unrelated Java dependencies' Prefab modules
# must not constrain this native build's STL choice.
if(NOT TURTLE_DISCORD_SDK_DIR)
    message(FATAL_ERROR "TURTLE_DISCORD_SDK_DIR must point at prepareDiscordSdk's output")
endif()
set(discord_module "${TURTLE_DISCORD_SDK_DIR}/prefab/modules/discord_partner_sdk")
add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION "${discord_module}/libs/android.${ANDROID_ABI}/libdiscord_partner_sdk.so"
    INTERFACE_INCLUDE_DIRECTORIES "${discord_module}/include")
add_library(turtlediscordrpc SHARED discord/discord_rpc_bridge.cpp)
target_link_libraries(turtlediscordrpc discord_partner_sdk log)
set_target_properties(turtlediscordrpc PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED YES)
