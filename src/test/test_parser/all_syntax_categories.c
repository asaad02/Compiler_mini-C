#include "stdio.h" 

// Struct declaration
struct Point {
    int x;
    int y;
};

// Function declaration
int add(int a, int b);

// Function definition
int add(int a, int b) {
    return a + b;
}

int main() {
    // Variable declarations
    a = 10;
    b = 20;
    c = a + b;  
    * ptr = &a;
    // value-at operator  
    val = *ptr;
    // array declaration and access
    int arr[5];
    // array access
    first = arr[0];

    // function call
    sum = add(a, b);  // Function call

    // struct declaration and access
    struct Point p1;
    p1.x = 100;
    p1.y = 200;
    // Struct field access
    x = p1.x;  // Struct field access

    // Pointer dereference and struct field access
    struct Point* pPtr;
    // Struct field access
    y = (*pPtr).y;

    // Relational and logical operators
    if (a > b) {
        printf("a is greater than b\n");
    } else {
        printf("b is greater than or equal to a\n");
    }

    // Arithmetic expressions with parentheses
    result = (a + b) * (c - val) / 2;  // Complex arithmetic expression

    // Type casting
    pi = 314159;
    intPi = (int)pi;  // Type casting

    // Sizeof operator
    size = sizeof(int);  // Sizeof operator

    // While loop
    int i;
    while (i < 5) {
        printf("Loop iteration: %d\n", i);
        ++i;
    }

    // If-else statement
    if (a > b) {
        printf("a is greater than b\n");
    } else {
        printf("b is greater than or equal to a\n");
    }

    // Return statement
    return 0;
}