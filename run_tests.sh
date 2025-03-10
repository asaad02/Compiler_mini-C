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
TEST_PARSER_DIR="$SRC_DIR/tests/test/test_parser"
TEST_LEXER_DIR="$SRC_DIR/tests/test/test_lexing"
TEST_AST_DIR="$SRC_DIR/tests/test/test_ast"
TEST_SEMANTIC_DIR="$SRC_DIR/tests/test/old_test"
TEST_CODEGEN_DIR="$SRC_DIR/tests/test/test_codegen"
CODEGEN_OUTPUT_DIR="./description/part3"
MARS_JAR="./description/part3/Mars4_5.jar"

# Function to display a header
display_header() {
  echo -e "${CYAN}"
  echo "======================================"
  echo "      JAVA COMPILER TEST SUITE       "
  echo "======================================"
  echo -e "${NC}"
}

# Check if Ant build file exists
check_build_file() {
  if [ ! -f "build.xml" ]; then
    echo -e "${RED}Error: build.xml not found in the current directory.${NC}"
    exit 1
  fi
}

# Run Ant build
run_ant_build() {
  echo -e "${CYAN}Running Ant build...${NC}"
  ant clean build
  if [ $? -ne 0 ]; then
    echo -e "${RED}Ant build failed. Exiting.${NC}"
    exit 1
  fi
  echo -e "${GREEN}Ant build completed successfully.${NC}"
}

# Function to run tests in a directory
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
          java -cp "$BUILD_DIR" Main3 -gen "$file" "$output_file"
          ;;
      esac

      if [ $? -ne 0 ]; then
        echo -e "${RED}Test failed for file: $file${NC}"
      else
        echo -e "${GREEN}Test passed for file: $file${NC}"
      fi
    fi
  done
}

# Run all .ast files through MARS simulator
run_mars_simulation() {
  echo -e "${YELLOW}Running MARS Simulator on all .ast files...${NC}"
  
  for ast_file in "$CODEGEN_OUTPUT_DIR"/*.ast; do
    if [ -f "$ast_file" ]; then
      echo -e "${CYAN}Processing file: $ast_file${NC}"
      java -jar "$MARS_JAR" sm nc  "$ast_file"

      if [ $? -ne 0 ]; then
        echo -e "${RED}MARS execution failed for: $ast_file${NC}"
      else
        echo -e "${GREEN}MARS execution completed successfully for: $ast_file${NC}"
      fi
    fi
  done
            # after running remove all ast files with ending .ast
        rm -f "$CODEGEN_OUTPUT_DIR"/*.ast
}

# Function to display a completion message
display_completion_message() {
  echo -e "${GREEN}All tests and simulations completed.${NC}"
}

# Main script logic
main() {
  display_header
  check_build_file
  run_ant_build

  # Run various test phases
  #run_tests "$TEST_PARSER_DIR" "parser"
  #run_tests "$TEST_LEXER_DIR" "lexer"
  #run_tests "$TEST_AST_DIR" "ast"
  #run_tests "$TEST_SEMANTIC_DIR" "sem"
  run_tests "$TEST_CODEGEN_DIR" "gen"

  # Run MARS on all .ast files
  run_mars_simulation

  display_completion_message
}

# Run the main function
main
