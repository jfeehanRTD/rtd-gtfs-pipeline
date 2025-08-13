#!/bin/bash

# SVN to Git Migration Tool
# This script pulls a project from SVN and pushes it to a GitHub repository
# Preserves commit history and handles branch mapping

set -e  # Exit on any error

# Default values
SVN_URL=""
GIT_REPO_URL=""
PROJECT_NAME=""
TEMP_DIR=""
PRESERVE_HISTORY=true
AUTHORS_FILE=""
SVN_BRANCHES="trunk,branches,tags"
GITHUB_TOKEN=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Usage function
usage() {
    echo -e "${BLUE}SVN to Git Migration Tool${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Required Options:"
    echo "  -s, --svn-url URL          SVN repository URL"
    echo "  -g, --git-repo URL         GitHub repository URL"
    echo "  -n, --name PROJECT         Project name for local directory"
    echo ""
    echo "Optional:"
    echo "  -a, --authors FILE         Authors mapping file (svn-user = Git User <email@domain.com>)"
    echo "  -b, --branches LAYOUT      SVN layout (default: trunk,branches,tags)"
    echo "  -t, --token TOKEN          GitHub personal access token"
    echo "  --no-history              Skip history preservation (faster)"
    echo "  --temp-dir DIR             Custom temporary directory"
    echo "  -h, --help                 Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 -s https://svn.company.com/repo/project -g git@github.com:user/project.git -n my-project"
    echo "  $0 -s svn://server/repo -g https://github.com/user/repo.git -n project -a authors.txt"
    echo ""
    echo "Authors file format:"
    echo "  svnuser1 = John Doe <john@company.com>"
    echo "  svnuser2 = Jane Smith <jane@company.com>"
    exit 1
}

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if required tools are installed
check_dependencies() {
    log_info "Checking dependencies..."
    
    local missing_deps=()
    
    if ! command -v git &> /dev/null; then
        missing_deps+=("git")
    fi
    
    if ! command -v svn &> /dev/null; then
        missing_deps+=("subversion")
    fi
    
    if ! command -v git-svn &> /dev/null; then
        missing_deps+=("git-svn")
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "Missing required dependencies: ${missing_deps[*]}"
        echo ""
        echo "Install missing dependencies:"
        echo "  macOS: brew install git subversion git-svn"
        echo "  Ubuntu: sudo apt-get install git subversion git-svn"
        echo "  CentOS: sudo yum install git subversion git-svn"
        exit 1
    fi
    
    log_success "All dependencies found"
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -s|--svn-url)
                SVN_URL="$2"
                shift 2
                ;;
            -g|--git-repo)
                GIT_REPO_URL="$2"
                shift 2
                ;;
            -n|--name)
                PROJECT_NAME="$2"
                shift 2
                ;;
            -a|--authors)
                AUTHORS_FILE="$2"
                shift 2
                ;;
            -b|--branches)
                SVN_BRANCHES="$2"
                shift 2
                ;;
            -t|--token)
                GITHUB_TOKEN="$2"
                shift 2
                ;;
            --no-history)
                PRESERVE_HISTORY=false
                shift
                ;;
            --temp-dir)
                TEMP_DIR="$2"
                shift 2
                ;;
            -h|--help)
                usage
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                ;;
        esac
    done
    
    # Validate required arguments
    if [[ -z "$SVN_URL" ]]; then
        log_error "SVN URL is required"
        usage
    fi
    
    if [[ -z "$GIT_REPO_URL" ]]; then
        log_error "Git repository URL is required"
        usage
    fi
    
    if [[ -z "$PROJECT_NAME" ]]; then
        log_error "Project name is required"
        usage
    fi
}

