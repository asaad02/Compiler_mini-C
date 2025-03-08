int square(int x , int y) {
    print_i(x);
    print_c('\n');
    return x + y;
}

void main() {
    print_i(square(4 , 5));  // Expected: 9
    print_c('\n');
    print_i(square(5 , 7));  // Expected: 25
    print_c('\n');
}
