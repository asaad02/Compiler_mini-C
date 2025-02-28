struct Node {
    int data;
    struct Node* next;

    
};

void printList(struct Node* head) {
    int a ;
    a = 0 ;
    while ( a > 0) {
        print_s("%d ", head.data);  // Pointer dereference
        head = head.next;
    }
}
