struct Node {
    int data;
    struct Node* next;

    struct hello {
        int data;
        struct Node* next;

        struct hello {

            struct hello {

                struct Node* next;
                //a = 2;

            };
            int data;
            struct Node* next;

        };
    };
    
};

void printList(struct Node* head) {
    while (head != NULL) {
        printf("%d ", head.data);  // Pointer dereference
        head = head.next;
    }
}
