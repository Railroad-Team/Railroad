package demo.statements;

class StatementShowcase {
    int run(int seed, java.util.List<String> values) {
        int total = 0;

        outer:
        for (int i = 0; i < values.size(); i++) {
            if (i % 2 == 0) {
                continue;
            }

            while (seed > 0) {
                seed--;
                if (seed == 3) {
                    break;
                }
            }

            do {
                total += i;
            } while (false);

            switch (i) {
                case 0, 1 -> total += 1;
                case 2 -> {
                    total += 2;
                    break outer;
                }
                default -> total += 3;
            }
        }

        try (java.io.StringReader reader = new java.io.StringReader(values.toString())) {
            if (reader.read() < 0) {
                throw new IllegalStateException("no data");
            }
        } catch (java.io.IOException ex) {
            total -= 1;
        } finally {
            total += 10;
        }

        synchronized (this) {
            total += seed;
        }

        assert total >= 0 : "negative";

        if (values.isEmpty()) {
            return total;
        }

        return total + values.size();
    }
}
