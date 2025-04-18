class Code {
    char tag[4];

    void setTag() {
        tag[0] = 'C';
        tag[1] = 'S';
        tag[2] = '1';
        tag[3] = '0';
    }

    void showTag() {
        print_s(tag);
        print_s((char*)"\n");
    }
}

void main() {
    class Code c;
    c.setTag();
    c.showTag();
}
