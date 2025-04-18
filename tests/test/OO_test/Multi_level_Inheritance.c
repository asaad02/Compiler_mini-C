class A {
    //int a;
    void hello(int i) {
        print_s((char*)"Hello from A\n");
    }
}

class B extends A {
    void hello(int i ) {

        print_s((char*)"Hello from B\n");
    }
}

class C extends B {
    void hello(int i) {
        print_s((char*)"Hello from C\n");
    }
}

int main() {
    class A x;
    x = (class A) new class C();
    int i ;
    i = 0;
    int b ;
    b = 0;
    i = (class t) x.hello(i);
    x.hello(i); // should call C
    //x.hello123(i);
    //x.a = 10; 
    return 0;
}
