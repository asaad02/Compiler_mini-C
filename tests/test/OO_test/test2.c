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
        int i ;
        i = 0;
        while (i < 20) {
            if (name[i] == '0') {
                break;
            }
            i = i + 1;
        }
        print_s((char*)"\n");
    }
}

void main() {
    class Student s;
    s = new class Student();
    s.setName();
    s.greet();
}
