int switchPlayer(int currentPlayer) {
    if (currentPlayer == 1) return 2;
    else return 1;
  }

  // taking input and switching player

  int main() {
    int p ;
    p = 1;
    p = switchPlayer(p);
    print_i(p);  // Expect to see "2"
    return 0;
}