package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreInfiniteRecursionInspectionTest {
    @Test
    public void coreInfiniteRecursionRuleEmitsDiagnosticForDirectRecursiveReturn() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                int run() {
                    return run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleEmitsDiagnosticForDirectRecursiveExpressionStatement() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                void run() {
                    run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleEmitsDiagnosticWhenBothIfBranchesRecurse() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                int run(boolean flag) {
                    if (flag) {
                        return run(flag);
                    } else {
                        return run(flag);
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleDoesNotEmitDiagnosticWhenBaseCaseReturns() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                int run(int n) {
                    if (n == 0) {
                        return 0;
                    }
                    return run(n - 1);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForLambdaContainedSelfCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                void run() {
                    Runnable action = () -> run();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForConditionalSingleBranchRecursion() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                void run(boolean flag) {
                    if (flag) {
                        run(flag);
                    }
                    System.out.println("done");
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

    @Test
    public void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForDifferentOverloadCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInfiniteRecursionInspection(), """
            class Example {
                int run() {
                    return run(1);
                }

                int run(int value) {
                    return value;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INFINITE_RECURSION".equals(d.code())));
    }

}
