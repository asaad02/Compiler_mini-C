int main() {
    int a ;
    int* ptr = &a;
    int** ptr2 = &ptr;
    printf("Value: %d\n", **ptr2); 
    return 0;
}