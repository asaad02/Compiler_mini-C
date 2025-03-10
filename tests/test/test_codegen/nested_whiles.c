
void main() {
    int i ;
    int j ;
    i = 0;
    j = 0;

    while (i < 3) {
        j = 0;
        while (j < 2) {
            print_i(i);
            print_c(' ');
            print_i(j);
            print_c('\n');
            j = j + 1; // ++j failing for some reason
        }
        i = i + 1; // ++i failing for some reason
    }
}
