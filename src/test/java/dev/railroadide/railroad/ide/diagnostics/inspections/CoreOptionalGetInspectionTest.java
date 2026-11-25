package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreOptionalGetInspectionTest {
    @Test
    public void ternaryArmsUseTheirOwnPresenceFacts() {
        assertWarnings("""
            class Example {
                String read(java.util.Optional<String> a, java.util.Optional<String> b, boolean ready) {
                    String first = a.isPresent() && ready ? a.get() : "";
                    String second = b.isEmpty() ? "" : b.get();
                    return a.isPresent() ? b.get() : a.get();
                }
            }
            """, List.of("b.get()", "a.get()"));
    }

    @Test
    public void onlyPresenceOfAFilteredValueProvesSourcePresence() {
        assertWarnings("""
            class Example {
                void read(java.util.Optional<String> a, java.util.Optional<String> b) {
                    if (a.filter(String::isEmpty).isEmpty()) return;
                    a.get();
                    b.get();
                }
                void emptyResult(java.util.Optional<String> a) {
                    if (a.filter(String::isEmpty).isPresent()) return;
                    a.get();
                }
                void fallback(java.util.Optional<String> a, java.util.Optional<String> b) {
                    if (a.or(() -> b).isEmpty()) return;
                    a.get();
                }
            }
            """, List.of("b.get()", "a.get()", "a.get()"));
    }

    @Test
    public void onlyAResolvedThrowingGuardEstablishesItsArgument() {
        assertWarnings("""
            class Example {
                private static void require(String message, boolean valid) {
                    if (!valid) { throw new IllegalStateException(message); }
                }
                static void require(boolean valid) { if (!valid) return; }
                void overridable(boolean valid) { if (!valid) throw new IllegalStateException(); }
                void read(java.util.Optional<String> a, java.util.Optional<String> b) {
                    require(a.isPresent());
                    a.get();
                    overridable(b.isPresent());
                    b.get();
                    require("missing", a.isPresent());
                    a.get();
                    b.get();
                    a = java.util.Optional.empty();
                    a.get();
                }
                void caught(java.util.Optional<String> a) {
                    try { require("missing", a.isPresent()); } catch (IllegalStateException ignored) {}
                    a.get();
                }
            }
            """, List.of("a.get()", "b.get()", "b.get()", "a.get()", "a.get()"));
    }

    @Test
    public void emptyChecksGuardShortCircuitOperandsAndFollowingStatements() {
        assertWarnings("""
            class Example {
                void read(java.util.Optional<String> opt, boolean repeat) {
                    if (opt.isEmpty() || opt.get().isBlank()) return;
                    opt.get();
                    if (repeat) { opt.get(); }
                }
                void loop(java.util.List<java.util.OptionalInt> values) {
                    for (java.util.OptionalInt opt : values) {
                        if (opt.isEmpty()) { System.out.println("skipped"); continue; }
                        opt.getAsInt();
                    }
                }
            }
            """, List.of());
    }

    @Test
    public void guardsRequireTheSameValueOnEveryPathToTheRead() {
        assertWarnings("""
            class Example {
                void read(java.util.Optional<String> first, java.util.Optional<String> other, boolean flag) {
                    if (first.isEmpty() && first.get().isBlank()) return;
                    if (first.isEmpty() || other.get().isBlank()) { if (flag) return; }
                    first.get();
                    if (first.isEmpty()) return;
                    first = java.util.Optional.empty();
                    first.get();
                }
                void loop(java.util.Optional<String> value, boolean repeat) {
                    if (value.isEmpty()) return;
                    while (repeat) { value.get(); value = java.util.Optional.empty(); }
                }
                void nested(java.util.Optional<String> opt, boolean flag) {
                    if (flag) { if (opt.isEmpty()) return; }
                    opt.get();
                }
                boolean eager(java.util.Optional<String> opt) { return opt.isEmpty() | opt.get().isBlank(); }
            }
            """, List.of("first.get()", "other.get()", "first.get()", "first.get()", "value.get()", "opt.get()",
            "opt.get()"));
    }

    private static void assertWarnings(String source, List<String> expected) {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(),
            source);
        assertEquals(expected, diagnostics.stream()
            .map(issue -> source.substring(issue.startOffset(), issue.endOffset()).strip()).toList());
    }

    @Test
    public void conditionalIfPresentReceiverDoesNotInterruptInspectionOfOtherGets() {
        String source = """
            class Example {
                String read(boolean flag, java.util.Optional<String> first, java.util.Optional<String> second) {
                    (flag ? first : second).ifPresent(value -> {});
                    if (first.isPresent()) {
                        return first.get();
                    }
                    return second.get();
                }
            }
            """;
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(),
            source);
        assertEquals(List.of("second.get()"), diagnostics.stream()
            .filter(issue -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(issue.code()))
            .map(issue -> source.substring(issue.startOffset(), issue.endOffset()).strip()).toList());
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticForPlainOptionalGet() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    return opt.get();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideIfPresentGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    if (opt.isPresent()) {
                        return opt.get();
                    }
                    return "fallback";
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideNegatedElseGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    if (!opt.isPresent()) {
                        return "fallback";
                    } else {
                        return opt.get();
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideDoubleNegationGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    if (!!opt.isPresent()) {
                        return opt.get();
                    }
                    return "fallback";
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideTripleNegationElseGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    if (!!!opt.isPresent()) {
                        return "fallback";
                    } else {
                        return opt.get();
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticWhenDifferentOptionalIsGuarded() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> left, java.util.Optional<String> right) {
                    if (left.isPresent()) {
                        return right.get();
                    }
                    return "fallback";
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticOutsideIfPresentGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                String run(java.util.Optional<String> opt) {
                    if (opt.isPresent()) {
                        System.out.println("present");
                    }
                    return opt.get();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideWhilePresentGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> opt) {
                    while (opt.isPresent()) {
                        System.out.println(opt.get());
                        break;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideForPresentGuard() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> opt) {
                    for (; opt.isPresent(); ) {
                        System.out.println(opt.get());
                        break;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticInDoWhileBody() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> opt) {
                    do {
                        System.out.println(opt.get());
                    } while (opt.isPresent());
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideIfPresentCallback() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> opt) {
                    opt.ifPresent(value -> {
                        System.out.println(opt.get());
                    });
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticForDifferentOptionalInsideIfPresentCallback() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> left, java.util.Optional<String> right) {
                    left.ifPresent(value -> {
                        System.out.println(right.get());
                    });
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }

    @Test
    public void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticOutsideIfPresentCallback() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), """
            class Example {
                void run(java.util.Optional<String> opt) {
                    opt.ifPresent(value -> {
                        System.out.println(value);
                    });
                    System.out.println(opt.get());
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK".equals(d.code())));
    }
}
