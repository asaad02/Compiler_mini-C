#include "stdio.h"
#include "stdlib.h"

// Variable Declaration
int a; 

// Struct Declaration
struct Node {
    int data;
    struct Node* next;
};

// Function Prototype
void insertSorted(struct Node** head, int value);
void printList(struct Node* head);

int main() {
    // Variable Initialization
    a = 0; 

    // Create an empty linked list
    struct Node* head ;
    head = NULL;

    // Insert elements into the sorted linked list
    insertSorted(&head, 5);
    insertSorted(&head, 2);
    insertSorted(&head, 8);
    insertSorted(&head, 1);

    // Print sorted linked list
    printList(head);

    return 0;
}

// Function Definition: Insert a node in sorted order
void insertSorted(struct Node** head, int value) {
    // Allocate memory for new node
    struct Node* newNode;
    newNode = (struct Node*)mcmalloc(sizeof(struct Node));
    newNode.data = value;
    newNode.next = NULL;

    // Handle empty list or insertion at the beginning

        newNode.next = *head;
        *head = newNode;


    // Find the correct position to insert
    struct Node* current ;
    current = *head;
    while (current.next != (struct Node*)NULL && current.next.data < value){

        current = current.next;
    }

    // Insert the new node
    newNode.next = current.next;
    current.next = newNode;
}

// Function Definition: Print the linked list
void printList(struct Node* head) {
    struct Node* temp ;
    temp = head;

    print_s("Sorted List: ");
    while (temp != NULL) {
        print_s("%d -> ", temp.data);
        temp = temp.next;
    }
    print_s("NULL\n");
}
