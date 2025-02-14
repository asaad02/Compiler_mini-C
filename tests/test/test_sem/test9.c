int x; // Global variable

int main() {
    int x;
    x = 5; // Local variable shadows global 'x'
    return x; // Returns 5, not the global 'x'
}
