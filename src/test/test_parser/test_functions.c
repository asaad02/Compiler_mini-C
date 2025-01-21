int add(int a, int b);

void printHello() {
    printf("Hello, World!\n");
}

int multiply(int a, int b) {
    return a * b;
}

void nestedExample(int x) {
    if (x > 0) {
        while (--x) {
            printf("Counting down: %d\n", x);
        }
    } else {
        return;
    }
}