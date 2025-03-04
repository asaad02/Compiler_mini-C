#include "stdio.h"
#include "stdlib.h"


struct Node {
    int data;
    struct Node* next;
};


int a;
char c;
int arr[10];


void print(int x);
int add(int a, int b);
struct Node* createNode(int value);


void print(int x) {

}

int add(int a, int b) {
    return a + b;
}


struct Node* createNode(int value) {
    struct Node* newNode ;
    newNode = (struct Node*)mcmalloc(sizeof(struct Node));
    newNode.data = value;
    newNode.next = NULL;
    return newNode;
}

int main() {

    a = 0;
    c = 'X';
    arr[2] = 5;


    int sum ;
    sum = a + arr[2] * 3 - 1;  


    int flag ;
    flag = (a == 0) && (arr[2] > 2);


    int i ;
    i  = 0;
    while (i < 5) {

        i = i + 1;
    }


    if (flag) {
        //print_s(100);
    } else {
        //print_s(-100);
    }


    int j ;
    j = 0;
    while (j < 10) {
        ++j;
        if (j == 5) continue;
        if (j == 8) break;
        //print_s(j);
    }


    struct Node node1;
    node1.data = 10;
    node1.next = NULL;

 
    struct Node* node2 ;
    node2 = createNode(20);
    node1.next = node2;
    //print_s(node1.next.data);


    *node2 = node1;


    int* ptr ;
    ptr = &a;
    //print_s(*ptr);


    //print_s(sizeof(struct Node));

   
    void* mem ;
    mem = mcmalloc(sizeof(int));
    int* intPtr ;
    intPtr = (int*)mem;
    *intPtr = 42;
    //print_s(*intPtr);


    return 0;
}
