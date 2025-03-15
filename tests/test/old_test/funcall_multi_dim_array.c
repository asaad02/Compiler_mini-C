void printArray(int arr[2][3]) {
    int i;
    i = 0;
    int j;
        j = 0;
    while (i < 2) {
        while (j < 3) {
            print_i(arr[i][j]);
            print_c(' ');
            j = j + 1;
        }
        print_c('\n');
        i = i + 1;
    }
}

void main() {
    int matrix[2][3] ;
    int i;
    i = 0;
    while (i < 2) {
        int j;
        j = 0;
        while (j < 3) {
            matrix[i][j] = i * 10 + j;
            j = j + 1;
        }
        i = i + 1;
    }
    printArray(matrix);
}
