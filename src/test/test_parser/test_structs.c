struct Point {
    int x, y;
    char label;
};

struct Rectangle {
    struct Point topLeft;
    struct Point bottomRight;
    char name[50];
};