void main() {
    int arr[5];  
    arr[0] = 10;
    arr[1] = 5;
    arr[2] = arr[0] + arr[1];

    print_i(arr[2]);   // Expected: 15
    print_c('\n');

    int arr2[5][5];
    arr2[0][0] = 1;
    arr2[0][1] = 2;
    arr2[0][2] = 3;
    arr2[0][3] = 4;
    arr2[0][4] = 5;

    print_i(arr2[0][0]);   // Expected: 1
    print_c('\n');
    print_i(arr2[0][1]);   // Expected: 2
    print_c('\n');
    print_i(arr2[0][2]);   // Expected: 3
    print_c('\n');
    print_i(arr2[0][3]);   // Expected: 4
    print_c('\n');
    print_i(arr2[0][4]);   // Expected: 5
    print_c('\n');
}