# Create authors file if it doesn't exist
create_authors_file() {
    if [[ -n "$AUTHORS_FILE" ]] && [[ ! -f "$AUTHORS_FILE" ]]; then
        log_info "Creating authors file template: $AUTHORS_FILE"
        
        # Get list of SVN authors
        log_info "Extracting SVN authors from repository..."
        svn log -q "$SVN_URL" | awk -F '|' '/^r/ {gsub(/ /, "", $2); print $2}' | sort -u > /tmp/svn_authors.txt
        
        # Create template authors file
        echo "# SVN to Git Authors Mapping" > "$AUTHORS_FILE"
        echo "# Format: svn-username = Git Name <email@domain.com>" >> "$AUTHORS_FILE"
        echo "" >> "$AUTHORS_FILE"
        
        while read -r author; do
            if [[ -n "$author" ]]; then
                echo "$author = $author <$author@company.com>" >> "$AUTHORS_FILE"
            fi
        done < /tmp/svn_authors.txt
        
        log_warning "Authors file created: $AUTHORS_FILE"
        log_warning "Please edit this file with correct names and email addresses before proceeding"
        read -p "Press Enter after editing the authors file to continue..."
        
        rm -f /tmp/svn_authors.txt
    fi
}

# Set up temporary directory
setup_temp_dir() {
    if [[ -z "$TEMP_DIR" ]]; then
        TEMP_DIR="/tmp/svn-to-git-$$"
    fi
    
    if [[ -d "$TEMP_DIR" ]]; then
        log_warning "Temporary directory exists: $TEMP_DIR"
        read -p "Remove existing directory? [y/N]: " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -rf "$TEMP_DIR"
        else
            log_error "Cannot proceed with existing directory"
            exit 1
        fi
    fi
    
    mkdir -p "$TEMP_DIR"
    log_info "Using temporary directory: $TEMP_DIR"
}

# Clone SVN repository
clone_svn_repo() {
    log_info "Cloning SVN repository..."
    
    cd "$TEMP_DIR"
    
    # Build git-svn command
    local git_svn_cmd="git svn clone"
    
    if [[ "$PRESERVE_HISTORY" == true ]]; then
        # Parse SVN layout
        IFS=',' read -ra LAYOUT <<< "$SVN_BRANCHES"
        for element in "${LAYOUT[@]}"; do
            case "$element" in
                trunk)
                    git_svn_cmd+=" --trunk=trunk"
                    ;;
                branches)
                    git_svn_cmd+=" --branches=branches"
                    ;;
                tags)
                    git_svn_cmd+=" --tags=tags"
                    ;;
            esac
        done
    else
        git_svn_cmd+=" --no-metadata"
    fi
    
    # Add authors file if provided
    if [[ -n "$AUTHORS_FILE" ]] && [[ -f "$AUTHORS_FILE" ]]; then
        git_svn_cmd+=" --authors-file=$AUTHORS_FILE"
    fi
    
    git_svn_cmd+=" \"$SVN_URL\" \"$PROJECT_NAME\""
    
    log_info "Running: $git_svn_cmd"
    eval $git_svn_cmd
    
    if [[ $? -eq 0 ]]; then
        log_success "SVN repository cloned successfully"
    else
        log_error "Failed to clone SVN repository"
        exit 1
    fi
    
    cd "$PROJECT_NAME"
}

# Convert SVN tags to Git tags
convert_tags() {
    if [[ "$PRESERVE_HISTORY" == true ]]; then
        log_info "Converting SVN tags to Git tags..."
        
        # Convert remote tags to proper git tags
        for tag in $(git branch -r | grep "tags/" | sed 's/.*tags\///'); do
            if [[ "$tag" != *"@"* ]]; then  # Skip peg revisions
                log_info "Creating tag: $tag"
                git tag "$tag" "remotes/tags/$tag"
                git branch -r -d "tags/$tag"
            fi
        done
        
        log_success "Tags converted successfully"
    fi
}

# Convert SVN branches to Git branches
convert_branches() {
    if [[ "$PRESERVE_HISTORY" == true ]]; then
        log_info "Converting SVN branches to Git branches..."
        
        # Convert remote branches to local branches
        for branch in $(git branch -r | grep -v tags | grep -v trunk | sed 's/.*\///'); do
            if [[ "$branch" != *"@"* ]]; then  # Skip peg revisions
                log_info "Creating branch: $branch"
                git checkout -b "$branch" "remotes/$branch"
            fi
        done
        
        # Switch to main/master branch
        if git show-ref --verify --quiet refs/remotes/trunk; then
            git checkout -b main remotes/trunk
        else
            git checkout main 2>/dev/null || git checkout master 2>/dev/null || true
        fi
        
        log_success "Branches converted successfully"
    fi
}

