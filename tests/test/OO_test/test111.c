class Course {
    int courseWorkScore;

    void setScore(int score) {
        courseWorkScore = score;
    }

    void whereToAttend() {
        print_s((char*)"Not determined! The course will be held virtually or in person!\n");
    }

    int hasExam() {
        if(courseWorkScore == 100)
            return 0;
        else
            return 1;
    }

    void testAll() {
        whereToAttend();
        print_i(hasExam());
        print_s((char*)"\n");
    }
}

void main() {
    class Course c;
    c.setScore(80);
    c.testAll();
}
