class Box {
    int value;
    void set(int x) {
        value = x;
    }
    void show() {
        print_i(value);
        print_s((char*)"\n");
    }
}

void modify(class Box b) {
    b.set(99);
}

int main() {
    class Box myBox;
    myBox = new class Box();
    myBox.set(5);
    modify(myBox);
    myBox.show(); // should print 99
    return 0;
}
