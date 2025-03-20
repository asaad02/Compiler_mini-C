int binary_search(int arr[8], int size, int target) {
    int left;
    left = 0;
    int right;
    right = size - 1;
    int mid;

    while (left <= right) {
        mid = left + (right - left) / 2;

        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }
    return -1; // Not found
}

// **Main Function**
int main() {
    int arr[8]; 
    arr[0] = 1;
    arr[1] = 3;
    arr[2] = 5;
    arr[3] = 7;
    arr[4] = 9;
    arr[5] = 11;
    arr[6] = 13;
    arr[7] = 15;
    
    int size;
    size = 8;
    
    int target;
    
    print_s("Enter a number to search: ");
    target = read_i();

    
    int result;
    result = binary_search(arr, size, target);

    if (result != -1) {
        print_s("Element found at index: ");
        print_i(result); 
        print_s("\n");
    } else {
        print_s("Element not found.\n"); 
    }

    return 0;
}
