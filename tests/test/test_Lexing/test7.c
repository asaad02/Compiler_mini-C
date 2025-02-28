int main() {
    int a ;
    a = 10;
    int b ;
    b = 20;
    int c ;
    c = a + b; // a +B
    int d ;
    d = a * b; // Valid expression
    int e ;
    e = a / 0;
    char c1 ;
    c1 = 'c';// Invalid character literal (unescaped single quote)
    char* str ;
    str  = "He said, \"Hi\""; // Invalid string literal unescaped double quote
    return 0;
}