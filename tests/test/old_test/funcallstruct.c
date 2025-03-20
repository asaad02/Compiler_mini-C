struct Point {
    int x ;
    int y;
};

struct hi {
    int x;
    int y;
};


struct Point make_point(int x , int y , struct Point p) {
    x = x + 1;
    y = y + 1;
    p.x = x;
    p.y = y;
    return p;
}

// function to print the struct
void print_point(struct Point p) {
    print_i(p.x);
    print_c(' ');
    print_i(p.y);
    print_c('\n');
}

void main() {
    struct Point p;
    p = make_point(5 , 10 , p);

    // call the function to print the struct
    print_point(p);
}

