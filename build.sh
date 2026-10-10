#!/bin/bash
# Aqli - Project Build Script
# This script handles cleaning, building, and installing Aqli modules.
# Usage: ./build.sh [options]

set -e

# --- Visuals ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}--------------------------------------------------${NC}"
echo -e "${GREEN} Aqli Build Utility ${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"

# --- Default Options ---
CLEAN=false
SKIP_TESTS=true
NATIVE=false
INSTALL_LIBS=false
OFFLINE=""
THREADS="1C"

usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -c, --clean         Run 'mvn clean' before building"
    echo "  -t, --tests         Run tests (default: skipped)"
    echo "  -n, --native        Build native images (Quarkus native profile)"
    echo "  -l, --libs          Install native libraries using Makefile.native"
    echo "  -o, --offline       Run Maven in offline mode"
    echo "  -j, --threads <n>   Number of threads for Maven (default: 1C)"
    echo "  -h, --help          Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -c               # Clean and build everything (skip tests)"
    echo "  $0 -n               # Build native executables"
    echo "  $0 -c -l            # Clean build and install native libs"
    exit 0
}

# --- Parse Arguments ---
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -c|--clean) CLEAN=true ;;
        -t|--tests) SKIP_TESTS=false ;;
        -n|--native) NATIVE=true ;;
        -l|--libs) INSTALL_LIBS=true ;;
        -o|--offline) OFFLINE="-o" ;;
        -j|--threads) THREADS="$2"; shift ;;
        -h|--help) usage ;;
        *) echo -e "${RED}Unknown parameter: $1${NC}"; usage ;;
    esac
    shift
done

# --- 1. Java 25 Verification ---
echo -e "${BLUE}🔍 Checking Java environment...${NC}"
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "25'; then
    echo -e "${YELLOW}⚠ Java 25 is required but not found or not active.${NC}"
    
    # Try to load SDKMAN
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"
        if sdk current java | grep -q '25'; then
            echo -e "${GREEN}✓ Loaded Java 25 via SDKMAN${NC}"
        else
            echo -e "${RED}❌ Java 25 is not available in SDKMAN. Please run ./install.sh first.${NC}"
            exit 1
        fi
    else
        echo -e "${RED}❌ Please ensure Java 25 is installed and active.${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}✓ Found $(java -version 2>&1 | head -n 1)${NC}"
fi

# --- 2. Build Error Codes (if script exists) ---
if [ -f "./scripts/generate-error-codes.sh" ]; then
    echo -e "${BLUE}>>> Generating error codes...${NC}"
    ./scripts/generate-error-codes.sh
fi

# --- 3. Maven Build ---
MVN_CMD="mvn"
MVN_ARGS="-T $THREADS $OFFLINE"

if [ "$CLEAN" = true ]; then
    MVN_ARGS="$MVN_ARGS clean"
fi

MVN_ARGS="$MVN_ARGS install"

if [ "$SKIP_TESTS" = true ]; then
    MVN_ARGS="$MVN_ARGS -DskipTests"
fi

if [ "$NATIVE" = true ]; then
    echo -e "${YELLOW}ℹ Building native images (this may take a while)...${NC}"
    MVN_ARGS="$MVN_ARGS -Pnative -Dquarkus.native.container-build=false"
fi

echo -e "${BLUE}>>> Running build: $MVN_CMD $MVN_ARGS${NC}"
$MVN_CMD $MVN_ARGS

# --- 4. Install Native Libraries ---
if [ "$INSTALL_LIBS" = true ]; then
    if [ -f "Makefile.native" ]; then
        echo -e "${BLUE}>>> Installing native libraries...${NC}"
        make -f Makefile.native install-native-libs
    else
        echo -e "${YELLOW}⚠ Makefile.native not found, skipping library installation.${NC}"
    fi
fi

echo ""
echo -e "${GREEN}✓ Build process completed successfully!${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"
