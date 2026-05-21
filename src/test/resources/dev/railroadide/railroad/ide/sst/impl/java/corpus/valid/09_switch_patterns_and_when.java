package demo.switchpatterns;

record Point(int x, int y) {
}

class PatternSwitch {
    int classify(Object value) {
        return switch (value) {
            case null -> -1;
            case String s when s.isBlank() -> 0;
            case String s -> s.length();
            case Point(int x, int y) when x == y -> 10;
            case Point(int x, int y) -> x + y;
            default -> 1;
        };
    }
}
