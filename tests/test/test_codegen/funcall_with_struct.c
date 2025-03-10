struct Point {
    int x;
    int y;
};

void printPoint(struct Point p) {
    print_i(p.x);
    print_c(' ');
    print_i(p.y);
    print_c('\n');
}

void main() {
    struct Point p;
    p.x = 10;
    p.y = 20;
    printPoint(p);
}
