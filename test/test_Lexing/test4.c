int main() {
    int a = 10;
    int b = 20;
    int c = a + b * (a - b) / 2;
    if (a < b && b > c || a == c) {
        return 1;
    } else {
        return 0;
    }
}