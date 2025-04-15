class Math {
    int square(int x) {
        return x * x;
    }

    void test() {
        int result = square(5);
        print_i(result);
        print_s((char*)"\n");
    }
}

void main() {
    Math m;
    m.test();
}
