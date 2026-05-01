cmake_minimum_required(VERSION 3.10)

# Set app name - must match core/avatar/JniBridgeJava.java System.loadLibrary("Demo")
set(APP_NAME Demo)

# Set directory paths
set(LIVE2D_PATH ${CMAKE_CURRENT_SOURCE_DIR}/live2d)
set(STB_PATH ${CMAKE_CURRENT_SOURCE_DIR}/stb)

# Specify C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Add Live2D Core as shared library (we use .so files)
add_library(Live2DCubismCore SHARED IMPORTED)
set_target_properties(Live2DCubismCore
    PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libLive2DCubismCore.so
)

# Collect Framework source files
# Exclude Vulkan, Metal, D3D9, D3D11 renderers - we only need OpenGL ES for Android
file(GLOB_RECURSE FRAMEWORK_SOURCES
    ${LIVE2D_PATH}/Utils/*.cpp
    ${LIVE2D_PATH}/Type/*.cpp
    ${LIVE2D_PATH}/Math/*.cpp
    ${LIVE2D_PATH}/Id/*.cpp
    ${LIVE2D_PATH}/Model/*.cpp
    ${LIVE2D_PATH}/Motion/*.cpp
    ${LIVE2D_PATH}/Physics/*.cpp
    ${LIVE2D_PATH}/Effect/*.cpp
    ${LIVE2D_PATH}/Rendering/OpenGLES2/*.cpp
    ${LIVE2D_PATH}/Rendering/CMakeLists.txt
    ${LIVE2D_PATH}/*.cpp
)

# Remove Vulkan, Metal, D3D files
list(FILTER FRAMEWORK_SOURCES EXCLUDE REGEX ".*/Vulkan/.*")
list(FILTER FRAMEWORK_SOURCES EXCLUDE REGEX ".*/Metal/.*")
list(FILTER FRAMEWORK_SOURCES EXCLUDE REGEX ".*/D3D9/.*")
list(FILTER FRAMEWORK_SOURCES EXCLUDE REGEX ".*/D3D11/.*")
list(FILTER FRAMEWORK_SOURCES EXCLUDE REGEX ".*/CMakeLists\\.txt$")

# Collect Framework header files
file(GLOB_RECURSE FRAMEWORK_HEADERS
    ${LIVE2D_PATH}/*.hpp
)

# Collect App source files
file(GLOB APP_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/*.cpp
)

file(GLOB APP_HEADERS
    ${CMAKE_CURRENT_SOURCE_DIR}/*.hpp
)

# Create Framework library
add_library(Framework STATIC ${FRAMEWORK_SOURCES})
target_include_directories(Framework PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIVE2D_PATH}
    ${STB_PATH}
)
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

# Create app shared library
add_library(${APP_NAME} SHARED ${APP_SOURCES})

# Link libraries
target_link_libraries(${APP_NAME}
    Framework
    Live2DCubismCore
    GLESv2
    EGL
    log
    android
)

# Include directories
target_include_directories(${APP_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIVE2D_PATH}
    ${STB_PATH}
)

# Compile definitions
target_compile_definitions(${APP_NAME} PRIVATE
    CSM_TARGET_ANDROID_ES2
)

# Enable verbose debug logging only for Debug builds
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(${APP_NAME} PRIVATE APP_DEBUG_ENABLE)
endif()
