class Compare {
    int a;
    int  b;

    void set(int x, int y) {
        a = x;
        b = y;
    }

    void bigger() {
        if (a > b)
            print_s((char*)"A is bigger\n");
        else
            print_s((char*)"B is bigger or equal\n");
    }
}

void main() {
    class Compare cmp;
    cmp.set(10, 5);
    cmp.bigger();
}
