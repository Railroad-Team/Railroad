package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCastConflictingWithInstanceofInspectionTest {
    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticForIncompatibleCastInPositiveBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForCompatibleSubtypeCast() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof CharSequence) {
                        String value = (String) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForNegatedInstanceofBranchInCurrentMvp() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (!(obj instanceof String)) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForSameTypeCast() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String) {
                        String value = (String) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForCompatibleSupertypeCast() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String) {
                        Object value = (Object) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForDifferentVariableCast() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, Object other) {
                    if (obj instanceof String) {
                        Integer value = (Integer) other;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForMethodCallExpressionInCurrentMvp() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                Object value() {
                    return "";
                }

                void run() {
                    if (value() instanceof String) {
                        Integer parsed = (Integer) value();
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideBlockThenBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String) {
                        Object x = 1;
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticForSingleStatementThenBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String)
                        consume((Integer) obj);
                }

                void consume(Integer value) {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticOutsideThenBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String) {
                        String s = (String) obj;
                    }
                    Integer value = (Integer) obj;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleHandlesPatternInstanceofSyntax() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (obj instanceof String s) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleHandlesParenthesizedCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if ((obj instanceof String)) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideElseBranchOfNegatedInstanceof() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (!(obj instanceof String)) {
                        return;
                    } else {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideWhileLoopBody() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    while (obj instanceof String) {
                        Integer value = (Integer) obj;
                        break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideForLoopBody() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    for (; obj instanceof String; ) {
                        Integer value = (Integer) obj;
                        break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticInsideDoWhileBodyBeforeCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    do {
                        Integer value = (Integer) obj;
                    } while (obj instanceof String);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForConflictingCastInNegatedThenBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj) {
                    if (!(obj instanceof String)) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideNestedIfWithinPositiveBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, boolean flag) {
                    if (obj instanceof String) {
                        if (flag) {
                            Integer value = (Integer) obj;
                        }
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticForInstanceofAndAdditionalCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, boolean flag) {
                    if (obj instanceof String && flag) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticForAdditionalConditionAndInstanceof() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, boolean flag) {
                    if (flag && obj instanceof String) {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleDoesNotEmitDiagnosticForCompatibleCastInCompoundCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, boolean flag) {
                    if (obj instanceof CharSequence && flag) {
                        String value = (String) obj;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())));
    }

    @Test
    void coreCastConflictingWithInstanceofRuleEmitsDiagnosticInsideElseBranchOfNegatedCompoundCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCastConflictingWithInstanceofInspection(), """
            class Example {
                void run(Object obj, boolean flag) {
                    if (!(obj instanceof String && flag)) {
                        return;
                    } else {
                        Integer value = (Integer) obj;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_CAST_CONFLICTING_WITH_INSTANCEOF".equals(d.code())
                && d.message().contains("Integer")
                && d.message().contains("String")));
    }


}
