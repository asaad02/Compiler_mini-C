class Course {
    int courseWorkScore;

    int hasExam(){
        if(courseWorkScore == 100)
            return 0;
        else
            return 1;
    }
}

int main() {
    class Course c;
    c = new class Course();
    c.courseWorkScore = 90;
    if (c.hasExam()) {
        print_s((char*)"Has exam\n");
    } else {
        print_s((char*)"No exam\n");
    }
    return 0;
}
