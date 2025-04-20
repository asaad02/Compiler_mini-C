class Exam {
    int score;

    void setScore(int s) {
        score = s;
    }

    int hasExam() {
        if (score == 100)
            return 0;
        return 1;
    }

    void printExamStatus() {
        int result ;
        result = hasExam();
        print_i(result);
        print_s((char*)"\n");
    }
}

void main() {
    class Exam e;
    e = new class Exam();
    e.setScore(100);
    e.printExamStatus();
}
