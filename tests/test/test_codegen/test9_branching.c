void main(){

    int a ;
    int b ;
    int c ;
    int d ;

    a = 1 ;
    b = 2 ;
    c = 3 ;
    d = 4 ;

    // if statement
    if (a == 1){
        a = 2 ;
        print_i(a);
        print_c('\n');
    }

    // if else statement
    if (b == 2){
        b = 3 ;
    } else {
        b = 4 ;
    }

    // nested if statement
    if (c == 3){
        if (d == 4){
            c = 4 ;
        }
    }

    // nested while and break inside if statement
    while (a < 4){
        print_i(a);
        print_c('\n');
        a = a + 1 ;
        if (b == 3){
            break ;
        }
    }
    
}