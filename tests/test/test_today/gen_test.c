// System call function declarations
void print_i(int num);
void print_c(char ch);
void print_s(char* str);
int read_i();
char read_c();
void* mcmalloc(int size);




// 2. Binary Operators
void test_binary_ops() {
    print_s("Test 2: Binary Operators\n");
    print_i(5 + 3); print_s(" Expected: 8\n");
    print_i(10 - 2); print_s(" Expected: 8\n");
    print_i(4 * 2); print_s(" Expected: 8\n");
    print_i(16 / 2); print_s(" Expected: 8\n");
}

// 3. Variable Allocations
void test_var_alloc() {
    int x;
    x = 42;
    print_s("Test 3: Variable Allocation\n");
    print_i(x); print_s(" Expected: 42\n");
}

// 4. Struct and Array Access
struct point {
    int x;
    int y;
};
void test_struct_array() {
    struct point p;
    p.x = 10;
    p.y = 20;
    print_s("Test 4: Structs and Arrays\n");
    print_i(p.x); print_s(" Expected: 10\n");
    print_i(p.y); print_s(" Expected: 20\n");
}

// 5. Branching Test
void test_branching() {
    int a;
    a = 5;
    print_s("Test 5: Branching\n");
    if (a > 3) {
        print_s("Branching Passed\n");
    } else {
        print_s("Branching Failed\n");
    }
}

// 6. Loop Test
void test_loops() {
    print_s("Test 6: Loop Test\n");
    int i ;
    i = 0 ;
    while (i < 3) {
        print_i(i); print_s(" ");
        if (i == 1) {
            i = i + 1;
            continue;
        }
        i = i + 1;
    }
    print_s("Expected: 0 1 2\n");
}

// 7. Function Calls
int square(int x) {
    return x * x;
}
void test_function_calls() {
    print_s("Test 7: Function Calls\n");
    print_i(square(4)); print_s(" Expected: 16\n");
}

// 8. Stack and Memory
void test_stack_memory() {
    print_s("Test 8: Stack and Memory\n");
    int* ptr ;
    ptr = (int*)mcmalloc(sizeof(int));
    *ptr = 99;
    print_i(*ptr); print_s(" Expected: 99\n");
}

// 9. Logical Operators
void test_logical_ops() {
    print_s("Test 9: Logical Operators\n");
    print_i(1 && 0); print_s(" Expected: 0\n");
    print_i(1 || 0); print_s(" Expected: 1\n");
}

// 10. Shadowing Test
void test_shadowing() {
    print_s("Test 10: Shadowing\n");
    int x ;
    x = 10;
    {
        int x ;
        x = 20;
        print_i(x); print_s(" Expected: 20\n");
    }
    print_i(x); print_s(" Expected: 10\n");
}

// More tests
void test_break() {
    print_s("Test 11: Break Statement\n");
    int i ;
    i = 0 ;
    while (i < 5) {
        if (i == 3) {
            print_s("Breaking loop\n");
            break;
        }
        print_i(i); print_s(" ");
        i = i + 1;
    }
    print_s("Expected: 0 1 2 Breaking loop\n");
}

// More tests...

int main() {
    test_binary_ops();
    test_var_alloc();
    test_struct_array();
    test_branching();
    test_loops();
    test_function_calls();
    test_stack_memory();
    test_logical_ops();
    test_shadowing();
    test_break();
    return 0;
}
