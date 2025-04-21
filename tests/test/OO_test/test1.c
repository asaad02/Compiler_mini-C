class HelloWorld {
    void sayHi() {
        print_s((char*)"Hello, World!\n");
    }
}

void main() {
    class HelloWorld hw;
    hw = new  class HelloWorld();
    hw.sayHi();
}
