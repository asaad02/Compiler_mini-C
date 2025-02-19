int sum(int a, int b);

int main() {
    int x;
    x = sum(1, 2); // Mismatched argument types
    return x;
}
int sum(int a, int b) {
    return a + b;
}