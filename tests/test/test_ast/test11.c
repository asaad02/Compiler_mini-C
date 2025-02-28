#include "stdio.h"

int main() {

    int a;
    int b;
    int c;
    int d;
    int e;
    int f;
    int g;

    // Invalid assignments
    a = 10 ;       // Missing right-hand value (error)
    b = 10 + 11 ;  // Incomplete expression (error)
    c = (10 * 10 );// Unfinished parenthesis (error)
    d = "hello"; // Invalid type assignment (string to int) (error)
    e = 5 / 0;  // Division by zero (runtime error, but should be caught by analyzer)
    f = 5 * 2; // Invalid operator for exponentiation (error)
    g = 9 ;    // Incomplete expression (error)

    return 0;
}
