void main() {
    print_i(1 && 0);   // Expected: 0
    print_c('\n');
    print_i(1 || 0);   // Expected: 1
    print_c('\n');
    
    print_i(5 == 5);   // Expected: 1
    print_c('\n');
    print_i(5 != 4);   // Expected: 1
    print_c('\n');
    print_i(3 < 7);    // Expected: 1
    print_c('\n');
    print_i(9 > 4);    // Expected: 1
    print_c('\n');

    print_i(1 + 2);   // Expected: 3
    print_c('\n');
    print_i(3 - 2);   // Expected: 1
    print_c('\n');  
    print_i(2 * 3);   // Expected: 6
}
