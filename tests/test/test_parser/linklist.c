// link list test

struct node {
    int data;
    struct node *next;
};

int main() {
    struct node *head ;
    head = NULL;
    struct node *current ;
    struct node *new_node ;
    current = head;
    new_node = (struct node *)mcmalloc(sizeof(struct node));
    new_node.data = 10;
    int i;

    while (i < 10) {
        struct node new_node;
        new_node.data = i;
        new_node.next = NULL;

    }

    return 0;
}

