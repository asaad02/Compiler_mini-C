// Global variables
int g ;
char c ;

void printVars(int x) {
    print_s((char*)"\nGlobal g should be 10: ");
    print_i(g);  //  global g is accessed
    print_s((char*)"\nLocal x should be 5: "); 
    print_i(x);  // Local x
    print_s((char*)"\nGlobal c should be 'A': ");
    print_c(c);  //  global c is accessed
}


void testShadowing() {
    g = 20;  // Modify the global g instead of shadowing it
    c = 'B'; // Modify the global c instead of shadowing it

    print_s((char*)"\nGlobal g should be 20: ");
    print_i(g);  // Now prints 20 (global g)
    print_s((char*)"\nGlobal c should be 'B': ");
    print_c(c);  // Now prints 'B' (global c)

    if (1) {
        int g ; 
        g  = 30;  // Shadows global g *inside this block only*
        print_s((char*)"\nLocal g should be 30: ");
        print_i(g);  // Should print 30
    }

    print_s((char*)"\nGlobal g should still be 20: ");
    print_i(g);  // Still prints 20 (global g)
}


void testNestedScopes() {
    int x ;
    x = 5;
    printVars(x);  // Expected: 10 (global g), 5 (local x), 'A' (global c)

    if (x < 0) {
        int x ;
        x = 50;  // Shadows outer x
        printVars(x);  // Expected: 10 (global g), 50 (local x), 'A' (global c)
    }

    while (x < 10) {
        int temp_x ;
        temp_x = 100;  // Instead of shadowing x, use a temp variable
        printVars(temp_x);  // Expected: 10 (global g), 100 (local x), 'A' (global c)
        break;  // Exit loop without modifying outer x
    }

    x = x - 1;  // Only modify the original x
    printVars(x);  // Expected: 10 (global g), 4 (local x), 'A' (global c)
}

void main() {
    g = 10;
    c = 'A';
    print_s((char*)"\nGlobal g should be 10: ");
    print_i(g);
    print_s((char*)"\nGlobal c should be 'A': ");
    print_c(c);
    
    testShadowing();

    // Reset `g` and `c` before next function
    g = 10;  
    c = 'A';

    testNestedScopes();
}
