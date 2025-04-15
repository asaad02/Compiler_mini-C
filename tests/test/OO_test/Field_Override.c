class A {
    int x;
}

class B extends A {
    int x; // should return error cannot override field
}

int main() {
    return 0;
}
