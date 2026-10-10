#!/bin/bash
# Aqli CLI Installation Script
# Usage: curl -sSL https://raw.githubusercontent.com/bhangun/aqli/main/scripts/install-cli.sh | bash

set -e

echo "🚀 Installing Aqli CLI..."

if command -v brew &> /dev/null; then
    echo "📦 Using Homebrew to install aqli-cli..."
    brew tap bhangun/aqli
    brew install aqli
else
    echo "📦 Using official Aqli release installer..."
    curl -fsSL https://raw.githubusercontent.com/bhangun/aqli/main/scripts/install.sh | bash
fi

echo "✅ Aqli CLI installation complete!"
echo "Try running 'aqli --version' to verify."
