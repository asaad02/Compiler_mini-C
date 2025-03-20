// dec2bin_test.c

// System call function declarations (provided by your runtime)
void print_s(char *s);
void print_i(int num);
int read_i(void);

// Declaration for dec2bin; it converts a decimal number into its binary representation
// and writes the result into bin (which must be large enough, e.g. 33 characters for 32-bit numbers).
void dec2bin(int dec, char *bin);

// Test function for dec2bin using a fixed set of test values
void test_dec2bin() {
    // Create an array of test decimal values
    int testValues[8];
    testValues[0] = 0;
    testValues[1] = 1;
    testValues[2] = 2;
    testValues[3] = 5;
    testValues[4] = 10;
    testValues[5] = 15;
    testValues[6] = 255;
    testValues[7] = 1023;

    int i = 0;
    while (i < 8) {
        char bin[33];  // Buffer for 32-bit binary representation plus null terminator
        dec2bin(testValues[i], bin);
        
        print_s("dec: ");
        print_i(testValues[i]);
        print_s(" => bin: ");
        print_s(bin);
        print_s("\n");
        
        i = i + 1;
    }
}

// Main function: calls the dec2bin test
int main() {
    print_s("Testing dec2bin:\n");
    test_dec2bin();
    return 0;
}
