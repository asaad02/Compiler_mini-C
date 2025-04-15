class Course {
    int credit;

    void printCredits() {
        print_i(credit);
        print_s((char*)"\n");
    }
}

int main() {
    class Course c;
    c = new class Course();
    c.credit = 5;
    c.printCredits(); // will print 5
    return 0;
}
