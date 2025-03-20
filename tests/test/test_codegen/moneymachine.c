// System call function declarations
void print_i(int num);
void print_c(char c);
int read_i();
char read_c();

// **Function to display the ATM menu**
void display_menu() {
    print_c('\n');
    print_c('=');
    print_c('=');
    print_c('=');
    print_c(' ');
    print_c('C');
    print_c('A');
    print_c('S');
    print_c('H');
    print_c(' ');
    print_c('M');
    print_c('A');
    print_c('C');
    print_c('H');
    print_c('I');
    print_c('N');
    print_c('E');
    print_c(' ');
    print_c('=');
    print_c('=');
    print_c('=');
    print_c('\n');

    print_i(1); print_c('.'); print_c(' '); print_c('D'); print_c('e'); print_c('p'); print_c('o'); print_c('s'); print_c('i'); print_c('t'); print_c('\n');
    print_i(2); print_c('.'); print_c(' '); print_c('W'); print_c('i'); print_c('t'); print_c('h'); print_c('d'); print_c('r'); print_c('a'); print_c('w'); print_c('\n');
    print_i(3); print_c('.'); print_c(' '); print_c('C'); print_c('h'); print_c('e'); print_c('c'); print_c('k'); print_c(' '); print_c('B'); print_c('a'); print_c('l'); print_c('a'); print_c('n'); print_c('c'); print_c('e'); print_c('\n');
    print_i(4); print_c('.'); print_c(' '); print_c('E'); print_c('x'); print_c('i'); print_c('t'); print_c('\n');
}

// **Main ATM Function**
void cash_machine() {
    int balance ;
    balance = 1000;
    int choice;
    int amount;

    while (1) {
        display_menu();
        choice = read_i();

        if (choice == 1) {
            print_c('E'); print_c('n'); print_c('t'); print_c('e'); print_c('r'); print_c(' '); print_c('a'); print_c('m'); print_c('o'); print_c('u'); print_c('n'); print_c('t'); print_c(':'); print_c(' ');
            amount = read_i();
            if (amount > 0) {
                balance = amount + balance ;
                print_c('N'); print_c('e'); print_c('w'); print_c(' '); print_c('b'); print_c('a'); print_c('l'); print_c('a'); print_c('n'); print_c('c'); print_c('e'); print_c(':'); print_c(' ');
                print_i(balance); print_c('\n');
            }
        } else if (choice == 2) {
            print_c('E'); print_c('n'); print_c('t'); print_c('e'); print_c('r'); print_c(' '); print_c('w'); print_c('i'); print_c('t'); print_c('h'); print_c('d'); print_c('r'); print_c('a'); print_c('w'); print_c('a'); print_c('l'); print_c(':'); print_c(' ');
            amount = read_i();
            if (amount > 0 && amount <= balance) {
                balance = balance - amount;
                print_c('N'); print_c('e'); print_c('w'); print_c(' '); print_c('b'); print_c('a'); print_c('l'); print_c('a'); print_c('n'); print_c('c'); print_c('e'); print_c(':'); print_c(' ');
                print_i(balance); print_c('\n');
            }
        } else if (choice == 3) {
            print_c('C'); print_c('u'); print_c('r'); print_c('r'); print_c('e'); print_c('n'); print_c('t'); print_c(' '); print_c('b'); print_c('a'); print_c('l'); print_c('a'); print_c('n'); print_c('c'); print_c('e'); print_c(':'); print_c(' ');
            print_i(balance); print_c('\n');
        } else if (choice == 4) {
            print_c('G'); print_c('o'); print_c('o'); print_c('d'); print_c('b'); print_c('y'); print_c('e'); print_c('!'); print_c('\n');
            break;
        }
    }
}

// **Main Function**
int main() {
    cash_machine();
    return 0;
}
