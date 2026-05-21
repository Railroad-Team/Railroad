package demo.basics;

import java.util.List;
import static java.util.Collections.emptyList;

// unit-level comment should be preserved as trivia
class BasicUnit {
    private final List<String> values;

    BasicUnit(List<String> values) {
        this.values = values == null ? emptyList() : values;
    }

    int size() {
        return values.size();
    }
}
