#!/bin/bash

# Define colors for output
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
CYAN="\033[0;36m"
NC="\033[0m" # No Color

# Set paths
BUILD_DIR="bin"
SRC_DIR="."
TEST_CODEGEN_DIR="$SRC_DIR/tests/test/OO_test"
CODEGEN_OUTPUT_DIR="./description/part3"
MARS_JAR="./description/part3/Mars4_5.jar"

# Arrays to store test results
PASSED_TESTS=()
FAILED_TESTS=()
PASSED_SIMULATIONS=()
FAILED_SIMULATIONS=()

display_header() {
  echo -e "${CYAN}"
  echo "======================================"
  echo "      JAVA COMPILER TEST SUITE       "
  echo "======================================"
  echo -e "${NC}"
}

check_build_file() {
  if [ ! -f "build.xml" ]; then
    echo -e "${RED}Error: build.xml not found in the current directory.${NC}"
    exit 1
  fi
}

run_ant_build() {
  echo -e "${CYAN}Running Ant build...${NC}"
  ant clean build
  if [ $? -ne 0 ]; then
    echo -e "${RED}Ant build failed. Exiting.${NC}"
    exit 1
  fi
  echo -e "${GREEN}Ant build completed successfully.${NC}"
}

run_tests() {
  local dir=$1
  local mode=$2
  echo -e "${YELLOW}Running tests in directory: $dir (Mode: $mode)${NC}"
  
  for file in "$dir"/*.c; do
    if [ -f "$file" ]; then
      echo -e "${CYAN}Running test: $file${NC}"
      case $mode in
        "parser") java -cp "$BUILD_DIR" Main1 -parser "$file" ;;
        "lexer") java -cp "$BUILD_DIR" Main1 -lexer "$file" ;;
        "ast") java -cp "$BUILD_DIR" Main2 -ast "$file" ;;
        "sem") java -cp "$BUILD_DIR" Main2 -sem "$file" ;;
        "gen")
          output_file="$CODEGEN_OUTPUT_DIR/$(basename "$file" .c).ast"
          java -cp "$BUILD_DIR" Main4 -gen naive "$file" "$output_file"
          ;;
      esac
      
      if [ $? -ne 0 ]; then
        echo -e "${RED}Test failed for file: $file${NC}"
        FAILED_TESTS+=("$file")
      else
        echo -e "${GREEN}Test passed for file: $file${NC}"
        PASSED_TESTS+=("$file")
      fi
    fi
  done
}

run_mars_simulation() {
  echo -e "${YELLOW}Running MARS simulation on all .ast files...${NC}"
  for file in "$CODEGEN_OUTPUT_DIR"/*.ast; do
    if [ -f "$file" ]; then
      echo -e "${CYAN}Running simulation on file: $file${NC}"
      java -jar "$MARS_JAR" sm nc me "$file"
      if [ $? -ne 0 ]; then
        echo -e "${RED}Simulation failed for file: $file${NC}"
        FAILED_SIMULATIONS+=("$file")
      else
        echo -e "${GREEN}Simulation passed for file: $file${NC}"
        PASSED_SIMULATIONS+=("$file")
      fi
    fi
  done
  
  # Remove all .ast files after simulation
  #rm -f "$CODEGEN_OUTPUT_DIR"/*.ast
  #rm -f "$CODEGEN_OUTPUT_DIR"/*.asm
}

display_results() {
  echo -e "${CYAN}================= TEST RESULTS =================${NC}"
  echo -e "${GREEN}Passed Tests: ${#PASSED_TESTS[@]}${NC}"
  for file in "${PASSED_TESTS[@]}"; do
    echo -e "  - ${GREEN}$file${NC}"
  done

  echo -e "${RED}Failed Tests: ${#FAILED_TESTS[@]}${NC}"
  for file in "${FAILED_TESTS[@]}"; do
    echo -e "  - ${RED}$file${NC}"
  done

  echo -e "${CYAN}================= SIMULATION RESULTS =================${NC}"
  echo -e "${GREEN}Passed Simulations: ${#PASSED_SIMULATIONS[@]}${NC}"
  for file in "${PASSED_SIMULATIONS[@]}"; do
    echo -e "  - ${GREEN}$file${NC}"
  done

  echo -e "${RED}Failed Simulations: ${#FAILED_SIMULATIONS[@]}${NC}"
  for file in "${FAILED_SIMULATIONS[@]}"; do
    echo -e "  - ${RED}$file${NC}"
  done
  
  echo -e "${CYAN}====================================================${NC}"
}

main() {
  display_header
  check_build_file
  run_ant_build
  run_tests "$TEST_CODEGEN_DIR" "gen"
  run_mars_simulation
  display_results
}

main
