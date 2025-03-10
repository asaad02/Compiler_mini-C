void hanoi(int n, char from, char to, char aux) {
    if (n == 1) {
        print_i(n);
        print_c(' ');
        print_c(from);
        print_c(' ');
        print_c(to);
        print_c('\n');
        return;
    }
    hanoi(n - 1, from, aux, to);
    print_i(n);
    print_c(' ');
    print_c(from);
    print_c(' ');
    print_c(to);
    print_c('\n');
    
    hanoi(n - 1, aux, to, from);
}

void main() {
    char A ;
    char B ;
    char C ;
    A = 'A';
    B = 'B';
    C = 'C';
    hanoi(3, A, C, B);
}
