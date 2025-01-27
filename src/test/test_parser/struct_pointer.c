struct Node {
    int data;
    struct Node* next;

    
};

void printList(struct Node* head) {
    while (head != NULL) {
        printf("%d ", head.data);  // Pointer dereference
        head = head.next;
    }
}
