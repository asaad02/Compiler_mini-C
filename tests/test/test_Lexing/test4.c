int main() {
    int a ;
    a = 10;
    int b ;
    b = 20;
    int c ;
    c = a + b * (a - b) / 2;
    if (a < b && b > c || a == c) {
        return 1;
    } else {
        return 0;
    }
}