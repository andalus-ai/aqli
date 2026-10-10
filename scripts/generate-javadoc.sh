#!/bin/bash

# Aqli Javadoc Generation Utility
# Aggregates Javadoc across all modules into a single searchable documentation.

set -e

# Get project root (script is in scripts/ or root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Navigate to project root
cd "$PROJECT_ROOT"

echo "--------------------------------------------------"
echo " Aqli Javadoc Generator "
echo "--------------------------------------------------"

echo ">>> Generating aggregate Javadoc for all modules..."
mvn javadoc:aggregate -DskipTests

APIDOCS_INDEX="target/site/apidocs/index.html"

if [ -f "$APIDOCS_INDEX" ]; then
    echo "--------------------------------------------------"
    echo " Javadoc generated successfully!"
    echo " Location: $PROJECT_ROOT/$APIDOCS_INDEX"
    echo "--------------------------------------------------"
    
    # Optional: Open on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        read -p "Would you like to open it in your browser? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            open "$APIDOCS_INDEX"
        fi
    fi
else
    echo "--------------------------------------------------"
    echo " Error: Javadoc generation failed or index not found."
    echo "--------------------------------------------------"
    exit 1
fi
