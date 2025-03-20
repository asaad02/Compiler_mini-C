// array_tests.c

// System calls provided by your runtime:
int print_i(int x);
int print_c(int c);
int print_s(char *s);

//------------------------------
// Test 1: Single-Dimensional Array
//------------------------------
void testSingleArray() {
    int arr[5];
    int i;
    i = 0;
    // Initialize arr with even numbers
    while (i < 5) {
        arr[i] = i * 2;
        i = i + 1;
    }
    print_s("Single array test:\n");
    i = 0;
    while (i < 5) {
        print_i(arr[i]);
        print_c(' ');
        i = i + 1;
    }
    // should print: 0 2 4 6 8
    print_c('\n');
}

//------------------------------
// Test 2: Two-Dimensional Array
//------------------------------
void testMultiDimArray() {
    int mat[3][3];
    int i;
    int j ;
    i = 0;
    // Fill matrix with values: 11, 12, 13, 21, 22, 23, etc.
    while (i < 3) {
        j = 0;
        while (j < 3) {
            mat[i][j] = (i + 1) * 10 + (j + 1);
            j = j + 1;
        }
        i = i + 1;
    }
    print_s("Multi-dimensional array test:\n");
    i = 0;
    while (i < 3) {
        j = 0;
        while (j < 3) {
            print_i(mat[i][j]);
            print_c(' ');
            j = j + 1;
        }
        print_c('\n');
        i = i + 1;
    }
}

//------------------------------
// Test 3: Array inside a Struct
//------------------------------
struct S {
    int data[4];
};

void testStructArray() {
    struct S s;
    int i;
    i = 0;
    // Fill the struct's array with values starting at 100
    while (i < 4) {
        s.data[i] = 100 + i;
        i = i + 1;
    }
    print_s("Struct array test:\n");
    i = 0;
    while (i < 4) {
        print_i(s.data[i]);
        print_c(' ');
        i = i + 1;
    }
    // should print: 100 101 102 103
    print_c('\n');
}

//------------------------------
// Main: Run all tests
//------------------------------
void main() {
    testSingleArray();
    print_c('\n');
    testMultiDimArray();
    print_c('\n');
    testStructArray();
    print_s("------------------\n");
}
