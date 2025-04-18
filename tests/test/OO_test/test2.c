class Student {
    char name[20];

    void setName() {
        name[0] = 'A';
        name[1] = 'l';
        name[2] = 'i';
        name[3] = '0';
    }

    void greet() {
        print_s((char*)"Hello ");
        print_s(name);
        print_s((char*)"\n");
    }
}

void main() {
    class Student s;
    s.setName();
    s.greet();
}
