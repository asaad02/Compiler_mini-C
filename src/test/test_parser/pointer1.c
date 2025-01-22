
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
    printf("Pointer Dereference: b = %d\n", b);

    // Struct Pointer Dereference Example
    //myStruct.value = 10;
    //myStruct.ptr = &myStruct.value;

    printf("Struct Pointer Dereference: *(myStruct.ptr) = %d\n", *(myStruct.ptr));

    // Double Reference Example
    int c ;
    int *ptrC ;
    int **ptrC2;
    int ***ptrC3;

    printf("Double Reference: ***ptrC3 = %d\n", ***ptrC3);

    // Type Casting Example
    malloc(sizeof(int)); // Allocate memory
    *(int *)genericPtr = 100;              // Cast void* to int* and assign a value

    printf("Type Casting: *(int *)genericPtr = %d\n", *(int *)genericPtr);

    free(genericPtr); // Free allocated memory

    return 0;
}
