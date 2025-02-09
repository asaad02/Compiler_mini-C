struct Point {
    int x;
    char label;
};

struct Rectangle {
    struct Point topLeft;
    struct Point bottomRight;
    char name[50];
};