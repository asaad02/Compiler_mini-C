class Course {
    void whereToAttend(){
        print_s((char*)"In person or online\n");
    }
}

class VirtualCourse extends Course {
    void whereToAttend(){
        print_s((char*)"Online only\n");
    }
}

int main() {
    class Course c1;
    class Course c2;
    c1 = new class Course();
    c2 = (class Course) new class VirtualCourse();
    c1.whereToAttend();
    c2.whereToAttend();  // overridden method
    return 0;
}
