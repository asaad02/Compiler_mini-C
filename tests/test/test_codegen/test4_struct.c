struct Node {
    int data;
    struct Node *next;
};

// Function to swap two node values
void swap(struct Node *a, struct Node *b) {
    int temp;
    temp = (*a).data;
    (*a).data = (*b).data;
    (*b).data = temp;
}

// Function to sort the linked list using Bubble Sort
void sort_linked_list(struct Node *head) {
    struct Node *i;
    struct Node *j;
    int swapped;

    if (head == 0 || (*head).next == 0) {
        // print 
        print_s("List is empty or has only one node\n");
        return; // No need to sort an empty or single-node list
    }

    swapped = 1;  //  loop starts

    while (swapped) {
        swapped = 0;
        i = head;

        while ((*i).next != 0) {  
            j = (*i).next;

            if ((*i).data > (*j).data) {
                swap(i, j);
                swapped = 1;  // Track if a swap was made
            }

            i = (*i).next;
        }
    }
}

// Function to print the linked list
void printList(struct Node *head) {
    struct Node *p;
    p = head;
    
    if (p == 0) {
        print_s("List is empty\n");
        return;
    }

    while (p != 0) {  
        print_i((*p).data);
        print_s(" ");  // Print space for formatting
        p = (*p).next;
    }
    print_s("\n");
}

int main() {
    struct Node n1;
    struct Node n2;
    struct Node n3;
    struct Node *head;

    // Initializing linked list
    n1.data = 9;
    n1.next = &n2;

    n2.data = 5;
    n2.next = &n3;

    n3.data = 7;
    n3.next = 0;  // End of the list

    head = &n1;

    // Print original list
    print_s("Original List:\n");
    printList(head);

    // Sort the list
    sort_linked_list(head);

    // Print sorted list
    print_s("Sorted List:\n");
    printList(head);

    return 0;
}
