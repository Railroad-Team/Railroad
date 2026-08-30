package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RailroadJavaStyleTest {
    @Test
    void removesBracesFromTerminalControlFlowBodies() {
        String before = """
            class Example {
                void run(boolean condition) {
                    if (condition) {
                        return;
                    } else {
                        throw new IllegalStateException();
                    }
                    while (condition) {
                        break;
                    }
                    do {
                        continue;
                    } while (condition);
                }
            }
            """;
        String after = """
            class Example {
                void run(boolean condition) {
                    if (condition)
                        return;
                    else
                        throw new IllegalStateException();
                    while (condition)
                        break;
                    do
                        continue;
                    while (condition);
                }
            }
            """;

        assertRewrite(before, after);
    }

    @Test
    void preservesBracesForNonTerminalAndMultiStatementBodies() {
        String source = """
            class Example {
                void run(boolean condition) {
                    if (condition) {
                        work();
                    }
                    if (!condition) {
                        work();
                        return;
                    }
                }

                void work() {
                }
            }
            """;

        assertRewrite(source, source);
    }

    @Test
    void addsBracesToNonTerminalControlFlowBodies() {
        assertRewrite("""
            class Example {
                void run(boolean condition) {
                    if (condition)
                        work();
                    else if (ready())
                        work();
                    else
                        work();
                    while (condition)
                        work();
                }

                boolean ready() {
                    return true;
                }

                void work() {
                }
            }
            """, """
            class Example {
                void run(boolean condition) {
                    if (condition)
                        {
            work();
            }
                    else if (ready())
                        {
            work();
            }
                    else
                        {
            work();
            }
                    while (condition)
                        {
            work();
            }
                }

                boolean ready() {
                    return true;
                }

                void work() {
                }
            }
            """);
    }

    @Test
    void preservesCommentsWhileRemovingOnlyTheOptionalBraces() {
        assertRewrite("""
            class Example {
                void run(boolean condition) {
                    if (condition) { // explain why
                        // keep this comment
                        return;
                    }
                }
            }
            """, """
            class Example {
                void run(boolean condition) {
                    if (condition) // explain why
                        // keep this comment
                        return;
                }
            }
            """);
    }

    @Test
    void usesVarForExactConstructedLocalTypes() {
        assertRewrite("""
            class Example {
                void run() {
                    Example value = new Example();
                    final Example finalValue = new Example();
                    Box<String> box = new Box<String>();
                    for (Example current = new Example(); ready(); current = new Example()) {
                        use(current);
                    }
                }

                boolean ready() {
                    return true;
                }

                void use(Example value) {
                }

                static class Box<T> {
                }
            }
            """, """
            class Example {
                void run() {
                    var value = new Example();
                    final var finalValue = new Example();
                    var box = new Box<String>();
                    for (var current = new Example(); ready(); current = new Example()) {
                        use(current);
                    }
                }

                boolean ready() {
                    return true;
                }

                void use(Example value) {
                }

                static class Box<T> {
                }
            }
            """);
    }

    @Test
    void preservesExplicitTypesWhenVarCouldChangeMeaningOrIsNotLocal() {
        String source = """
            class Example {
                Example field = new Example();

                void run(Example parameter) {
                    Parent interfaceType = new Child();
                    Box<String> diamond = new Box<>();
                    Example anonymous = new Example() { };
                    @Marker Example annotated = new Example();
                    Example fromMethod = create();
                    Example first = new Example(), second = new Example();
                }

                Example create() {
                    return new Example();
                }

                interface Parent {
                }

                static class Child implements Parent {
                }

                static class Box<T> {
                }

                @interface Marker {
                }
            }
            """;

        assertRewrite(source, source);
    }

    @Test
    void replacesConventionalUnusedNamesWithUnnamedVariables() {
        assertRewrite("""
            class Example {
                void run() {
                    Object ignored = value();
                    for (Object $ = value(); ready(); ) {
                        work();
                    }
                    for (Object $1 : values()) {
                        work();
                    }
                    try (Resource $2 = open()) {
                        work();
                    } catch (Exception ignored) {
                        work();
                    }
                    Consumer consumer = ignored -> work();
                    BiConsumer pair = ($, $1) -> work();
                }
            }
            """, """
            class Example {
                void run() {
                    Object _ = value();
                    for (Object _ = value(); ready(); ) {
                        work();
                    }
                    for (Object _ : values()) {
                        work();
                    }
                    try (Resource _ = open()) {
                        work();
                    } catch (Exception _) {
                        work();
                    }
                    Consumer consumer = _ -> work();
                    BiConsumer pair = (_, _) -> work();
                }
            }
            """);
    }

    @Test
    void preservesConventionalUnusedNamesWhenReferencedOrUnderscoreIsIllegal() {
        String source = """
            class Example {
                Object ignored = value();

                void run(Object ignored) {
                    Object $ = value();
                    use($);
                    for (Object $1 : values()) {
                        use($1);
                    }
                    try {
                        work();
                    } catch (Exception ignored) {
                        use(ignored);
                    }
                    Consumer consumer = ignored -> use(ignored);
                    Object ignored1 = value();
                    Object $name = value();
                    Object ignored = value(), $2 = value();
                }
            }
            """;

        assertRewrite(source, source);
    }

    private static void assertRewrite(String before, String after) {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
    }
}
