void pointer_tests() {
    int *p;
    int  i ;
    i = 10;
    p = &i;               // ✅ Should pass (Pointer assignment)

    *p = 20;              // ✅ Should pass (Dereferencing a pointer is valid)

    int *q;
    *q = 30;              // ❌ Should fail (Uninitialized pointer dereference)
}