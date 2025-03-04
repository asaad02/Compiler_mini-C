
struct Node {
    int data;
    struct Node* next;
};


struct NodeWrapper {
     struct Node* node;
};

void insertSorted(struct Node** headRef, int value);

int main() {

 
    struct Node* head;
    head = NULL;
    insertSorted(&head, 2); 


    struct Node** headPtr;
    headPtr = &head;
    insertSorted(headPtr, 3);


    struct Node myNode;
    //insertSorted(&myNode, 4);

    struct Node** nullPtr ;
    nullPtr = NULL;
    insertSorted(nullPtr, 6);


    struct Node** ptrArray[5];
    ptrArray[0] = headPtr;
    (*(ptrArray[0])).data = 10;

    struct Node* nodePtr ;
    nodePtr = &myNode;
    nodePtr.next = (struct Node*)mcmalloc(sizeof(struct Node));
    nodePtr.next.data = 7;

    struct NodeWrapper wrapper;
    wrapper.node = nodePtr;
    wrapper.node.next.data = 8;



    return 0;
}

void insertSorted(struct Node** headRef, int value) {
    
}