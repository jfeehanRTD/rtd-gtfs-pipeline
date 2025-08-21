#!/bin/bash

# Achievement Appender Script
# Easily append major achievements to cursor_context.md

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Achievement Appender]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_info() {
    echo -e "${CYAN}ℹ️${NC} $1"
}

# Configuration
CONTEXT_FILE="cursor_context.md"
TEMP_FILE="/tmp/achievement-$(date +%s).md"

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --title \"Achievement Title\"     - Title of the achievement"
    echo "  --description \"Description\"     - Brief description"
    echo "  --accomplishments \"item1,item2\" - Comma-separated list of accomplishments"
    echo "  --technical \"details\"           - Technical details"
    echo "  --commands \"cmd1,cmd2\"          - Comma-separated list of usage commands"
    echo "  --files \"file1:desc1,file2:desc2\" - Comma-separated list of files with descriptions"
    echo "  --interactive                     - Interactive mode"
    echo "  --help                           - Show this help"
    echo
    echo "Examples:"
    echo "  $0 --title \"New Feature\" --description \"Implemented X\" --accomplishments \"Feature A,Feature B\""
    echo "  $0 --interactive"
}

# Function to get user input
get_input() {
    local prompt="$1"
    local default="$2"
    local input
    
    if [[ -n "$default" ]]; then
        read -p "$prompt [$default]: " input
        echo "${input:-$default}"
    else
        read -p "$prompt: " input
        echo "$input"
    fi
}

# Function to create achievement entry
create_achievement_entry() {
    local title="$1"
    local description="$2"
    local accomplishments="$3"
    local technical="$4"
    local commands="$5"
    local files="$6"
    local date=$(date +%Y-%m-%d)
    
    cat > "$TEMP_FILE" << EOF
### ${date}: ${title} ✅
**Achievement**: ${description}

**Key Accomplishments**:
EOF

    # Add accomplishments
    if [[ -n "$accomplishments" ]]; then
        IFS=',' read -ra ACCOMPLISHMENTS <<< "$accomplishments"
        for accomplishment in "${ACCOMPLISHMENTS[@]}"; do
            echo "- ✅ **${accomplishment}**: [Description]" >> "$TEMP_FILE"
        done
    fi

    # Add technical details
    if [[ -n "$technical" ]]; then
        echo "" >> "$TEMP_FILE"
        echo "**Technical Details**:" >> "$TEMP_FILE"
        echo "- ${technical}" >> "$TEMP_FILE"
    fi

    # Add usage commands
    if [[ -n "$commands" ]]; then
        echo "" >> "$TEMP_FILE"
        echo "**Usage Commands** (if applicable):" >> "$TEMP_FILE"
        echo '```bash' >> "$TEMP_FILE"
        IFS=',' read -ra COMMANDS <<< "$commands"
        for command in "${COMMANDS[@]}"; do
            echo "# ${command}" >> "$TEMP_FILE"
        done
        echo '```' >> "$TEMP_FILE"
    fi

    # Add files
    if [[ -n "$files" ]]; then
        echo "" >> "$TEMP_FILE"
        echo "**Files Created/Modified**:" >> "$TEMP_FILE"
        IFS=',' read -ra FILES <<< "$files"
        for file in "${FILES[@]}"; do
            IFS=':' read -ra FILE_PARTS <<< "$file"
            if [[ ${#FILE_PARTS[@]} -eq 2 ]]; then
                echo "- \`${FILE_PARTS[0]}\` - ${FILE_PARTS[1]}" >> "$TEMP_FILE"
            else
                echo "- \`${file}\` - [Description]" >> "$TEMP_FILE"
            fi
        done
    fi

    echo "" >> "$TEMP_FILE"
    echo "---" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"
}

# Function to append to context file
append_to_context() {
    local achievement_content="$1"
    
    # Find the position after the "Major Achievements Log" header
    local insert_line=$(grep -n "## Major Achievements Log" "$CONTEXT_FILE" | cut -d: -f1)
    
    if [[ -z "$insert_line" ]]; then
        print_error "Could not find 'Major Achievements Log' section in $CONTEXT_FILE"
        exit 1
    fi
    
    # Insert the achievement after the header
    local next_line=$((insert_line + 1))
    
    # Create temporary file with the new content
    head -n "$next_line" "$CONTEXT_FILE" > "${TEMP_FILE}.new"
    echo "$achievement_content" >> "${TEMP_FILE}.new"
    tail -n +$((next_line + 1)) "$CONTEXT_FILE" >> "${TEMP_FILE}.new"
    
    # Replace the original file
    mv "${TEMP_FILE}.new" "$CONTEXT_FILE"
}

# Interactive mode
interactive_mode() {
    print_status "Interactive Achievement Entry Mode"
    echo
    
    local title=$(get_input "Achievement title")
    local description=$(get_input "Brief description")
    local accomplishments=$(get_input "Key accomplishments (comma-separated)")
    local technical=$(get_input "Technical details (optional)")
    local commands=$(get_input "Usage commands (comma-separated, optional)")
    local files=$(get_input "Files created/modified (file:desc,file:desc, optional)")
    
    create_achievement_entry "$title" "$description" "$accomplishments" "$technical" "$commands" "$files"
    
    print_status "Generated achievement entry:"
    echo
    cat "$TEMP_FILE"
    echo
    
    local confirm=$(get_input "Append to $CONTEXT_FILE? (y/N)" "N")
    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        local content=$(cat "$TEMP_FILE")
        append_to_context "$content"
        print_success "Achievement appended to $CONTEXT_FILE"
    else
        print_warning "Achievement not appended"
    fi
}

# Parse command line arguments
TITLE=""
DESCRIPTION=""
ACCOMPLISHMENTS=""
TECHNICAL=""
COMMANDS=""
FILES=""
INTERACTIVE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --title)
            TITLE="$2"
            shift 2
            ;;
        --description)
            DESCRIPTION="$2"
            shift 2
            ;;
        --accomplishments)
            ACCOMPLISHMENTS="$2"
            shift 2
            ;;
        --technical)
            TECHNICAL="$2"
            shift 2
            ;;
        --commands)
            COMMANDS="$2"
            shift 2
            ;;
        --files)
            FILES="$2"
            shift 2
            ;;
        --interactive|-i)
            INTERACTIVE=true
            shift
            ;;
        --help|-h)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Check if context file exists
if [[ ! -f "$CONTEXT_FILE" ]]; then
    print_error "Context file $CONTEXT_FILE not found"
    exit 1
fi

# Interactive mode
if [[ "$INTERACTIVE" == true ]]; then
    interactive_mode
    exit 0
fi

# Check required parameters
if [[ -z "$TITLE" || -z "$DESCRIPTION" ]]; then
    print_error "Title and description are required"
    show_usage
    exit 1
fi

# Create achievement entry
create_achievement_entry "$TITLE" "$DESCRIPTION" "$ACCOMPLISHMENTS" "$TECHNICAL" "$COMMANDS" "$FILES"

# Show preview
print_status "Generated achievement entry:"
echo
cat "$TEMP_FILE"
echo

# Append to context file
content=$(cat "$TEMP_FILE")
append_to_context "$content"

print_success "Achievement appended to $CONTEXT_FILE"

# Clean up
rm -f "$TEMP_FILE"
