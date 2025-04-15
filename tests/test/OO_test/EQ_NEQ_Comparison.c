class Dummy {}

int main() {
    class Dummy a;
    class Dummy b;
    class Dummy c;

    a = new class Dummy();
    b = new class Dummy();
    c = a;

    if (a == b) {
        print_s((char*)"a == b\n");
    } else {
        print_s((char*)"a != b\n");
    }

    if (a == c) {
        print_s((char*)"a == c\n");
    } else {
        print_s((char*)"a != c\n");
    }

    return 0;
}
