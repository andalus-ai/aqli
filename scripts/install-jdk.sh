#!/bin/bash
# Aqli SDK - JDK 25 Installation Script
# Usage: curl -sSL https://raw.githubusercontent.com/bhangun/aqli/main/scripts/install-jdk.sh | bash

set -e

echo "☕ Installing JDK 25 via SDKMAN!..."

# Check and install SDKMAN
if [ -z "$SDKMAN_DIR" ]; then
    export SDKMAN_DIR="$HOME/.sdkman"
fi

if [ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    echo "📦 SDKMAN not found, installing..."
    curl -s "https://get.sdkman.io" | bash
fi

# Source SDKMAN
source "$SDKMAN_DIR/bin/sdkman-init.sh"

echo "📦 Installing Java 25-open..."
sdk install java 25-open < /dev/null || echo "Java 25 may already be installed."
sdk default java 25-open < /dev/null || true

echo "✅ JDK 25 installation complete!"
java_version=$(java -version 2>&1 | head -n 1)
echo "✓ Currently active java: $java_version"
