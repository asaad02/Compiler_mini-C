int switchPlayer(int currentPlayer) {
    if (currentPlayer == 1) return 2;
    else return 1;
  }

void main(){
    int currentPlayer123;
    currentPlayer123 = 1;

    currentPlayer123 = switchPlayer(currentPlayer123);

    print_i(currentPlayer123); // Expected: 2
    print_c('\n');
} 