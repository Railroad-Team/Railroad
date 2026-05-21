package demo.labels;

class LabelShowcase {
    int execute(int limit) {
        int total = 0;

        outer:
        for (int i = 0; i < limit; i++) {
            inner:
            while (true) {
                total += i;
                if (i % 2 == 0) {
                    continue outer;
                }
                break inner;
            }

            total += 100;
        }

        return total;
    }
}
