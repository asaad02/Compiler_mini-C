#include "stdio.h"
#include "stdlib.h"


// Struct declaration (StructTypeDecl)
struct Node {
    int data;
    struct Node* next;
};

// Global variable declaration (VarDecl)
int a;
char c;
int arr[10];

// Function prototype (FunDecl)
void print(int x);
int add(int a, int b);
struct Node* createNode(int value);

// Function definition (FunDef)
void print(int x) {

}

// Function definition with return value
int add(int a, int b) {
    return a + b;
}

// Function to create a new node
struct Node* createNode(int value) {
    struct Node* newNode ;
    newNode = (struct Node*)mcmalloc(sizeof(struct Node));
    newNode.data = value;
    newNode.next = NULL;
    return newNode;
}

int main() {
    // Variable initialization (Assignment)
    a = 0;
    c = 'X';
    arr[2] = 5;

    // Binary operations (BinOp)
    int sum ;
    sum = a + arr[2] * 3 - 1;  // (a + (arr[2] * 3)) - 1

    // Logical operations
    int flag ;
    flag = (a == 0) && (arr[2] > 2);

    // Function call (FunCallExpr)
    print_s(sum);

    // While loop (While)
    int i ;
    i  = 0;
    while (i < 5) {
        print_s(i);
        i = i + 1;
    }

    // If statement (If)
    if (flag) {
        print_s(100);
    } else {
        print_s(-100);
    }

    // Continue and break (Continue, Break)
    int j ;
    j = 0;
    while (j < 10) {
        ++j;
        if (j == 5) continue;
        if (j == 8) break;
        print_s(j);
    }

    // Struct instance (StructTypeDecl, VarDecl)
    struct Node node1;
    node1.data = 10;
    node1.next = NULL;

    // Struct pointer operations (FieldAccessExpr)
    struct Node* node2 ;
    node2 = createNode(20);
    node1.next = node2;
    print_s(node1.next.data);

    // Pointer dereferencing (ValueAtExpr)
    *node2 = node1;

    // Address-of operator (AddressOfExpr)
    int* ptr ;
    ptr = &a;
    print_s(*ptr);

    // Sizeof operator (SizeOfExpr)
    print_s(sizeof(struct Node));

    // Typecasting (TypecastExpr)
    void* mem ;
    mem = mcmalloc(sizeof(int));
    int* intPtr ;
    intPtr = (int*)mem;
    *intPtr = 42;
    print_s(*intPtr);

    // Return statement (Return)
    return 0;
}
