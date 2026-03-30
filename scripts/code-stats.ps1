# Code statistics script for MineAgent base + extensions
# Calculates lines of code, classes, and methods for each module
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/code-stats.ps1
#   OR
#   .\scripts\code-stats.ps1 (if execution policy allows)
#
# This script analyzes Java source files in each subproject and provides:
# - Total lines of code
# - Non-empty lines (code density)
# - Number of classes/interfaces/enums/records
# - Number of methods
# - Averages and percentages

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

Set-Location $ProjectRoot

# Subprojects to analyze
$Subprojects = @(
    "base/core",
    "base/common-1.20.1",
    "base/forge-1.20.1",
    "base/fabric-1.20.1",
    "ext-ae/core",
    "ext-ae/common-1.20.1",
    "ext-ae/forge-1.20.1",
    "ext-ae/fabric-1.20.1",
    "ext-matrix/core",
    "ext-matrix/common-1.20.1",
    "ext-matrix/forge-1.20.1",
    "ext-matrix/fabric-1.20.1"
)

# Function to count lines in Java files
function Count-Lines {
    param([string]$Dir)
    
    if (-not (Test-Path $Dir)) {
        return 0
    }
    
    $javaFiles = Get-ChildItem -Path "$Dir\src\main\java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue
    if ($null -eq $javaFiles) {
        return 0
    }
    
    $totalLines = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName -ErrorAction SilentlyContinue
        if ($content) {
            $totalLines += $content.Count
        }
    }
    return $totalLines
}

# Function to count non-empty lines (excluding blank lines)
function Count-CodeLines {
    param([string]$Dir)
    
    if (-not (Test-Path $Dir)) {
        return 0
    }
    
    $javaFiles = Get-ChildItem -Path "$Dir\src\main\java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue
    if ($null -eq $javaFiles) {
        return 0
    }
    
    $totalLines = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName -ErrorAction SilentlyContinue
        if ($content) {
            $nonEmpty = $content | Where-Object { $_.Trim() -ne "" }
            $totalLines += $nonEmpty.Count
        }
    }
    return $totalLines
}

# Function to count classes (public/private/protected class/interface/enum/record)
function Count-Classes {
    param([string]$Dir)
    
    if (-not (Test-Path $Dir)) {
        return 0
    }
    
    $javaFiles = Get-ChildItem -Path "$Dir\src\main\java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue
    if ($null -eq $javaFiles) {
        return 0
    }
    
    $count = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName -ErrorAction SilentlyContinue
        if ($content) {
            # Match class, interface, enum, record declarations (with or without modifiers)
            # Pattern: [modifiers] [abstract/final] class/interface/enum/record [name]
            $matches = $content | Select-String -Pattern '^\s*((public|private|protected|static|abstract|final|sealed)\s+)*(class|interface|enum|record)\s+\w+' -AllMatches
            if ($matches) {
                # Filter out comments
                $validMatches = $matches | Where-Object { $_.Line.Trim() -notmatch '^\s*//' }
                $count += $validMatches.Count
            }
        }
    }
    return $count
}

# Function to count methods
function Count-Methods {
    param([string]$Dir)
    
    if (-not (Test-Path $Dir)) {
        return 0
    }
    
    $javaFiles = Get-ChildItem -Path "$Dir\src\main\java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue
    if ($null -eq $javaFiles) {
        return 0
    }
    
    $count = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName -ErrorAction SilentlyContinue
        if ($content) {
            # Match method declarations: [modifiers] [return type] [method name]([params]) [throws] { or ;
            $matches = $content | Select-String -Pattern '^\s*((public|private|protected|static|final|abstract|synchronized|native|default)\s+)*(void|[A-Za-z_][A-Za-z0-9_<>\[\],\s]+)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^)]*\)\s*(throws\s+[^{;]+)?\s*[;{]' -AllMatches
            if ($matches) {
                # Filter out comments
                $validMatches = $matches | Where-Object { $_.Line.Trim() -notmatch '^\s*//' }
                $count += $validMatches.Count
            }
        }
    }
    return $count
}

