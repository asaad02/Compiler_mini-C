struct array1
{
    int a[10];
    int b[10][10];
};


int main() {
    int x;
    int a[0][0];
    a[0][0] = 1;
    x = 5;
    x = x + 5;
    return x;
}

int array(int a[10]) {
    return a[0];
}