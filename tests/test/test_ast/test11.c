#include "stdio.h"

int main() {
    // Invalid assignments
    a = ;       // Missing right-hand value (error)
    b = 10 + ;  // Incomplete expression (error)
    c = (10 * );// Unfinished parenthesis (error)
    d = "hello"; // Invalid type assignment (string to int) (error)
    e = 5 / 0;  // Division by zero (runtime error, but should be caught by analyzer)
    f = 5 ** 2; // Invalid operator for exponentiation (error)
    g = 9 -;    // Incomplete expression (error)

    // Assigning to non-variable
    10 = a;         // Can't assign to a literal (error)
    "text" = d;     // Can't assign to a string literal (error)
    (a + b) = 20;   // Can't assign to an expression (error)

    // Misuse of operators in assignment
    h = ++;    // Prefix increment without variable (error)
    i = --;    // Prefix decrement without variable (error)
    j = = 5;   // Unexpected '=' at start (error)
    k += 5;    // Use of '+=' without initialization (error)
    l -= 3;    // Use of '-=' without initialization (error)


    // Using operators incorrectly
    int m = 5 *;  // Incomplete multiplication (error)
    int n = / 2;  // No left operand for division (error)


    return 0;
}
