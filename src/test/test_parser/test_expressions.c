int a ;
char c;
void testPointers();
void testPointers() {
    int *p;
    p = &a;
    *p = 42;
}