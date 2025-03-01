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
TEST_SEMANTIC_DIR="$SRC_DIR/tests/test/old_"

# Function to display a header
display_header() {
  echo -e "${CYAN}"
  echo "======================================"
  echo "       JAVA TEST       please run lol" 
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
      if [[ "$mode" == "parser" ]]; then
        java -cp "$BUILD_DIR" Main1 -parser "$file"
      elif [[ "$mode" == "lexer" ]]; then
        java -cp "$BUILD_DIR" Main1 -lexer "$file"
      elif [[ "$mode" == "ast" ]]; then
        java -cp "$BUILD_DIR" Main2 -ast "$file"
      elif [[ "$mode" == "sem" ]]; then
        java -cp "$BUILD_DIR" Main2 -sem "$file"
      fi

      # Check if the test passed or failed
      if [ $? -ne 0 ]; then
        echo -e "${RED}Test failed for file: $file${NC}"
      else
        echo -e "${GREEN}Test passed for file: $file${NC}"
      fi
    fi
  done
}

# Function to display a completion message
display_completion_message() {
  echo -e "${GREEN}All tests completed.${NC}"
}

# Main script logic
main() {
  display_header
  check_build_file
  run_ant_build

  # Run parser tests
  #run_tests "$TEST_PARSER_DIR" "parser"

  #run_tests "$TEST_AST_DIR" "parser"

  #run_tests "$TEST_LEXER_DIR" "parser"

  #run_tests "$TEST_SEMANTIC_DIR" "parser"

  # Run lexer tests
  #run_tests "$TEST_LEXER_DIR" "lexer"
  
   # Run AST tests
   #run_tests "$TEST_PARSER_DIR" "ast"

   # Run semantic tests
    run_tests "$TEST_SEMANTIC_DIR" "sem"
    run_tests "$TEST_PARSER_DIR" "sem"
    run_tests "$TEST_AST_DIR" "sem"

    run_tests "$TEST_LEXER_DIR" "sem"

  #run_tests "$TEST_SEMANTIC_DIR" "sem"

  display_completion_message
}

# Run the main function
main