# Function to count files
function Count-Files {
    param([string]$Dir)
    
    if (-not (Test-Path $Dir)) {
        return 0
    }
    
    $javaFiles = Get-ChildItem -Path "$Dir\src\main\java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue
    if ($null -eq $javaFiles) {
        return 0
    }
    
    return $javaFiles.Count
}

Write-Host "========================================" -ForegroundColor Blue
Write-Host "MineAgent Code Statistics" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

# Table header
Write-Host ("{0,-28} {1,10} {2,10} {3,10} {4,10} {5,10}" -f "Module", "Files", "Total Lines", "Code Lines", "Classes", "Methods")
Write-Host ("-" * 80)

$TotalFiles = 0
$TotalLines = 0
$TotalCodeLines = 0
$TotalClasses = 0
$TotalMethods = 0

$results = @()

foreach ($subproject in $Subprojects) {
    if (-not (Test-Path $subproject)) {
        continue
    }
    
    $files = Count-Files $subproject
    $lines = Count-Lines $subproject
    $codeLines = Count-CodeLines $subproject
    $classes = Count-Classes $subproject
    $methods = Count-Methods $subproject
    
    Write-Host ("{0,-28} {1,10} {2,10} {3,10} {4,10} {5,10}" -f $subproject, $files, $lines, $codeLines, $classes, $methods)
    
    $TotalFiles += $files
    $TotalLines += $lines
    $TotalCodeLines += $codeLines
    $TotalClasses += $classes
    $TotalMethods += $methods
    
    $results += [PSCustomObject]@{
        Module = $subproject
        Files = $files
        Lines = $lines
        CodeLines = $codeLines
        Classes = $classes
        Methods = $methods
    }
}

Write-Host ("-" * 80)
Write-Host ("{0,-28} {1,10} {2,10} {3,10} {4,10} {5,10}" -f "TOTAL", $TotalFiles, $TotalLines, $TotalCodeLines, $TotalClasses, $TotalMethods) -ForegroundColor Green
Write-Host ""

# Additional statistics
Write-Host "Additional Statistics:" -ForegroundColor Yellow
Write-Host ""

# Calculate averages
if ($TotalFiles -gt 0) {
    $avgLinesPerFile = [math]::Round($TotalLines / $TotalFiles, 1)
    $avgCodeLinesPerFile = [math]::Round($TotalCodeLines / $TotalFiles, 1)
    $avgClassesPerFile = [math]::Round($TotalClasses / $TotalFiles, 1)
    $avgMethodsPerFile = [math]::Round($TotalMethods / $TotalFiles, 1)
    
    Write-Host "Average lines per file: " -NoNewline
    Write-Host $avgLinesPerFile -ForegroundColor Green
    Write-Host "Average code lines per file: " -NoNewline
    Write-Host $avgCodeLinesPerFile -ForegroundColor Green
    Write-Host "Average classes per file: " -NoNewline
    Write-Host $avgClassesPerFile -ForegroundColor Green
    Write-Host "Average methods per file: " -NoNewline
    Write-Host $avgMethodsPerFile -ForegroundColor Green
    Write-Host ""
}

# Calculate code density
if ($TotalLines -gt 0) {
    $codeDensity = [math]::Round(($TotalCodeLines * 100.0 / $TotalLines), 1)
    Write-Host "Code density (non-empty lines): " -NoNewline
    Write-Host "$codeDensity%" -ForegroundColor Green
    Write-Host ""
}

# Breakdown by subproject
Write-Host "Breakdown by Subproject:" -ForegroundColor Yellow
Write-Host ""

foreach ($result in $results) {
    if ($TotalLines -gt 0) {
        $percentage = [math]::Round(($result.Lines * 100.0 / $TotalLines), 1)
        Write-Host "$($result.Module):" -ForegroundColor Blue
        Write-Host "  Files: $($result.Files)"
        Write-Host "  Lines: $($result.Lines) ($percentage% of total)"
        Write-Host "  Code lines: $($result.CodeLines)"
        Write-Host "  Classes: $($result.Classes)"
        Write-Host "  Methods: $($result.Methods)"
        Write-Host ""
    }
}

Write-Host "Statistics calculation complete!" -ForegroundColor Green
