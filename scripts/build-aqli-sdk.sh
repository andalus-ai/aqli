#!/bin/bash
# scripts/build-aqli-sdk.sh
# Builds the Aqli SDK as a shared library for FFI integration.

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_MODULE="sdk/aqli-sdk-java-local"
DIST_DIR="${PROJECT_ROOT}/dist/native"
LIB_NAME="aqli_sdk_local"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}>>> Building Aqli SDK Shared Library...${NC}"

# 1. Build the SDK JAR
echo -e "${YELLOW}Step 1: Building Gradle project...${NC}"
./gradlew :sdk:aqli-sdk-local:jar

# 2. Prepare Dist Directory
mkdir -p "${DIST_DIR}"

# 3. Build Native Image (Shared Library)
echo -e "${YELLOW}Step 2: Generating classpath and running native-image...${NC}"

# Get the project JAR
JAR_PATH=$(find "${PROJECT_ROOT}/sdk/aqli-sdk-local/build/libs" -name "aqli-sdk-local-*.jar" -not -name "*sources*" | head -n 1)

# Get dependency classpath (robust way via init script)
echo -e "${YELLOW}Extracting dependency classpath...${NC}"
cat << 'EOF' > .cp.gradle
allprojects {
    afterEvaluate { project ->
        if (project.path == ':sdk:aqli-sdk-local') {
            task printCp {
                doLast {
                    println "CLASSPATH_START:" + project.sourceSets.main.runtimeClasspath.asPath
                }
            }
        }
    }
}
EOF

CP=$(./gradlew -I .cp.gradle :sdk:aqli-sdk-local:printCp -q | grep CLASSPATH_START: | sed 's/CLASSPATH_START://')
rm .cp.gradle

if [ -z "$CP" ]; then
    echo "Error: Could not generate classpath"
    exit 1
fi

native-image \
    -cp "${JAR_PATH}:${CP}" \
    --shared \
    -H:Name="${LIB_NAME}" \
    -H:Path="${DIST_DIR}" \
    -H:Class=tech.kayys.aqli.sdk.local.nativeinterop.AqliNativeEntrypoints \
    --no-fallback \
    --enable-preview \
    --add-modules jdk.incubator.vector \
    --enable-native-access=ALL-UNNAMED \
    --initialize-at-build-time \
    --initialize-at-run-time=io.netty.util.internal.PlatformDependent0,io.netty.util.internal.PlatformDependent \
    -H:+UnlockExperimentalVMOptions \
    -H:+InstallExitHandlers \
    -H:+ReportExceptionStackTraces

# 4. Locate and Move Library
echo -e "${YELLOW}Step 3: Organizing artifacts...${NC}"
NATIVE_OUT="${DIST_DIR}/${LIB_NAME}"
# Fix: native-image might not add 'lib' prefix to name
if [ -f "${NATIVE_OUT}.dylib" ]; then
    mv "${NATIVE_OUT}.dylib" "${DIST_DIR}/lib${LIB_NAME}.dylib"
elif [ -f "${NATIVE_OUT}.so" ]; then
    mv "${NATIVE_OUT}.so" "${DIST_DIR}/lib${LIB_NAME}.so"
fi

echo -e "${GREEN}✓ Shared library ready at ${DIST_DIR}/lib${LIB_NAME}${NC}"

# 5. Copy to Flutter Plugin (if path exists)
FLUTTER_PLUGIN_PATH="${PROJECT_ROOT}/../mobile/aqli_engine_flutter"
if [ -d "${FLUTTER_PLUGIN_PATH}" ]; then
    echo -e "${YELLOW}Step 4: Deploying to Flutter plugin...${NC}"
    
    # macOS
    mkdir -p "${FLUTTER_PLUGIN_PATH}/macos/Libraries"
    if [ -f "${DIST_DIR}/lib${LIB_NAME}.dylib" ]; then
        cp "${DIST_DIR}/lib${LIB_NAME}.dylib" "${FLUTTER_PLUGIN_PATH}/macos/Libraries/"
    fi
    
    # Android (Linux .so)
    if [ -f "${DIST_DIR}/lib${LIB_NAME}.so" ]; then
        mkdir -p "${FLUTTER_PLUGIN_PATH}/android/src/main/jniLibs/arm64-v8a"
        cp "${DIST_DIR}/lib${LIB_NAME}.so" "${FLUTTER_PLUGIN_PATH}/android/src/main/jniLibs/arm64-v8a/"
    fi
    
    echo -e "${GREEN}✓ Deployed to ${FLUTTER_PLUGIN_PATH}${NC}"
fi
