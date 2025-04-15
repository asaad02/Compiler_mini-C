class Course {
    int credit;
}

class Math extends Course {
    int difficulty;
}

int main() {
    class Math m;
    m = new class Math();
    m.credit = 3;
    m.difficulty = 2;
    print_i(m.credit); // inharitance 
    print_s((char*)" ");
    print_i(m.difficulty);
    print_s((char*)"\n");
    return 0;
}
