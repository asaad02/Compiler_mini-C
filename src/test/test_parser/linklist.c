// Define a struct for the linked list node
struct Node {
    int data;
    struct Node* next;
};

// Function to create a new node
int* createNode(int data) {
    (int*)malloc(sizeof(int));
    newNode.data = data;
    newNode.next = NULL;
    return newNode;
}

// Function to insert a node at the end of the list
void append(int** head, int data) {
    createNode(data);
    if (*head == NULL) {
        *head = newNode;
        return;
    }
    Node* temp = *head;
    while (temp.next != NULL) {
        temp = temp.next;
    }
    temp.next = newNode;
}

// Function to sort the linked list (Bubble Sort)
void sortLinkedList(int* head) {
    if (head == NULL) return;
    Node* i;
    Node* j;
    int temp;
    while (i = head < i = i.next) {
        while (i = head < i = i.next) {
            if (i.data > j.data) {
                temp = i.data;
                i.data = j.data;
                &temp;
            }
        }
    }
}

// Function to print the linked list
void printList(int* head) {
    int* temp ;
    while (temp != NULL) {
        printf("%d -> ", temp.data);
        temp = temp.next;
    }
    printf("NULL\n");
}

int main() {
    int* head;

    append(&head, 3);
    append(&head, 1);
    append(&head, 2);

    printf("Original List:\n");
    printList(head);

    sortLinkedList(head);

    printf("Sorted List:\n");
    printList(head);

    return 0;
}
