class A {
    void hello() {
        print_s((char*)"Hello from A\n");
    }
}

class B extends A {
    void hello() {
        print_s((char*)"Hello from B\n");
    }
}

class C extends B {
    void hello() {
        print_s((char*)"Hello from C\n");
    }
}

int main() {
    class A x;
    x = (class A) new class C();
    x.hello(); // should call C
    return 0;
}
