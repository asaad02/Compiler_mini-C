struct Point {
    int x;
    int y;
};

void print_point(struct Point p) {
    print_i(p.x);
    print_c('\n');
    print_i(p.y);
    print_c('\n');
}

void main() {
    struct Point p;
    p.x = 10;
    p.y = 20;
    

    print_point(p);  // Struct passed by value
}
