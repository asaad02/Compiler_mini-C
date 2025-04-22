class Code {
    char tag[4];

    void setTag() {
        tag[0] = 'C';
        tag[1] = 'S';
        tag[2] = '1';
        tag[3] = '0';
    }

    void showTag() {
        int i;
        i = 0;
        while (i < 4) {
            if (tag[i] == '0') {
                break;
            }
            //print_c(tag[i]);
            i = i + 1;
        }
        print_s((char*)"\n");
    }
}

void main() {
    class Code c;
    c = new class Code();
    c.setTag();
    c.showTag();
}
