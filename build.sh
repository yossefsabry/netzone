#!/bin/bash

# NetAccess Kotlin Android App - Build Script
# This script builds the debug APK and displays the output path

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  NetAccess Kotlin Android - Build Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE="$PROJECT_DIR/.gradle-home/wrapper/dists/gradle-8.2-all/6mxqtxovn2faat1idb7p6lxsa/gradle-8.2/bin/gradle"
if [ ! -f "$GRADLE" ]; then
    echo -e "${YELLOW}Local Gradle not found. Attempting to use wrapper...${NC}"
    GRADLE="./gradlew"
fi

# Clean previous build
echo -e "${YELLOW}Cleaning previous build...${NC}"
$GRADLE clean --console=plain > /dev/null 2>&1 || true

# Build debug APK
echo -e "${YELLOW}Building debug APK...${NC}"
echo ""

$GRADLE assembleDebug --console=plain

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  BUILD SUCCESSFUL!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    # Get APK path
    APK_PATH="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    
    # Check if APK exists
    if [ -f "$APK_PATH" ]; then
        # Get APK size
        APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
        
        echo -e "${BLUE}APK Location:${NC}"
        echo -e "${GREEN}$APK_PATH${NC}"
        echo ""
        echo -e "${BLUE}APK Size:${NC} $APK_SIZE"
        echo ""
        echo -e "${BLUE}To install on emulator:${NC}"
        echo "adb install -r $APK_PATH"
        echo ""
        echo -e "${BLUE}To launch app:${NC}"
        echo "adb shell am start -n com.netaccess.app/.MainActivity"
    else
        echo -e "${YELLOW}Warning: APK file not found at expected location${NC}"
    fi
else
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}  BUILD FAILED${NC}"
    echo -e "${YELLOW}========================================${NC}"
    exit 1
fi
