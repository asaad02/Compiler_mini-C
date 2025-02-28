char c1 ; 
char c2 ;

char c3 ;

char c4 ;

char* str ;

char* invalidStr ;


int main() {
    c1 = 'a';
    c2 = 'a';    
    c3 = 'a'; // error 
    c4 = '"';  
    str = "Hello, \"world!\""; 
    invalidStr = "Unterminated string; // Invalid";
    return 0;
}
