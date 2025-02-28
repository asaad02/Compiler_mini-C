
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
    int first;
     a = 10;
     b = 20;
     c = a + b;  
    * ptr = &a;  
     val = *ptr;

    // Array declaration and access
     arr[5];
     first = arr[0];

     int sum;

    // Function call
    sum = add(a, b);  

    // Struct declaration and access
    struct Point p1;
    p1.x = 100;
    p1.y = 200;
    int x;
     x = p1.x;  // Struct field access
    int y;
    struct Point p2;
    // Pointer dereference and struct field access
    struct Point* pPtr;
    pPtr = &p1;
     x = (*pPtr).x;  // Struct field access
     y = (*pPtr).y;  // Struct field access


    int result;
    // Arithmetic expressions with parentheses
     result = (a + b) * (c - val) / 2;
    int sa;
    // Type casting
     sa = 213;
     int  intPi;
    char pi;
    // Type casting
     intPi = (int)pi; 
    int size;
    // Sizeof operator
    size = sizeof(int);  // Sizeof operator


    return 0;
}