void printArray(int arr[10][10][10]) {
    int ii ;
    int jj;
    int k;

    ii = 0;
    while (ii < 3) {
        jj = 0;
        while (jj < 3) {
            print_c('(');
            print_i(ii);
            print_c(',');
            print_i(jj);
            print_c(')');
            print_c(':');

            k = 0;
            while (k < 3) {
                print_i(arr[ii][jj][k]);
                print_c(' ');
                k = k + 1;
            }

            print_c('\n');
            jj = jj + 1;
        }
        print_c('\n');
        ii = ii + 1;
    }

    print_s((char*)"------------------\n");
    print_c('\n');
}
void main() {
    int matrix[10][10][10];
    int i ;
    int j ;
    int k;

    i = 0;
    while (i < 3) {
        j = 0;
        while (j < 3) {
            k = 0;
            while (k < 3) {
                matrix[i][j][k] = 10 + k;  
                k = k + 1;
            }
            j = j + 1;
        }
        i = i + 1;
    }

    printArray(matrix);
}

/*
(0,0):10 11 12 
(0,1):10 11 12 
(0,2):10 11 12 

(1,0):10 11 12 
(1,1):10 11 12 
(1,2):10 11 12 

(2,0):10 11 12 
(2,1):10 11 12 
(2,2):10 11 12 

------------------*/