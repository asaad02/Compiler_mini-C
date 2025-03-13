int a ;

void testDeepShadowing() {
    int a ;
    a= 10; // Shadows global
    {
        int a ;
        a = 20; // Shadows previous
        {
            int a ; 
            a = 30; // Shadows deeper
            print_s((char*)"\nLocal a should be 30: ");
            print_i(a); // Should print 30
            print_c('\n');
        }
        print_s((char*)"\nLocal a should be 20: ");
        print_i(a); // Should print 20
        print_c('\n');
    }
    print_s((char*)"\nLocal a should be 10: ");
    print_i(a); // Should print 10
    print_c('\n');
}

void main() {
    a = 5;
    testDeepShadowing();
    print_s((char*)"\nGlobal a should be 5: ");
    print_i(a); // Should print 5 (global)
    print_c('\n');
}
