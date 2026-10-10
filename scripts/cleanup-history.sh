#!/bin/bash
#
# Aggressive Git History Cleanup
# Completely removes large files from ALL git history
# 
# WARNING: This rewrites git history. DO NOT use on shared branches without team coordination.
#

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "========================================"
echo "⚠️  WARNING: Aggressive History Cleanup"
echo "========================================"
echo ""
echo "This script will REWRITE git history to completely remove large files."
echo ""
echo "BEFORE RUNNING:"
echo "1. Ensure you have a backup or can recover from mistakes"
echo "2. DO NOT use on shared branches without team coordination"
echo "3. All collaborators will need to re-clone after this"
echo ""
read -p "Do you want to continue? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Cleanup cancelled."
    exit 0
fi

echo ""
echo "Starting aggressive cleanup..."
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Show current size
GIT_SIZE_BEFORE=$(du -sh .git 2>/dev/null | cut -f1)
print_status "Current .git size: $GIT_SIZE_BEFORE"

echo ""
echo "========================================"
echo "Step 1: Remove native libraries from history"
echo "========================================"

# Remove .dylib files from all history
print_status "Removing *.dylib files from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/*.dylib" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

# Remove .so files from all history
print_status "Removing *.so files from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/*.so" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

# Remove .dll files from all history
print_status "Removing *.dll files from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/*.dll" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 2: Remove build directories from history"
echo "========================================"

# Remove target directories
print_status "Removing target/ directories from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet **/target" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

# Remove CMakeFiles directories
print_status "Removing CMakeFiles/ directories from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet **/CMakeFiles" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

# Remove cmake-build directories
print_status "Removing cmake-build-*/ directories from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet **/cmake-build-*" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 3: Remove ONNX Runtime build from history"
echo "========================================"

print_status "Removing onnxruntime/build/ from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet **/onnxruntime/build" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

print_status "Removing onnxruntime*.tgz from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/onnxruntime*.tgz" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

print_status "Removing onnxruntime*.tar.gz from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/onnxruntime*.tar.gz" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 4: Remove llama.cpp from history"
echo "========================================"

print_status "Removing llama-cpp/lib/ from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet **/llama-cpp/lib" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

print_status "Removing vendor/llama-cpp/lib/ from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet vendor/llama-cpp/lib" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 5: Remove generated Java files from history"
echo "========================================"

print_status "Removing llama_h.java files from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/llama_h.java" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

print_status "Removing llama_h_1.java files from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/llama_h_1.java" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 6: Remove benchmark JARs from history"
echo "========================================"

print_status "Removing benchmarks.jar from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet **/benchmarks.jar" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 7: Remove system files from history"
echo "========================================"

print_status "Removing .DS_Store from history..."
git filter-branch --force --index-filter \
    "git rm --cached --ignore-unmatch --quiet .DS_Store **/.DS_Store" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

print_status "Removing .quarkus/ from history..."
git filter-branch --force --index-filter \
    "git rm -r --cached --ignore-unmatch --quiet .quarkus" \
    --prune-empty --tag-name-filter cat -- --all 2>/dev/null || true

echo ""
echo "========================================"
echo "Step 8: Remove backup refs and run GC"
echo "========================================"

print_status "Removing backup refs..."
rm -rf .git/refs/original/

print_status "Expiring reflog..."
git reflog expire --expire=now --all

print_status "Running aggressive garbage collection..."
git gc --prune=now --aggressive

echo ""
echo "========================================"
echo "Step 9: Verify cleanup"
echo "========================================"

GIT_SIZE_AFTER=$(du -sh .git 2>/dev/null | cut -f1)
print_status "New .git size: $GIT_SIZE_AFTER"

# Calculate reduction
echo ""
echo "========================================"
echo "Cleanup Summary"
echo "========================================"
echo "Before: $GIT_SIZE_BEFORE"
echo "After:  $GIT_SIZE_AFTER"
echo ""

print_status "✅ Cleanup complete!"

echo ""
echo "========================================"
echo "⚠️  IMPORTANT NEXT STEPS"
echo "========================================"
echo ""
echo "1. Review the changes:"
echo "   git status"
echo ""
echo "2. Commit the cleanup:"
echo "   git commit -m 'chore: remove large files from git history'"
echo ""
echo "3. Force push to remote (WARNING: Rewrites history):"
echo "   git push --force --all"
echo "   git push --force --tags"
echo ""
echo "4. Notify all collaborators to RE-CLONE the repository:"
echo "   git clone <repository-url>"
echo ""
echo "DO NOT use 'git pull' - must re-clone for clean history!"
echo "========================================"
