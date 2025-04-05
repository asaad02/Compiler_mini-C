#!/bin/bash

# Define colors for output
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
CYAN="\033[0;36m"
NC="\033[0m" 

# Directories
BUILD_DIR="bin"
SRC_DIR="."
TEST_CODEGEN_DIR="$SRC_DIR/tests/test/test_codegen"
CODEGEN_OUTPUT_DIR="./description/part3"
MARS_JAR="./description/part3/Mars4_5.jar"

# Arrays to store results
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
  echo -e "${YELLOW}Running code generation (-gen colour) on: $dir${NC}"

  # Create output dir if missing
  mkdir -p "$CODEGEN_OUTPUT_DIR"

  for file in "$dir"/*.c; do
    if [ -f "$file" ]; then
      echo -e "${CYAN}Running test: $file${NC}"
      local output_file="$CODEGEN_OUTPUT_DIR/$(basename "$file" .c).asm"

      java -cp "$BUILD_DIR" Main4 -gen colour "$file" "$output_file"
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

count_memory_accesses() {
  local file=$1
  grep -Ei '^[[:space:]]*(lw|sw)[[:space:]]' "$file" | wc -l
}

run_regalloc() {
  echo -e "${YELLOW}Running Register Allocation (-regalloc colour)...${NC}"

  for file in "$CODEGEN_OUTPUT_DIR"/*.asm; do
    if [ -f "$file" ]; then
      local output_file="${file%.asm}.regalloc.asm"
      echo -e "${CYAN}Register allocating: $file → $output_file${NC}"

      java -cp "$BUILD_DIR" Main4 -regalloc colour "$file" "$output_file"
      if [ $? -ne 0 ]; then
        echo -e "${RED}RegAlloc failed for: $file${NC}"
        FAILED_SIMULATIONS+=("$file (regalloc)")
      else
        echo -e "${GREEN}RegAlloc succeeded for: $file${NC}"
        PASSED_SIMULATIONS+=("$file (regalloc)")

        local mem_access_count
        mem_access_count=$(count_memory_accesses "$output_file")
        echo -e "${CYAN}Memory accesses in ${output_file}: $mem_access_count${NC}"
      fi
    fi
  done
}

simulate_file() {
  local file=$1
  local output
  output=$(java -jar "$MARS_JAR" sm nc me "$file" 2>&1)
  echo "$output"
  if echo "$output" | grep -q "^Error in"; then
    return 1
  else
    return 0
  fi
}

run_mars_simulation() {
  echo -e "${YELLOW}Running MARS simulation on .regalloc.asm files...${NC}"

  for file in "$CODEGEN_OUTPUT_DIR"/*.regalloc.asm; do
    if [ -f "$file" ]; then
      echo -e "${CYAN}Simulating: $file${NC}"
      if simulate_file "$file"; then
        echo -e "${GREEN}Simulation passed: $file${NC}"
        PASSED_SIMULATIONS+=("$file")
      else
        echo -e "${RED}Simulation failed: $file${NC}"
        FAILED_SIMULATIONS+=("$file")
      fi
    fi
  done
}

print_memory_accesses() {
  echo -e "${CYAN}======== Memory Access Summary ========${NC}"
  for file in "$CODEGEN_OUTPUT_DIR"/*.regalloc.asm; do
    if [ -f "$file" ]; then
      local count
      count=$(count_memory_accesses "$file")
      echo -e "${YELLOW}$(basename "$file"): $count memory accesses${NC}"
    fi
  done
  echo -e "${CYAN}=========================================${NC}"
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
  rm -f "$CODEGEN_OUTPUT_DIR"/*.asm "$CODEGEN_OUTPUT_DIR"/*.regalloc.asm
  display_header
  check_build_file
  run_ant_build
  run_tests "$TEST_CODEGEN_DIR"
  run_regalloc
  run_mars_simulation
  display_results
  print_memory_accesses

  echo -e "${YELLOW}Compiling TestGraphColouringRegAlloc.java...${NC}"
  echo -e "${YELLOW}Running TestGraphColouringRegAlloc...${NC}"
  #java -cp "$BUILD_DIR:lib/*:." TestGraphColouringRegAlloc
  #rm -f "$CODEGEN_OUTPUT_DIR"/*.asm "$CODEGEN_OUTPUT_DIR"/*.regalloc.asm
}

main
