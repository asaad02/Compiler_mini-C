
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
     val = *ptr;

    // Array declaration and access
     arr[5];
     first = arr[0];

    // Function call
    sum = add(a, b);  

    // Struct declaration and access
    struct Point p1;
    p1.x = 100;
    p1.y = 200;
     x = p1.x;  // Struct field access

    // Pointer dereference and struct field access
     Point* pPtr = &p1;
     y = (*pPtr).y;  // Struct field access

    // Relational and logical operators
    if (a > b) {
        printf("a is greater than b\n");
    } else {
        printf("b is greater than or equal to a\n");
    }

    // Arithmetic expressions with parentheses
     result = (a + b) * (c - val) / 2;

    // Type casting
     sa = 213;
    // Type casting
     intPi = (int)pi; 

    // Sizeof operator
    size = sizeof(int);  // Sizeof operator

    // Output results
    printf("a = %d, b = %d, c = %d\n", a, b, c);
    printf("val = %d, first = %d, sum = %d\n", val, first, sum);
    printf("p1.x = %d, p1.y = %d\n", p1.x, p1.y);
    printf("result = %d, intPi = %d, size = %zu\n", result, intPi, size);

    return 0;
}