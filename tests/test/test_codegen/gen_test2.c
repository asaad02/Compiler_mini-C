// System call function declarations
void print_i(int num);
void print_s(char* str);

// Function prototypes
int return_42();
int always_zero();
int always_one();

// **Comprehensive Test Function**
void test_all() {
    print_s("=== Comprehensive Test Start ===\n");

    // **1. Integer Literals**
    print_s("Test 1: Integer Literals\n");
    print_i(0); print_s(" Expected: 0\n");
    print_i(1); print_s(" Expected: 1\n");
    print_i(-1); print_s(" Expected: -1\n");
    print_i(42); print_s(" Expected: 42\n");
    print_i(100); print_s(" Expected: 100\n");

    // **2. Arithmetic Operators**
    print_s("Test 2: Arithmetic Operators\n");
    print_i(5 + 3); print_s(" Expected: 8\n");
    print_i(10 - 2); print_s(" Expected: 8\n");
    print_i(4 * 2); print_s(" Expected: 8\n");
    print_i(16 / 2); print_s(" Expected: 8\n");
    print_i(17 % 3); print_s(" Expected: 2\n");


    // **4. Logical Operators**
    print_s("Test 4: Logical Operators\n");
    print_i(1 && 0); print_s(" Expected: 0\n");
    print_i(1 && 1); print_s(" Expected: 1\n");
    print_i(0 || 0); print_s(" Expected: 0\n");
    print_i(1 || 0); print_s(" Expected: 1\n");


    // **5. Comparison Operators**
    print_s("Test 5: Comparison Operators\n");
    print_i(5 > 3); print_s(" Expected: 1\n");
    print_i(5 < 3); print_s(" Expected: 0\n");
    print_i(5 == 5); print_s(" Expected: 1\n");
    print_i(5 != 5); print_s(" Expected: 0\n");
    print_i(4 <= 4); print_s(" Expected: 1\n");
    print_i(5 >= 7); print_s(" Expected: 0\n");

    // **6. If-Else**
    print_s("Test 6: If-Else\n");
    int a ;
    a = 10;
    if (a > 5) {
        print_s("If Passed\n");
    } else {
        print_s("If Failed\n");
    }

    // **7. Nested If-Else**
    print_s("Test 7: Nested If-Else\n");
    int x ;
    x = 4;
    
    int y ;
    y = 5;
    if (x > 2) {
        if (y > 4) {
            print_s("Nested If Passed\n");
        } else {
            print_s("Nested If Failed\n");
        }
    }

    // **8. While Loop with Break**
    print_s("Test 8: While Loop with Break\n");
    int i ;
    i = 0;
    while (i < 5) {
        if (i == 3) {
            print_s("Breaking Loop\n");
            break;
        }
        print_i(i); print_s(" ");
        i = i + 1;
    }
    print_s("\n");

    // **9. While Loop with Continue**
    print_s("Test 9: While Loop with Continue\n");
    int j ;
    j = 0;
    while (j < 5) {
        j = j + 1;
        if (j == 3) {
            continue;
        }
        print_i(j); print_s(" ");
    }
    print_s("\n");

    // **10. Function Calls with Short-Circuiting**
    print_s("Test 10: Function Calls with Short-Circuit\n");
    int result1;
    int result2;

    result1 = (0 && always_zero()); // should NOT call always_zero()
    print_s("Result 1: (0 && always_zero()) = ");
    print_i(result1);
    print_s(" (Expected: 0, always_zero() should not be called)\n");

    result2 = (1 || always_one()); // should NOT call always_one()
    print_s("Result 2: (1 || always_one()) = ");
    print_i(result2);
    print_s(" (Expected: 1, always_one() should not be called)\n");

    // **11. Function Return Values**
    print_s("Test 11: Function Return Values\n");
    print_i(return_42()); print_s(" Expected: 42\n");

    print_s("=== Comprehensive Test End ===\n");
}

// **Function that returns 42**
int return_42() {
    return 42;
}

// **Function that returns 0**
int always_zero() {
    print_s("always_zero() called\n");
    return 0;
}

// **Function that returns 1**
int always_one() {
    print_s("always_one() called\n");
    return 1;
}

// **Main Function**
int main() {
    test_all();
    return 0;
}
