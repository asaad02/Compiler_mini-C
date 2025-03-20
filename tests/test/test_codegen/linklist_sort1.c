struct Node {
    int data;
    struct Node *next;
};

void swap(struct Node *a, struct Node *b) {
    int temp;
    temp = (*a).data;  
    (*a).data = (*b).data;
    (*b).data = temp;
}

void sort_linked_list(struct Node *head) {
    struct Node *i;
    struct Node *j;

    i = head;
    while (i != 0) {  
        j = (*i).next;  
        while (j != 0) {
            if ((*i).data > (*j).data) {
                swap(i, j);
            }
            j = (*j).next;
        }
        i = (*i).next;
    }
}

void printList(struct Node *head) {
    struct Node *p;
    p = head;
    while (p != 0) {  
        print_i((*p).data);  
        p = (*p).next;
    }
}

int main() {
    struct Node n1;
    struct Node n2;
    struct Node n3;
    struct Node n4;
    struct Node n5;

    struct Node *head;
    
    n1.data = 4;
    n1.next = &n2;
    
    n2.data = 2;
    n2.next = &n3;
    
    n3.data = 8;
    n3.next = &n4;

    n4.data = 1;
    n4.next = &n5;

    n5.data = 3;
    n5.next = 0;

    head = &n1;

    print_s("Original List:\n");
    printList(head);

    sort_linked_list(head);

    print_s("Sorted List:\n");
    printList(head);

    return 0;
}
