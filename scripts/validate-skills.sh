#!/bin/bash
#
# Validate skills against Agent Skills specification (https://agentskills.io/specification)
# This validator checks:
# - SKILL.md exists and has valid YAML frontmatter
# - name field: lowercase, hyphens only, matches directory name, no leading/trailing hyphens
# - description field: 1-1024 characters, non-empty
# - metadata field structure (optional)
# - license, compatibility, allowed-tools fields (optional)
# - Body content (minimal validation)
#

set -e

SKILLS_DIR="${1:-.}"
ERRORS=0
WARNINGS=0
VALID_SKILLS=0

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
error() {
    echo -e "${RED}ERROR${NC}: $1" >&2
    ((ERRORS++))
}

warning() {
    echo -e "${YELLOW}WARNING${NC}: $1"
    ((WARNINGS++))
}

success() {
    echo -e "${GREEN}✓${NC} $1"
}

info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

# Validate YAML frontmatter extraction and parsing
validate_skill() {
    local skill_dir="$1"
    local skill_name=$(basename "$skill_dir")
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Validating: $skill_name"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Check if SKILL.md exists
    if [ ! -f "$skill_dir/SKILL.md" ]; then
        error "$skill_name: SKILL.md not found"
        return 1
    fi
    
    success "$skill_name: SKILL.md exists"
    
    # Extract frontmatter
    local frontmatter_end=$(grep -n "^---$" "$skill_dir/SKILL.md" | head -2 | tail -1 | cut -d: -f1)
    if [ -z "$frontmatter_end" ]; then
        error "$skill_name: No closing --- found for YAML frontmatter"
        return 1
    fi
    
    # Validate frontmatter exists
    if ! head -1 "$skill_dir/SKILL.md" | grep -q "^---$"; then
        error "$skill_name: SKILL.md must start with --- (YAML frontmatter)"
        return 1
    fi
    
    success "$skill_name: Valid YAML frontmatter structure"
    
    # Extract and validate 'name' field
    local name_value=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^name:" | head -1 | sed 's/^name:[[:space:]]*//;s/[[:space:]]*$//')
    
    if [ -z "$name_value" ]; then
        error "$skill_name: 'name' field is required"
        return 1
    fi
    
    # Validate name field constraints
    if [ ${#name_value} -gt 64 ]; then
        error "$skill_name: 'name' exceeds 64 characters (${#name_value})"
        return 1
    fi
    
    if ! [[ "$name_value" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ ]]; then
        error "$skill_name: 'name' contains invalid characters. Must be lowercase letters, numbers, and hyphens only"
        return 1
    fi
    
    if [[ "$name_value" == -* ]]; then
        error "$skill_name: 'name' cannot start with a hyphen"
        return 1
    fi
    
    if [[ "$name_value" == *- ]]; then
        error "$skill_name: 'name' cannot end with a hyphen"
        return 1
    fi
    
    if [[ "$name_value" == *--* ]]; then
        error "$skill_name: 'name' cannot contain consecutive hyphens"
        return 1
    fi
    
    if [ "$name_value" != "$skill_name" ]; then
        error "$skill_name: 'name' field ($name_value) must match directory name ($skill_name)"
        return 1
    fi
    
    success "$skill_name: 'name' field is valid ($name_value)"
    
    # Extract and validate 'description' field
    local description_value=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^description:" | head -1 | sed 's/^description:[[:space:]]*//;s/[[:space:]]*$//')
    
    if [ -z "$description_value" ]; then
        error "$skill_name: 'description' field is required"
        return 1
    fi
    
    local desc_len=${#description_value}
    if [ $desc_len -lt 1 ] || [ $desc_len -gt 1024 ]; then
        error "$skill_name: 'description' must be 1-1024 characters (got $desc_len)"
        return 1
    fi
    
    success "$skill_name: 'description' field is valid ($desc_len chars)"
    
    # Check for optional fields
    local license=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^license:" | head -1)
    if [ -n "$license" ]; then
        success "$skill_name: 'license' field present"
    fi
    
    local compatibility=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^compatibility:" | head -1 | sed 's/^compatibility:[[:space:]]*//;s/[[:space:]]*$//')
    if [ -n "$compatibility" ]; then
        local compat_len=${#compatibility}
        if [ $compat_len -gt 500 ]; then
            warning "$skill_name: 'compatibility' exceeds 500 characters ($compat_len)"
        else
            success "$skill_name: 'compatibility' field present and valid"
        fi
    fi
    
    local metadata=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^metadata:" | head -1)
    if [ -n "$metadata" ]; then
        success "$skill_name: 'metadata' field present"
    fi
    
    local allowed_tools=$(sed -n '1,'$frontmatter_end'p' "$skill_dir/SKILL.md" | grep "^allowed-tools:" | head -1)
    if [ -n "$allowed_tools" ]; then
        success "$skill_name: 'allowed-tools' field present"
    fi
    
    # Check body content exists
    local body_start=$((frontmatter_end + 1))
    local body_lines=$(tail -n +$body_start "$skill_dir/SKILL.md" | wc -l)
    
    if [ "$body_lines" -lt 2 ]; then
        warning "$skill_name: Body content is very minimal (${body_lines} lines)"
    else
        success "$skill_name: Body content present (${body_lines} lines)"
    fi
    
    # Check directory structure
    if [ -d "$skill_dir/scripts" ]; then
        success "$skill_name: scripts/ directory found"
    fi
    
    if [ -d "$skill_dir/references" ]; then
        success "$skill_name: references/ directory found"
    fi
    
    if [ -d "$skill_dir/assets" ]; then
        success "$skill_name: assets/ directory found"
    fi
    
    echo ""
    ((VALID_SKILLS++))
    return 0
}

# Main
main() {
    if [ ! -d "$SKILLS_DIR" ]; then
        error "Skills directory not found: $SKILLS_DIR"
        exit 1
    fi
    
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "Agent Skills Specification Validator"
    echo "Specification: https://agentskills.io/specification"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo ""
    
    # Find all skill directories (directories that are direct children of SKILLS_DIR)
    local skill_dirs=()
    for dir in "$SKILLS_DIR"/*; do
        if [ -d "$dir" ] && [ "$(basename "$dir")" != ".DS_Store" ]; then
            skill_dirs+=("$dir")
        fi
    done
    
    if [ ${#skill_dirs[@]} -eq 0 ]; then
        error "No skill directories found in $SKILLS_DIR"
        exit 1
    fi
    
    # Validate each skill
    for skill_dir in "${skill_dirs[@]}"; do
        validate_skill "$skill_dir" || true
    done
    
    # Summary
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "Validation Summary"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo ""
    
    if [ $ERRORS -eq 0 ]; then
        echo -e "${GREEN}✓ All $VALID_SKILLS skills are valid${NC}"
    else
        echo -e "${RED}✗ Validation failed${NC}"
    fi
    
    echo ""
    echo "Results:"
    echo "  Valid Skills:  $VALID_SKILLS"
    echo "  Errors:        $ERRORS"
    echo "  Warnings:      $WARNINGS"
    echo ""
    
    if [ $ERRORS -gt 0 ]; then
        exit 1
    fi
    
    exit 0
}

main "$@"
