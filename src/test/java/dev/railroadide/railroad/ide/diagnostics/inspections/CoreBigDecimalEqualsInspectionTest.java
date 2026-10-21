package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreBigDecimalEqualsInspectionTest {
    @Test
    public void coreBigDecimalEqualsRuleEmitsDiagnosticForBigDecimalEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(java.math.BigDecimal left, java.math.BigDecimal right) {
                    return left.equals(right);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleEmitsDiagnosticForImportedBigDecimalEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            import java.math.BigDecimal;

            class Example {
                boolean same(BigDecimal left, BigDecimal right) {
                    return left.equals(right);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleEmitsDiagnosticForBigDecimalLiteralEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same() {
                    return new java.math.BigDecimal("1.0").equals(new java.math.BigDecimal("1.00"));
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticForCompareToEqualityCheck() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(java.math.BigDecimal left, java.math.BigDecimal right) {
                    return left.compareTo(right) == 0;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticForNonBigDecimalEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(String left, String right) {
                    return left.equals(right);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleEmitsDiagnosticWhenArgumentIsBigDecimalSubtype() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class CustomBigDecimal extends java.math.BigDecimal {
                CustomBigDecimal(String value) {
                    super(value);
                }
            }

            class Example {
                boolean same(java.math.BigDecimal left, CustomBigDecimal right) {
                    return left.equals(right);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticWhenArgumentIsNotBigDecimal() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(java.math.BigDecimal value, Object other) {
                    return value.equals(other);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    public void coreBigDecimalEqualsRuleEmitsDiagnosticForParenthesizedBigDecimalEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(java.math.BigDecimal left, java.math.BigDecimal right) {
                    return (left).equals((right));
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

}
