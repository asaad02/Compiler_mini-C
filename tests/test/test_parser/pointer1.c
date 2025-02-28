
struct MyStruct {
    int value;
    int *ptr;
};

int main() {
    // Pointer Pointer Example
    int a;
    int *ptr;
    int **ptr2;

    //printf("Pointer Pointer: **ptr2 = %d\n", **ptr2);

    int *ptrB;

    *ptrB = 50; // Dereference pointer to modify value
    print_s("Pointer Dereference: b = %d\n");

    // Struct Pointer Dereference Example
    //myStruct.value = 10;
    //myStruct.ptr = &myStruct.value;

    print_s("Struct Pointer Dereference: *(myStruct.ptr) = %d\n");

    // Double Reference Example
    int c ;
    int *ptrC ;
    int **ptrC2;
    int ***ptrC3;

    print_s("Double Reference: ***ptrC3 = %d\n");

    // Type Casting Example
    mcmalloc(sizeof(int)); // Allocate memory            

    print_s("Type Casting: *(int *)genericPtr = %d\n");


    return 0;
}
