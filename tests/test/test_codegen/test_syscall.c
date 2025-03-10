
void main() {
    // Test print_i
    print_i(42);
    print_c('\n');

    // Test print_c
    print_c('A');
    print_c('\n');

    // Test print_s
    print_s("Hello, World!\n");

    // Test read_i
    print_s("Enter an integer: ");
    int num ;
    num  = read_i();
    print_s("You entered: ");
    print_i(num);
    print_c('\n');

    // Test read_c
    print_s("Enter a character: ");
    char ch ;
    ch = read_c();
    print_s("You entered: ");
    print_c(ch);
    print_c('\n');

    // Test mcmalloc
    print_s("Allocating memory...\n");
    int* ptr ;
    ptr = mcmalloc(sizeof(int));
    *ptr = 42;
    print_s("Stored value: ");
    print_i(*ptr);
    print_c('\n');
}
