class Math {
    int square(int x) {
        return x * x;
    }

    void test() {
        int result ;
        result = square(5);
        print_i(result);
        print_s((char*)"\n");
    }
}

void main() {
    class Math m;
    m = new class Math();
    m.test();
}
