#include "stdio.h" 


void checkValue(int x);

int main() { 
    x = 10;

    
    if (x > 5)  // if block
        printf("x is greater than 5.\n");
     else  // else block
        printf("x is not greater than 5.\n");
    

    
    if (x % 2 == 0) 
        if (x == 10) 
            printf("x is exactly 10.\n");
        else 
            printf("x is even but not 10.\n");
        
     else 
        printf("x is odd.\n");
     

    return 0; 
}


void checkValue(int x) {

    if (x == 42)
        printf("x is the answer.\n");
    else
        printf("x is not the answer.\n");
}
