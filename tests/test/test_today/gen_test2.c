// System call function declarations
void print_i(int num);
void print_c(char ch);
void print_s(char* str);
int read_i();
char read_c();
void* mcmalloc(int size);

// 1. Sorting a linked list
struct Node {
    int data;
    struct Node* next;
};
void test_sort_linked_list() {
    print_s("Test 1: Sort Linked List\n");
    struct Node n1;
    struct Node n2;
    struct Node n3;
    n1.data = 3; n1.next = &n2;
    n2.data = 1; n2.next = &n3;
    n3.data = 2; n3.next = 0;
    // Sorting logic here (not implemented)
    print_s("Expected: 1 2 3\n");
}

// 2. Multi-dimensional array function call
void test_funcall_multi_dim_array() {
    print_s("Test 2: Function Call with Multi-Dimensional Array\n");
    int arr[2][3] ;
    // while loop to initialize the array
    int i ;
    i = 0;
    while (i < 2) {
        int j ;
        j = 0;
        while (j < 3) {
            arr[i][j] = i * 3 + j;
            j = j + 1;
        }
        i = i + 1;
    }
    // while loop to print the array 
    i = 0;
    while (i < 2) {
        int j ;
        j = 0;
        while (j < 3) {
            print_i(arr[i][j]);
            j = j + 1;
        }
        i = i + 1;
    }
}

// 3. Array inside struct
struct Container {
    int values[3];
};
void test_array_in_struct() {
    print_s("Test 3: Array in Struct\n");
    struct Container c;
    c.values[0] = 10;
    print_i(c.values[0]); print_s(" Expected: 10\n");
}

// 4. Hello World Test
void test_hello_world() {
    print_s("Test 4: Hello World\n");
    print_s("Hello, World!\n");
}

// 5. Integer Literals Test
void test_int_literals() {
    print_s("Test 5: Integer Literals\n");
    print_i(42); print_s(" Expected: 42\n");
}

// 6. Nested While Loops
void test_nested_whiles() {
    print_s("Test 6: Nested While Loops\n");
    int i ;
    i = 0;
    int j ;
    while (i < 2) {
        j = 0;
        while (j < 2) {
            print_i(i); print_s(","); print_i(j); print_s(" ");
            j = j + 1;
        }
        i = i + 1;
    }
    print_s("Expected: 0,0 0,1 1,0 1,1\n");
}

// 7. Shadowing Test
void test_shadowing() {
    print_s("Test 7: Shadowing\n");
    int x ;
    x = 10;
    {
        int x ;
        x = 20;
        print_i(x); print_s(" Expected: 20\n");
    }
    print_i(x); print_s(" Expected: 10\n");
}

// 8. Arithmetic Operations Test
void test_arithmetics() {
    print_s("Test 8: Arithmetics\n");
    print_i(3 + 2 * 5); print_s(" Expected: 13\n");
}

// More tests...
int main() {
    test_sort_linked_list();
    test_funcall_multi_dim_array();
    test_array_in_struct();
    test_hello_world();
    test_int_literals();
    test_nested_whiles();
    test_shadowing();
    test_arithmetics();
    return 0;
}
