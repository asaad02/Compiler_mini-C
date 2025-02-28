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
    int a;
    int b;
    int c;
    int* ptr;
    int val;
    int arr[10];

    a = 10;
    b = 20;
    c = a + b;  
    * ptr = &a;
    // value-at operator  
    val = *ptr;
    // array declaration and access
    int arr[5];
    int first;
    // array access
    first = arr[0];
    int sum;
    // function call
    sum = add(a, b);  // Function call

    // struct declaration and access
    struct Point p1;
    p1.x = 100;
    p1.y = 200;
    int x;
    // Struct field access
    x = p1.x;  // Struct field access

    // Pointer dereference and struct field access
    struct Point* pPtr;
    // Struct field access
    pPtr = &p1;
    x = (*pPtr).x;
    int y;
    y = (*pPtr).y;

    // Relational and logical operators
    int result;
    int pi;
    int intPi;

    // Arithmetic expressions with parentheses
    result = (a + b) * (c - val) / 2;  // Complex arithmetic expression

    // Type casting
    pi = 314159;
    intPi = (int)pi;  // Type casting
    int size;
    // Sizeof operator
    size = sizeof(int);  // Sizeof operator

    // While loop
    int i;
    while (i < 5) {

        ++i;
    }

    // If-else statement
    if (a > b) {
        a = b;
    } else {
        a = b;
    }

    // Return statement
    return 0;
}