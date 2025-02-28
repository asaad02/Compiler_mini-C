#include "stdio.h" 


void checkValue(int x);

int main() { 
    int x ;
    x = 10;


    
    if (x > 5)  // if block
        x = 42;
     else  // else block
        x = 42;
    

    
    if (x % 2 == 0) 
        if (x == 10) 
            x = 42;
        else 
        x = 42;
        
     else 
     x = 42;
     

    return 0; 
}


void checkValue(int x) {

    if (x == 42)
    x = 42;
    else
    x = 42;
}
