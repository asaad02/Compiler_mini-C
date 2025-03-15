struct Point {
    int x;
    int y;
};


struct Point make_point(int x, int y) {
    struct Point p;
    p.x = x;
    p.y = y;
    return p;
}

void main() {
    struct Point p;
    p = make_point(10, 20);

    print_i(p.x);
    print_c('\n');
    print_i(p.y);
    print_c('\n');
}
