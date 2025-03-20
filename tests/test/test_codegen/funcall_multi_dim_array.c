
void printArray(int arr[10][10]) {  // Fix: Explicit column size
    int ii ;
    int jj ;
    int c ;
    ii = 0;
    jj = 0;
    c = 0;
    
    while (ii < 3) {
        while (jj < 3) {
            print_c('(');
            print_i(ii);
            print_c(',');
            print_i(jj);
            print_c(')');
            print_c(':');
            print_i(arr[ii][jj]);  // Print stored value
            print_c(' ');
            //c = arr[i][j];  // Read back the value
            //print_i(c);  // Print retrieved value
            print_c(' ');
            jj = jj + 1;
            print_c('\n');
        }
        print_c('\n');
        jj = 0 ;
        ii = ii + 1;
    }

    print_s((char*)"------------------\n");

    //print_i(arr[0][0]);  // Print the last retrieved value
    print_c('\n');
}

void main() {
    int matrix[10][10]; 
    int i ;
    int c;
    int j ;
    i = 0;
    j = 0;
    c = 0;
    while (i < 3) {
        while (j < 3) {
            matrix[i][j] = 10 + i;  // Assign 10 explicitly
            c = matrix[i][j]; 
            print_c('(');
            print_i(i);
            print_c(',');
            print_i(j);
            print_c(')');
            print_c(':');
            print_i(c);
            print_c(' ');
            j = j + 1;
            // print j 
            //print_i(j);
            print_c('\n');
        }
        //print_s("\nloop\n");
        // print i
        //print_i(i);
        print_c('\n');
        j = 0;
        i = i + 1;
    }
    
    print_c('\n');
    printArray(matrix);  // Fix: Pass the array itself
}
