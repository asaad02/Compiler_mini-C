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
        int result = hasExam();
        print_i(result);
        print_s((char*)"\n");
    }
}

void main() {
    Exam e;
    e.setScore(100);
    e.printExamStatus();
}
