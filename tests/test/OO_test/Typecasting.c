class A {
    int a;
    void foo() {
        print_s((char*)"A\n");
    }
}

class B extends A {
    int b;
    void foo() {
        print_s((char*)"B\n");
    }
}

int main() {
    class A obj;
    obj = (class A) new class B();
    obj.foo(); // should call B 

    // obj.b = 10; // should return b not accessible in A

    return 0;
}
