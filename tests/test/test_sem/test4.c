int main() {
    int x;
    x = 5;
    x = sum(4, 3); // Error: function 'sum' used before declaration
    return x;
}

int sum(int a, int b) {
    return a + b;
}
