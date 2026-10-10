#!/bin/bash
#
# Git Repository Cleanup Script for Aqli Inference
# Removes large files from git tracking and optimizes repository
# Uses xargs for parallel processing (up to 10 threads)
#

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "========================================"
echo "Aqli Git Repository Cleanup"
echo "========================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're on main branch
CURRENT_BRANCH=$(git branch --show-current)
print_status "Current branch: $CURRENT_BRANCH"

# Show current .git size
GIT_SIZE_BEFORE=$(du -sh .git 2>/dev/null | cut -f1)
print_status "Current .git size: $GIT_SIZE_BEFORE"

echo ""
echo "========================================"
echo "Step 1: Remove large files from index"
echo "========================================"

# Files to remove from git tracking (but keep locally)
LARGE_FILES=(
    "plugins/runner/onnx/aqli-runner-onnx/src/main/cpp/onnxruntime/build"
    "plugins/runner/onnx/aqli-runner-onnx/src/main/cpp/onnxruntime/build/onnxruntime-osx-arm64-1.19.2"
    "plugins/runner/onnx/aqli-runner-onnx/src/main/cpp/onnxruntime/build/onnxruntime-osx-arm64-1.19.2.tgz"
    "extension/format/gguf/vendor/llama-cpp/lib"
    "vendor/llama-cpp/lib"
    "**/target"
    "**/CMakeFiles"
    "**/cmake-build-*"
    "**/benchmarks.jar"
    "**/*.dylib"
    "**/*.so"
    "**/*.dll"
    "**/*.jnilib"
    "**/llama_h.java"
    "**/llama_h_1.java"
    "**/CMakeConfigureLog.yaml"
    "**/compiler_depend.make"
    ".DS_Store"
    ".quarkus"
)

print_status "Removing large files from git tracking..."
for pattern in "${LARGE_FILES[@]}"; do
    git rm -r --cached --ignore-unmatch "$pattern" 2>/dev/null || true
done

echo ""
echo "========================================"
echo "Step 2: Clean .DS_Store files (10 threads)"
echo "========================================"

# Find and remove .DS_Store from git in parallel using xargs
find . -name ".DS_Store" -type f -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 3: Clean build artifacts (10 threads)"
echo "========================================"

# Clean target directories in parallel
find . -type d -name "target" -print0 | xargs -0 -P 10 -I {} git rm -r --cached --ignore-unmatch "{}" 2>/dev/null || true

# Clean CMakeFiles directories in parallel
find . -type d -name "CMakeFiles" -print0 | xargs -0 -P 10 -I {} git rm -r --cached --ignore-unmatch "{}" 2>/dev/null || true

# Clean cmake-build directories in parallel
find . -type d -name "cmake-build-*" -print0 | xargs -0 -P 10 -I {} git rm -r --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 4: Remove native libraries (10 threads)"
echo "========================================"

# Remove native library files in parallel
find . -type f \( -name "*.dylib" -o -name "*.so" -o -name "*.dll" -o -name "*.jnilib" \) -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 5: Remove generated Java files (10 threads)"
echo "========================================"

# Remove large generated Java files in parallel
find . -type f \( -name "llama_h.java" -o -name "llama_h_1.java" -o -name "onnxruntime_c_api.java" \) -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 6: Remove benchmark JARs (10 threads)"
echo "========================================"

# Remove benchmark JARs in parallel
find . -type f -name "benchmarks.jar" -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 7: Remove CMake build logs (10 threads)"
echo "========================================"

# Remove CMake build logs in parallel
find . -type f \( -name "CMakeConfigureLog.yaml" -o -name "compiler_depend.make" \) -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 8: Remove ONNX runtime archives (10 threads)"
echo "========================================"

# Remove ONNX runtime archives
find . -type f \( -name "onnxruntime*.tgz" -o -name "onnxruntime*.tar.gz" \) -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 9: Remove llama.cpp libraries (10 threads)"
echo "========================================"

# Remove llama.cpp libraries
find . -type f \( -name "libllama*.a" -o -name "libggml*.a" \) -print0 | xargs -0 -P 10 -I {} git rm --cached --ignore-unmatch "{}" 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 10: Garbage collection"
echo "========================================"

print_status "Running git garbage collection..."
git gc --aggressive --prune=now

echo ""
echo "========================================"
echo "Step 11: Verify cleanup"
echo "========================================"

GIT_SIZE_AFTER=$(du -sh .git 2>/dev/null | cut -f1)
print_status "New .git size: $GIT_SIZE_AFTER"

# Count files to be committed
FILES_TO_COMMIT=$(git status --porcelain | wc -l | tr -d ' ')
print_status "Files to commit: $FILES_TO_COMMIT"

echo ""
print_status "Cleanup complete!"
echo ""
echo "========================================"
echo "Next Steps:"
echo "========================================"
echo "1. Review changes: git status"
echo "2. Commit cleanup: git commit -m 'chore: remove large files from git tracking'"
echo "3. Force push (CAUTION): git push --force"
echo ""
echo "WARNING: Force pushing will rewrite history. Ensure all collaborators are aware."
echo "========================================"
