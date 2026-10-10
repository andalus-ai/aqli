#!/bin/bash

# Aqli Deployment Utility
# Facilitates multi-module project versioning, git tagging, and artifact deployment.

set -e

# Default values
NEW_VERSION=""
REPO_URL=""
SNAPSHOT_REPO_URL=""
DRY_RUN=false
SKIP_TESTS=true
GIT_TAG=false
GIT_PUSH=true      # Enabled by default
RUN_MAVEN=true
SKIP_COMMIT=false
FORCE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get project root (script is in scripts/ or root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
    echo -e "${BLUE}--------------------------------------------------${NC}"
    echo -e "${GREEN} Aqli Deployment Utility ${NC}"
    echo -e "${BLUE}--------------------------------------------------${NC}"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -v, --version <new-version>      Update project version (e.g., 0.1.0 or 1.0.1-SNAPSHOT)"
    echo "  -r, --repo <url>                 Override release repository URL"
    echo "  -s, --snapshot-repo <url>        Override snapshot repository URL"
    echo "  -t, --tag                        Create git tag (auto-enabled for release versions)"
    echo "  --no-push                        Skip pushing commits and tags to remote"
    echo "  -f, --force                      Force actions (e.g., overwrite existing tags)"
    echo "  --no-build                       Skip Maven build and deployment"
    echo "  --keep-tests                     Do not skip tests during build"
    echo "  --skip-commit                    Skip git commit after version update"
    echo "  --dry-run                        Show commands without executing them"
    echo "  -h, --help                       Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -v 0.1.0                      # Version, build/deploy, tag, and PUSH"
    echo "  $0 -v 0.1.0 --no-push            # Build/deploy and tag locally only"
    echo "  $0 -v 1.0.0-SNAPSHOT             # Version and build/deploy (no tag, but PUSHES commit)"
    echo ""
    exit 0
}

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -v|--version) NEW_VERSION="$2"; shift ;;
        -r|--repo) REPO_URL="$2"; shift ;;
        -s|--snapshot-repo) SNAPSHOT_REPO_URL="$2"; shift ;;
        -t|--tag) GIT_TAG=true ;;
        -p|--push) GIT_PUSH=true ;; # Keep for compat
        --no-push) GIT_PUSH=false ;;
        -f|--force) FORCE=true ;;
        --no-build|--no-jar) RUN_MAVEN=false ;;
        --keep-tests) SKIP_TESTS=false ;;
        --skip-commit) SKIP_COMMIT=true ;;
        --dry-run) DRY_RUN=true ;;
        -h|--help) usage ;;
        *) echo -e "${RED}Unknown parameter: $1${NC}"; usage ;;
    esac
    shift
done

# Navigate to project root
cd "$PROJECT_ROOT"

echo -e "${BLUE}--------------------------------------------------${NC}"
echo -e "${GREEN} Aqli Deployment Utility ${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"
echo ""

# Helper function for dry-run execution
run_cmd() {
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} $*"
    else
        eval "$@"
    fi
}

