
struct Data {
    int values[5];
};

void printStructArray(struct Data d) {
    int i;

    i = 0;
    while (i < 5) {
        print_i(d.values[i]);
        print_c(' ');
        i = i + 1;
    }
    print_c('\n');
}

void main() {
    struct Data myData;
    int i;
    i = 0;
    while (i < 5) {
        myData.values[i] = i * 10;
        i = i + 1;
    }
    printStructArray(myData);
}
