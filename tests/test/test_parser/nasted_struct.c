// nasted struct

struct A {
    struct B {
        int a;
        int b;
    };
    int c;
};

int main() {
    struct A a;
    a.b.a = 1;
    a.b.b = 2;
    a.c = 3;
    return 0;
}