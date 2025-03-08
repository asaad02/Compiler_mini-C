void main() {
    int i ;
    i = 0;
    while (i < 5) {
        print_i(i);  // Expected: 0 1 2 3 4
        print_c('\n');
        i = i + 1;
    }
}