# Clean up SVN metadata
cleanup_svn_metadata() {
    log_info "Cleaning up SVN metadata..."
    
    # Remove git-svn metadata
    rm -rf .git/svn
    
    # Remove SVN remote references
    git config --remove-section svn-remote.svn 2>/dev/null || true
    
    # Clean up remote references
    for remote in $(git branch -r | grep -v origin); do
        git branch -r -d "$remote" 2>/dev/null || true
    done
    
    log_success "SVN metadata cleaned up"
}

# Add GitHub remote and push
push_to_github() {
    log_info "Adding GitHub remote and pushing..."
    
    # Set up GitHub authentication if token provided
    if [[ -n "$GITHUB_TOKEN" ]]; then
        # Configure Git to use token for HTTPS
        if [[ "$GIT_REPO_URL" =~ ^https:// ]]; then
            local auth_url=$(echo "$GIT_REPO_URL" | sed "s/https:\/\//https:\/\/$GITHUB_TOKEN@/")
            git remote add origin "$auth_url"
        else
            git remote add origin "$GIT_REPO_URL"
        fi
    else
        git remote add origin "$GIT_REPO_URL"
    fi
    
    # Push main branch first
    log_info "Pushing main branch..."
    git push -u origin main 2>/dev/null || git push -u origin master 2>/dev/null || {
        log_warning "Failed to push main/master branch, trying current branch"
        git push -u origin HEAD
    }
    
    # Push all other branches
    if [[ "$PRESERVE_HISTORY" == true ]]; then
        log_info "Pushing all branches..."
        git push --all origin
        
        # Push tags
        log_info "Pushing tags..."
        git push --tags origin
    fi
    
    log_success "Repository pushed to GitHub successfully"
}

# Generate migration report
generate_report() {
    log_info "Generating migration report..."
    
    local report_file="$TEMP_DIR/migration-report.txt"
    
    cat > "$report_file" << EOF
SVN to Git Migration Report
==========================
Date: $(date)
SVN URL: $SVN_URL
Git Repository: $GIT_REPO_URL
Project Name: $PROJECT_NAME
Preserve History: $PRESERVE_HISTORY

Migration Statistics:
- Total commits: $(git rev-list --all --count)
- Total branches: $(git branch -a | grep -v remotes | wc -l)
- Total tags: $(git tag | wc -l)
- Repository size: $(du -sh . | cut -f1)

Branches migrated:
$(git branch -a | grep -v remotes)

Tags migrated:
$(git tag)

Authors found:
$(git log --format='%an <%ae>' | sort -u)

Commands used:
git svn clone $(echo "$git_svn_cmd" | sed 's/git svn clone//')

Next steps:
1. Verify the migration at: $GIT_REPO_URL
2. Update any CI/CD configurations
3. Notify team members of the new repository
4. Archive or redirect the SVN repository
EOF

    echo ""
    log_success "Migration completed successfully!"
    echo ""
    echo "Migration report saved to: $report_file"
    echo ""
    echo "Repository details:"
    echo "  GitHub URL: $GIT_REPO_URL"
    echo "  Local copy: $TEMP_DIR/$PROJECT_NAME"
    echo "  Branches: $(git branch | wc -l | tr -d ' ')"
    echo "  Tags: $(git tag | wc -l | tr -d ' ')"
    echo "  Commits: $(git rev-list --all --count)"
    echo ""
}

# Cleanup function
cleanup() {
    if [[ -n "$TEMP_DIR" ]] && [[ -d "$TEMP_DIR" ]]; then
        read -p "Remove temporary directory $TEMP_DIR? [y/N]: " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -rf "$TEMP_DIR"
            log_info "Temporary directory removed"
        else
            log_info "Temporary directory preserved: $TEMP_DIR"
        fi
    fi
}

# Main function
main() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════╗"
    echo "║        SVN to Git Migration Tool     ║"
    echo "║                                      ║"
    echo "║  Preserves history, branches & tags  ║"
    echo "╚══════════════════════════════════════╝"
    echo -e "${NC}"
    
    parse_args "$@"
    check_dependencies
    create_authors_file
    setup_temp_dir
    
    # Set trap for cleanup
    trap cleanup EXIT
    
    clone_svn_repo
    convert_tags
    convert_branches
    cleanup_svn_metadata
    push_to_github
    generate_report
    
    log_success "Migration completed! 🎉"
}

# Run main function with all arguments
main "$@"