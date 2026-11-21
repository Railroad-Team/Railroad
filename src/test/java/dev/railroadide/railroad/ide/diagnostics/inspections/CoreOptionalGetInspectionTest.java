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
        var diagnostics = runProvider(new CoreOptionalGetWithoutIsPresentCheckInspection(), source);
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
