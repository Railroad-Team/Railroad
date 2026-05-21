package demo.members;

class Members {
    static int global;
    int value = 1;

    static {
        global = 10;
    }

    {
        value += global;
    }

    Members() {
    }

    Members(int value) {
        this.value = value;
    }

    <T extends Number> int map(T number) {
        return number.intValue();
    }

    void withVarArgs(String label, int... numbers) {
        for (int number : numbers) {
            value += number;
        }
    }

    class Inner {
    }

    static class Nested {
    }

    interface Contract {
        void run();
    }

    enum Kind {
        A,
        B
    }

    record Pair(int left, int right) {
        Pair {
            if (left < 0) {
                left = 0;
            }
        }
    }
}
