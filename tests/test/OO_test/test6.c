class Box {
    int width;
    int height;

    void setSize(int w, int h) {
        width = w;
        height = h;
    }

    int area() {
        return width * height;
    }

    void printArea() {
        print_i(area());
        print_s((char*)"\n");
    }
}

void main() {
    Box b;
    b.setSize(3, 4);
    b.printArea();
}
