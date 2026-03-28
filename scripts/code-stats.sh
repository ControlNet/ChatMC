#!/bin/bash

# Code statistics script for ChatMC base + extensions
# Calculates lines of code, classes, and methods for each module
#
# Usage:
#   bash scripts/code-stats.sh
#   OR
#   ./scripts/code-stats.sh (if executable)
#
# Note: This script requires bash and standard Unix tools (find, grep, wc, etc.)
#       For Windows, use code-stats.ps1 instead

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Subprojects to analyze
SUBPROJECTS=(
    "base/core"
    "base/common-1.20.1"
    "base/forge-1.20.1"
    "base/fabric-1.20.1"
    "ext-ae/core"
    "ext-ae/common-1.20.1"
    "ext-ae/forge-1.20.1"
    "ext-ae/fabric-1.20.1"
    "ext-matrix/core"
    "ext-matrix/common-1.20.1"
    "ext-matrix/forge-1.20.1"
    "ext-matrix/fabric-1.20.1"
)

# Function to count lines in Java files
count_lines() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    find "$dir/src/main/java" -name "*.java" 2>/dev/null | xargs cat 2>/dev/null | wc -l | tr -d ' ' || echo "0"
}

# Function to count non-empty lines (excluding blank lines)
count_code_lines() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    find "$dir/src/main/java" -name "*.java" 2>/dev/null | xargs grep -v '^\s*$' 2>/dev/null | wc -l | tr -d ' ' || echo "0"
}

# Function to count classes (public/private/protected class/interface/enum/record)
count_classes() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    # Match class, interface, enum, record declarations (with or without modifiers)
    # Pattern: [modifiers] [abstract/final] class/interface/enum/record [name]
    find "$dir/src/main/java" -name "*.java" 2>/dev/null | xargs grep -E '^\s*((public|private|protected|static|abstract|final|sealed)\s+)*(class|interface|enum|record)\s+\w+' 2>/dev/null | grep -v '^\s*//' | wc -l | tr -d ' ' || echo "0"
}

# Function to count methods (public/private/protected methods, excluding constructors)
count_methods() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    # Match method declarations: return type, method name, parameters
    # Exclude constructors (same name as class) and getters/setters patterns
    find "$dir/src/main/java" -name "*.java" 2>/dev/null | xargs grep -E '^\s*(public|private|protected|static)?\s+.*\s+\w+\s*\([^)]*\)\s*\{?\s*$' 2>/dev/null | grep -v '^\s*//' | wc -l | tr -d ' ' || echo "0"
}

# Function to count methods more accurately (using better pattern)
count_methods_accurate() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    # Count method declarations more accurately
    # Pattern: [modifiers] [return type] [method name]([params]) [throws] { or ;
    # Exclude constructors by checking if method name matches class name (simplified)
    local count=0
    while IFS= read -r file; do
        # Count lines that look like method declarations
        # Match: modifiers, return type (including void), method name, params, optional throws, { or ;
        local file_count=$(grep -E '^\s*((public|private|protected|static|final|abstract|synchronized|native|default)\s+)*(void|[A-Za-z_][A-Za-z0-9_<>\[\],\s]+)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^)]*\)\s*(throws\s+[^{;]+)?\s*[;{]' "$file" 2>/dev/null | grep -v '^\s*//' | wc -l | tr -d ' ' || echo "0")
        count=$((count + file_count))
    done < <(find "$dir/src/main/java" -name "*.java" 2>/dev/null)
    echo "$count"
}

# Function to count files
count_files() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        echo "0"
        return
    fi
    find "$dir/src/main/java" -name "*.java" 2>/dev/null | wc -l | tr -d ' ' || echo "0"
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ChatMC Code Statistics${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Table header
printf "%-28s %10s %10s %10s %10s %10s\n" "Module" "Files" "Total Lines" "Code Lines" "Classes" "Methods"
echo "--------------------------------------------------------------------------------"

TOTAL_FILES=0
TOTAL_LINES=0
TOTAL_CODE_LINES=0
TOTAL_CLASSES=0
TOTAL_METHODS=0

for subproject in "${SUBPROJECTS[@]}"; do
    if [ ! -d "$subproject" ]; then
        continue
    fi
    
    files=$(count_files "$subproject")
    lines=$(count_lines "$subproject")
    code_lines=$(count_code_lines "$subproject")
    classes=$(count_classes "$subproject")
    methods=$(count_methods_accurate "$subproject")
    
    printf "%-28s %10s %10s %10s %10s %10s\n" "$subproject" "$files" "$lines" "$code_lines" "$classes" "$methods"
    
    TOTAL_FILES=$((TOTAL_FILES + files))
    TOTAL_LINES=$((TOTAL_LINES + lines))
    TOTAL_CODE_LINES=$((TOTAL_CODE_LINES + code_lines))
    TOTAL_CLASSES=$((TOTAL_CLASSES + classes))
    TOTAL_METHODS=$((TOTAL_METHODS + methods))
done

echo "--------------------------------------------------------------------------------"
printf "%-28s %10s %10s %10s %10s %10s\n" "${GREEN}TOTAL${NC}" "$TOTAL_FILES" "$TOTAL_LINES" "$TOTAL_CODE_LINES" "$TOTAL_CLASSES" "$TOTAL_METHODS"
echo ""

# Additional statistics
echo -e "${YELLOW}Additional Statistics:${NC}"
echo ""

# Calculate averages
if [ "$TOTAL_FILES" -gt 0 ]; then
    avg_lines_per_file=$((TOTAL_LINES / TOTAL_FILES))
    avg_code_lines_per_file=$((TOTAL_CODE_LINES / TOTAL_FILES))
    avg_classes_per_file=$((TOTAL_CLASSES / TOTAL_FILES))
    avg_methods_per_file=$((TOTAL_METHODS / TOTAL_FILES))
    
    echo -e "Average lines per file: ${GREEN}$avg_lines_per_file${NC}"
    echo -e "Average code lines per file: ${GREEN}$avg_code_lines_per_file${NC}"
    echo -e "Average classes per file: ${GREEN}$avg_classes_per_file${NC}"
    echo -e "Average methods per file: ${GREEN}$avg_methods_per_file${NC}"
    echo ""
fi

# Calculate code density
if [ "$TOTAL_LINES" -gt 0 ]; then
    code_density=$((TOTAL_CODE_LINES * 100 / TOTAL_LINES))
    echo -e "Code density (non-empty lines): ${GREEN}${code_density}%${NC}"
    echo ""
fi

# Breakdown by subproject
echo -e "${YELLOW}Breakdown by Subproject:${NC}"
echo ""

for subproject in "${SUBPROJECTS[@]}"; do
    if [ ! -d "$subproject" ]; then
        continue
    fi
    
    files=$(count_files "$subproject")
    lines=$(count_lines "$subproject")
    code_lines=$(count_code_lines "$subproject")
    classes=$(count_classes "$subproject")
    methods=$(count_methods_accurate "$subproject")
    
    if [ "$TOTAL_LINES" -gt 0 ]; then
        percentage=$((lines * 100 / TOTAL_LINES))
        echo -e "${BLUE}$subproject:${NC}"
        echo -e "  Files: $files"
        echo -e "  Lines: $lines (${percentage}% of total)"
        echo -e "  Code lines: $code_lines"
        echo -e "  Classes: $classes"
        echo -e "  Methods: $methods"
        echo ""
    fi
done

echo -e "${GREEN}Statistics calculation complete!${NC}"
