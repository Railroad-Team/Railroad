package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreOptionalGetInspectionTest {
    @Test
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticForPlainOptionalGet() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideIfPresentGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideNegatedElseGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideDoubleNegationGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideTripleNegationElseGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticWhenDifferentOptionalIsGuarded() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticOutsideIfPresentGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideWhilePresentGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideForPresentGuard() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticInDoWhileBody() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleDoesNotEmitDiagnosticInsideIfPresentCallback() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticForDifferentOptionalInsideIfPresentCallback() {
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
    void coreOptionalGetWithoutIsPresentCheckRuleEmitsDiagnosticOutsideIfPresentCallback() {
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
