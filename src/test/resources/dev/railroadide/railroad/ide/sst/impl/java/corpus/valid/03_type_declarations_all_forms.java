package demo.types;

;

class Box {
}

interface Worker {
    void run();
}

enum Level {
    LOW,
    HIGH;
}

@interface Flag {
    String value() default "x";
}

record Pair(int left, int right) {
}
