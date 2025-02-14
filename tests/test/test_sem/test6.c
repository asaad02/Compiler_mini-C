int main() {
    int x;
    x = 5;
    {
        int x;
        x = 10; // This shadows the outer 'x'
    }
    return x; // Returns 5, outer 'x' remains unchanged
}
