package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.*;

class CoreConstantConditionalExpressionInspectionTest {
    @Test
    void coreConstantConditionalExpressionRuleFlagsHardcodedIfLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    if (true) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsHardcodedWhileLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    while (false) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsHardcodedDoWhileLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    do {} while (false);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsHardcodedForLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    for (; false; ) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsHardcodedTernaryLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean x = true ? false : true;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsUnaryCompileTimeConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    if (!true) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsBinaryCompileTimeConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    if (true && false) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsNamedCompileTimeConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                static final boolean DEBUG = false;

                void m() {
                    if (DEBUG) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsLocalDataFlowConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean b = true;
                    if (b) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsBranchNarrowedThenCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m(boolean p) {
                    if (p) {
                        if (p) {}
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsBranchNarrowedElseCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m(boolean p) {
                    if (p) {
                    } else {
                        if (p) {}
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsAssignmentDrivenDataFlowConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean b = false;
                    b = true;
                    if (b) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleDoesNotLeakShadowedFactsAcrossScopes() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean b = true;
                    {
                        boolean b = false;
                    }
                }
            }
            """);

        long flowReports = diagnostics.stream()
            .filter(d -> "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code()))
            .count();
        assertEquals(0, flowReports);
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsSingleStatementBranchNarrowing() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m(boolean p) {
                    if (p)
                        if (p) {}
                    else
                        if (p) {}
                }
            }
            """);

        long flowReports = diagnostics.stream()
            .filter(d -> "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code()))
            .count();
        assertEquals(2, flowReports);
    }

    @Test
    void coreConstantConditionalExpressionRuleFlagsLoopConditionFromKnownFact() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean b = false;
                    while (b) {}
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleDoesNotPreserveLoopFactWhenVariableIsUpdated() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    boolean b = true;
                    for (; b; b = false) {
                        if (b) {}
                    }
                }
            }
            """);

        long flowReports = diagnostics.stream()
            .filter(d -> "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code()))
            .count();
        assertEquals(1, flowReports);
    }

    @Test
    void coreConstantConditionalExpressionRuleDoesNotTreatNonFinalNamedValueAsCompileTimeConstant() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                static boolean DEBUG = false;

                void m() {
                    if (DEBUG) {}
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT".equals(d.code())));
    }

    @Test
    void coreConstantConditionalExpressionRuleProducesExpectedMessagesForEachRuleKind() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                static final boolean DEBUG = false;

                void m(boolean p) {
                    if (true) {}
                    if (DEBUG) {}
                    if (p) {
                        if (p) {}
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())
                && d.message().contains("'if' condition is always 'true'")));
        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT".equals(d.code())
                && d.message().contains("'if' condition is always 'false'")));
        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT".equals(d.code())
                && d.message().contains("'p' is known to be 'true'")
                && d.message().contains("always 'true'")));
    }

    @Test
    void coreConstantConditionalExpressionRuleDoesNotDuplicateHardcodedAndCompileTimeReports() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    if (true) {}
                }
            }
            """);

        long reports = diagnostics.stream()
            .filter(d -> d.code().startsWith("SEM_CONSTANT_CONDITIONAL_EXPRESSION"))
            .count();
        assertEquals(1, reports);
    }

    @Test
    void coreConstantConditionalExpressionRuleIgnoresWhileTrueIdiom() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                void m() {
                    while (true) {
                        if (System.currentTimeMillis() > 0) break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().noneMatch(d -> "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL".equals(d.code())),
            "while(true) should be ignored as an intentional infinite loop idiom");
    }

    @Test
    void coreConstantConditionalExpressionRuleDoesNotFlagDynamicExpressions() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConstantConditionalExpressionInspection(), """
            class Example {
                boolean get() { return true; }
                void m(boolean p) {
                    if (p) {}
                    if (get()) {}

                    boolean changing = true;
                    changing = get();
                    if (changing) {}
                }
            }
            """);

        assertTrue(diagnostics.isEmpty(), "Dynamic conditions should not be flagged as constant");
    }


}
