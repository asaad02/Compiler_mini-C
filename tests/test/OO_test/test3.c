class Grade {
    int score;

    void setScore(int s) {
        score = s;
    }

    void passStatus() {
        if (score >= 50)
            print_s((char*)"Pass\n");
        else
            print_s((char*)"Fail\n");
    }
}

void main() {
    class Grade g;
    g = new class Grade();
    g.setScore(70);
    g.passStatus();
}
