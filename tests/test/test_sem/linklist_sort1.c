
struct Node {
    int data;
    struct Node *next;
} ;

int main() {
    struct Node *head ;
    struct Node *current ;
    struct Node new_node ;
    int i;


    struct Node *p ; 
    *p = head;
    while (p != head) {
        printf("%d\n", p.data);
        p = p.next;
    }

    return 0;
}