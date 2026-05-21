package dev.railroadide.railroad.ide.sst.impl.java;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class JavaParserFeatureParityTest {
    private static final Duration MAX_PARSE_TIME = Duration.ofSeconds(10);

    @TestFactory
    List<DynamicTest> targetedFeatureSnippetsParseWithoutRecovery() {
        return featureCases().stream()
                .map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
                    assertTimeoutPreemptively(
                            MAX_PARSE_TIME,
                            () -> JavaSyntaxAssertions.assertParsesWithoutRecovery(testCase.source()),
                            () -> "Timed out while parsing snippet: " + testCase.name()
                    );
                }))
                .toList();
    }

    private static List<FeatureCase> featureCases() {
        return List.of(
                new FeatureCase(
                        "basic module declaration",
                        """
                                module demo.main {
                                    requires java.base;
                                    exports demo.api;
                                }
                                """
                ),
                new FeatureCase(
                        "class and member access",
                        """
                                package demo;

                                class Box {
                                    private int value;

                                    int first() {
                                        return value;
                                    }
                                }
                                """
                ),
                new FeatureCase(
                        "enum declaration and constants",
                        """
                                package demo;

                                enum State {
                                    START,
                                    STOP;

                                    int code() {
                                        return ordinal();
                                    }
                                }
                                """
                ),
                new FeatureCase(
                        "switch expression with yield",
                        """
                                package demo;

                                class Switcher {
                                    int score(int value) {
                                        return switch (value) {
                                            case 0, 1 -> 1;
                                            case 2 -> {
                                                yield value + 2;
                                            }
                                            default -> value * 2;
                                        };
                                    }
                                }
                                """
                ),
                new FeatureCase(
                        "lambda expression forms",
                        """
                                package demo;

                                class Lambdas {
                                    void run() {
                                        java.util.function.IntUnaryOperator increment = n -> n + 1;
                                        Runnable task = () -> {
                                            int local = 1;
                                        };
                                    }
                                }
                                """
                ),
                new FeatureCase(
                        "casts arrays and ternary expression",
                        """
                                package demo;

                                class Expressions {
                                    int eval(Object value, int[] data) {
                                        int[][] matrix = new int[2][3];
                                        int first = (int) (data[0] + 1);
                                        int bonus = value instanceof String ? 1 : 0;
                                        return first + bonus;
                                    }
                                }
                                """
                )
        );
    }

    private record FeatureCase(String name, String source) {
    }
}
