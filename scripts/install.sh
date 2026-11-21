#!/usr/bin/env bash
# AQLI bootstrap installer: prepares Java 25 and delegates to the source installer.
# Usage: curl -fsSL https://raw.githubusercontent.com/andalus-ai/aqli/main/scripts/install.sh | bash

set -euo pipefail

# --- Configuration ---
AQLI_REPO="${AQLI_REPO:-andalus-ai/aqli}"
AQLI_BRANCH="${AQLI_BRANCH:-main}"
export AQLI_REPO AQLI_BRANCH

# --- Visuals ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Aqli - Complete Environment Setup                          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo "This script will prepare your environment for Aqli."
echo ""

# --- Helper: Detect Interactivity ---
if [ -t 0 ]; then
    INTERACTIVE=true
else
    INTERACTIVE=false
fi

prompt_confirm() {
    if [ "$INTERACTIVE" = false ]; then
        return 0 # Auto-confirm in non-interactive mode
    fi
    local reply
    # Use /dev/tty so interactive prompt works even if script is piped via curl
    read -p "$1 (y/n) " -n 1 -r reply < /dev/tty
    echo
    if [[ $reply =~ ^[Yy]$ ]]; then
        return 0
    fi
    return 1
}

# --- 1. Java 25 Verification ---
echo -e "${BLUE}🔍 Checking Java environment...${NC}"
JAVA_OK=false
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    if [[ "$JAVA_VERSION" == *"\"25"* ]]; then
        JAVA_OK=true
        echo -e "${GREEN}✓ Found $JAVA_VERSION${NC}"
    else
        echo -e "${YELLOW}⚠ Found $JAVA_VERSION, but Java 25 is required.${NC}"
    fi
fi

if [ "$JAVA_OK" = false ]; then
    if prompt_confirm "Java 25 is required but not found. Install it now via SDKMAN?"; then
        echo -e "${BLUE}📦 Installing Java 25 via SDKMAN...${NC}"
        curl -fsSL "https://raw.githubusercontent.com/${AQLI_REPO}/${AQLI_BRANCH}/scripts/install-jdk.sh" | bash
        
        # Load SDKMAN if it was just installed
        export SDKMAN_DIR="$HOME/.sdkman"
        if [ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
            source "$SDKMAN_DIR/bin/sdkman-init.sh"
        fi
    else
        echo -e "${RED}JDK 25 is required. Installation cancelled.${NC}"
        exit 1
    fi
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1 || true)"
if [[ "$JAVA_VERSION" != *'"25'* ]]; then
    echo -e "${RED}JDK 25 is required, but the active Java runtime is: ${JAVA_VERSION:-not found}.${NC}"
    echo -e "${YELLOW}Install JDK 25, then run this installer again.${NC}"
    exit 1
fi

# --- 2. Aqli CLI Installation ---
echo ""
echo -e "${BLUE}🚀 Checking Aqli CLI...${NC}"
if command -v aqli >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Aqli CLI is already available: $(aqli --version 2>/dev/null || echo 'installed')${NC}"
    if prompt_confirm "Do you want to reinstall/update Aqli CLI?"; then
        echo -e "${BLUE}📦 Updating Aqli CLI...${NC}"
        curl -fsSL "https://raw.githubusercontent.com/${AQLI_REPO}/${AQLI_BRANCH}/scripts/install-cli.sh" | bash
    fi
else
    if prompt_confirm "Aqli CLI not found. Install it now?"; then
        echo -e "${BLUE}📦 Installing Aqli CLI...${NC}"
        curl -fsSL "https://raw.githubusercontent.com/${AQLI_REPO}/${AQLI_BRANCH}/scripts/install-cli.sh" | bash
    else
        echo -e "${YELLOW}⏭️ Skipping Aqli CLI installation.${NC}"
    fi
fi

# --- 3. Summary ---
echo ""
echo -e "${GREEN}AQLI installation workflow completed.${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo -e "  ${YELLOW}aqli --version${NC}"
echo -e "  ${YELLOW}aqli --help${NC}"
echo -e "  ${YELLOW}aqli pull hf:Qwen/Qwen2.5-7B-Instruct-GGUF${NC}"
echo ""
echo -e "Documentation: https://github.com/${AQLI_REPO}"
echo ""