# Version Management
if [ -n "$NEW_VERSION" ]; then
    echo -e "${BLUE}>>> Updating version to: ${GREEN}$NEW_VERSION${NC}"
    
    # Check if version is a release (not SNAPSHOT)
    if [[ "$NEW_VERSION" != *"-SNAPSHOT" ]]; then
        echo -e "${YELLOW}ℹ Release version detected (not SNAPSHOT)${NC}"
        GIT_TAG=true
    fi
    
    # Update version using Maven versions plugin
    echo -e "${BLUE}>>> Updating Maven POMs...${NC}"
    run_cmd "mvn versions:set -DnewVersion=\"$NEW_VERSION\" -DgenerateBackupPoms=false"
    
    # Commit version changes
    COMMIT_SUCCESS=false
    if [ "$SKIP_COMMIT" = false ] && [ "$DRY_RUN" = false ]; then
        echo -e "${BLUE}>>> Committing version changes...${NC}"
        git add "**/pom.xml"
        if git commit -m "chore: bump version to $NEW_VERSION"; then
            COMMIT_SUCCESS=true
        else
            echo -e "${YELLOW}⚠ No changes to commit or not a git repo${NC}"
        fi
    fi
    
    # Create git tag if requested
    if [ "$GIT_TAG" = true ]; then
        TAG_NAME="$NEW_VERSION"
        echo -e "${BLUE}>>> Creating git tag: ${GREEN}$TAG_NAME${NC}"
        
        # Check if tag already exists
        if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
            if [ "$FORCE" = true ]; then
                echo -e "${YELLOW}⚠ Tag $TAG_NAME already exists. Forcing recreation...${NC}"
                run_cmd "git tag -d \"$TAG_NAME\""
                run_cmd "git tag -a \"$TAG_NAME\" -m \"Release $NEW_VERSION\""
                echo -e "${GREEN}✓ Git tag force-recreated: $TAG_NAME${NC}"
            else
                echo -e "${YELLOW}⚠ Tag $TAG_NAME already exists. Skipping tag creation (use -f to force).${NC}"
            fi
        else
            run_cmd "git tag -a \"$TAG_NAME\" -m \"Release $NEW_VERSION\""
            echo -e "${GREEN}✓ Git tag created: $TAG_NAME${NC}"
        fi
    fi

    # Push to remote if enabled
    if [ "$GIT_PUSH" = true ] && [ "$DRY_RUN" = false ]; then
        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
        echo -e "${BLUE}>>> Pushing to remote (branch: $CURRENT_BRANCH)...${NC}"
        
        # Push the branch/commits
        run_cmd "git push origin $CURRENT_BRANCH"

        # Push the tag if it was created
        if [ "$GIT_TAG" = true ]; then
            echo -e "${BLUE}>>> Pushing git tag to remote...${NC}"
            if [ "$FORCE" = true ]; then
                run_cmd "git push origin :refs/tags/\"$TAG_NAME\" || true"
                run_cmd "git push origin \"$TAG_NAME\""
            else
                run_cmd "git push origin \"$TAG_NAME\""
            fi
        fi
        echo -e "${GREEN}✓ Pushed to remote successfully${NC}"
    fi
    
    echo -e "${GREEN}✓ Version updated to $NEW_VERSION${NC}"
    echo ""
fi

# Build and Deployment
if [ "$RUN_MAVEN" = true ]; then
    MVN_ARGS="clean install deploy"

    if [ "$SKIP_TESTS" = true ]; then
        MVN_ARGS="$MVN_ARGS -DskipTests"
    fi

    if [ -n "$REPO_URL" ]; then
        MVN_ARGS="$MVN_ARGS -Ddeployment.repo.url=$REPO_URL"
    fi

    if [ -n "$SNAPSHOT_REPO_URL" ]; then
        MVN_ARGS="$MVN_ARGS -Ddeployment.snapshot.repo.url=$SNAPSHOT_REPO_URL"
    fi

    echo -e "${BLUE}>>> Running deployment: mvn $MVN_ARGS${NC}"
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} mvn $MVN_ARGS"
    else
        mvn $MVN_ARGS
    fi
else
    echo -e "${YELLOW}⚠ Maven build and deployment skipped (--no-build)${NC}"
fi

echo ""
echo -e "${BLUE}--------------------------------------------------${NC}"
echo -e "${GREEN} Deployment process completed!${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"

# Summary
if [ -n "$NEW_VERSION" ]; then
    echo ""
    echo -e "${BLUE}Summary:${NC}"
    echo -e "  Version:  ${GREEN}$NEW_VERSION${NC}"
    if [ "$GIT_TAG" = true ]; then
        echo -e "  Git Tag:  ${GREEN}$NEW_VERSION${NC}"
    fi
    echo -e "  Pushed:   $( [ "$GIT_PUSH" = true ] && echo -e "${GREEN}Yes${NC}" || echo -e "${YELLOW}No${NC}" )"
    if [ "$RUN_MAVEN" = true ]; then
        echo -e "  Build:    ${GREEN}clean install deploy${NC}"
    else
        echo -e "  Build:    ${YELLOW}Skipped${NC}"
    fi
    echo ""
fi
