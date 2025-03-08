struct Point {
    int x;
    int y;
};

void main() {
    struct Point p;
    p.x = 4;
    p.y = 8;

    print_i(p.x);  // Expected: 4
    print_c('\n');
    print_i(p.y);  // Expected: 8
    print_c('\n');
}
