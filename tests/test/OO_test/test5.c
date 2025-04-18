class EvenCheck {
    int isEven(int x) {
        if (x % 2 == 0)
            return 1;
        else
            return 0;
    }

    void test() {
        if (isEven(6))
            print_s((char*)"Even\n");
        else
            print_s((char*)"Odd\n");
    }
}

void main() {
    class EvenCheck ec;
    ec.test();
}
