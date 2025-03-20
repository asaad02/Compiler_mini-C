// System call function declarations
void print_i(int num);
void print_s(char* str);

// Function prototypes
int add(int a, int b);
int return_42();
void test_struct();
void test_array();
void test_functions();
void test_branching();
void test_loops();
void test_arithmetic();
void test_comparison();
void test_logical();
void test_memory();
void test_standard_library();
void test_all();

// **Main Function**
void main() {
    test_all();
}

// **Comprehensive Test Function**
void test_all() {
    print_s("=== Comprehensive Test Start ===\n");
    test_arithmetic();
    test_comparison();
    test_logical();
    test_branching();
    test_loops();
    test_memory();
    test_struct();
    test_array();
    test_functions();
    test_standard_library();
    print_s("=== Comprehensive Test End ===\n");
}

// **1. Arithmetic Operators**
void test_arithmetic() {
    print_s("Test 1: Arithmetic Operators\n");
    
    int a;
    a = 5 + 3;
    print_i(a); print_s(" Expected: 8\n");

    int b;
    b = 10 - 2;
    print_i(b); print_s(" Expected: 8\n");

    int c;
    c = 4 * 2;
    print_i(c); print_s(" Expected: 8\n");

    int d;
    d = 16 / 2;
    print_i(d); print_s(" Expected: 8\n");

    int e;
    e = 17 % 3;
    print_i(e); print_s(" Expected: 2\n");
}

// **2. Comparison Operators**
void test_comparison() {
    print_s("Test 2: Comparison Operators\n");

    int a;
    a = (5 > 3);
    print_i(a); print_s(" Expected: 1\n");

    int b;
    b = (5 < 3);
    print_i(b); print_s(" Expected: 0\n");

    int c;
    c = (5 == 5);
    print_i(c); print_s(" Expected: 1\n");

    int d;
    d = (5 != 5);
    print_i(d); print_s(" Expected: 0\n");

    int e;
    e = (4 <= 4);
    print_i(e); print_s(" Expected: 1\n");

    int f;
    f = (5 >= 7);
    print_i(f); print_s(" Expected: 0\n");
}



// **4. Logical Operators**
void test_logical() {
    print_s("Test 4: Logical Operators\n");

    int a;
    a = (1 && 0);
    print_i(a); print_s(" Expected: 0\n");

    int b;
    b = (1 && 1);
    print_i(b); print_s(" Expected: 1\n");

    int c;
    c = (0 || 0);
    print_i(c); print_s(" Expected: 0\n");

    int d;
    d = (1 || 0);
    print_i(d); print_s(" Expected: 1\n");


}

// **5. Branching**
void test_branching() {
    print_s("Test 5: Branching\n");

    int x;
    x = 10;

    if (x > 5) {
        print_s("If Passed\n");
    } else {
        print_s("If Failed\n");
    }
}

// **6. Loops**
void test_loops() {
    print_s("Test 6: Loops\n");

    int i;
    i = 0;
    while (i < 3) {
        print_i(i); print_s(" ");
        i = i + 1;
    }
    print_s("\n");

}

// **7. Memory Allocation**
void test_memory() {
    print_s("Test 7: Memory Allocation\n");

    int a;
    a = 5;
    print_i(a); print_s(" Expected: 5\n");
}

// **8. Struct Accesses**
struct Point {
    int x;
    int y;
};
void test_struct() {
    print_s("Test 8: Struct Access\n");

    struct Point p;
    p.x = 3;
    p.y = 4;

    print_i(p.x); print_s(" Expected: 3\n");
    print_i(p.y); print_s(" Expected: 4\n");
}

// **9. Array Accesses**
void test_array() {
    print_s("Test 9: Array Access\n");

    int arr[3];
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;

    print_i(arr[0]); print_s(" Expected: 1\n");
    print_i(arr[1]); print_s(" Expected: 2\n");
    print_i(arr[2]); print_s(" Expected: 3\n");
}

// **10. Function Calls**
void test_functions() {
    print_s("Test 10: Function Calls\n");

    int result1;
    result1 = add(4, 5);
    print_i(result1); print_s(" Expected: 9\n");

    int result2;
    result2 = return_42();
    print_i(result2); print_s(" Expected: 42\n");
}

// **11. Standard Library Functions**
void test_standard_library() {
    print_s("Test 11: Standard Library Functions\n");
    print_s("Hello, world!\n");
}

// **Function Implementations**
int add(int a, int b) {
    return a + b;
}

int return_42() {
    return 42;
}
