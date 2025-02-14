struct Point {
    int x;
    int y;
    int x; // Error: duplicate field 'x'
};

int main() {
    int x;
    x = 5;
    struct Point p;
    //struct Point p;
    return x;
}
