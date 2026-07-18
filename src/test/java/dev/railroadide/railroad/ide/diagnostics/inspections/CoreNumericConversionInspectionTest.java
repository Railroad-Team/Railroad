package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.*;

class CoreNumericConversionInspectionTest {
    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForParenthesizedAndNestedLongContexts() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long parenthesized = (0x8000_0000);

                long nested = 1 + (0x8000_0000);
            }
            """);

        long count = diagnostics.stream()
            .filter(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code()))
            .count();
        assertEquals(2L, count);
    }

    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForJdkMethodInvocationArgument() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long run() {
                    return Long.max(0x8000_0000, 1L);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForFieldInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long field = 0x8000_0000;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForArrayInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long[] values = { 0x8000_0000 };
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForConditionalArm() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long conditional(boolean flag) {
                    return flag ? 0x8000_0000 : 1L;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleEmitsDiagnosticForCastContext() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long casted() {
                    return (long) 0x8000_0000;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleDoesNotEmitForDecimalLongLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long value = 2147483648L;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleDoesNotEmitForHexLiteralWithLongSuffix() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long value = 0x8000_0000L;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleDoesNotEmitForNonNegativeHexInt() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long value = 0x7FFF_FFFF;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreNegativeHexIntInLongContextRuleDoesNotEmitForOutOfRangeHexLiteral() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNegativeHexIntInLongContextInspection(), """
            class Example {
                long value = 0x1_0000_0000;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT".equals(d.code())));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForVariableInitializerWidening() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    int source = 1;
                    long target = source;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("widening")
                && d.message().contains("'int'")
                && d.message().contains("'long'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForAssignmentWidening() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    int source = 1;
                    long target = 0L;
                    target = source;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("widening")
                && d.message().contains("'int'")
                && d.message().contains("'long'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForReturnWidening() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                long run() {
                    int value = 1;
                    return value;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("widening")
                && d.message().contains("'int'")
                && d.message().contains("'long'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForMethodInvocationArgument() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void accept(long value) {
                }

                void run() {
                    int value = 1;
                    accept(value);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("widening")
                && d.message().contains("'int'")
                && d.message().contains("'long'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForConstructorArgument() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                Example(long value) {
                }

                static Example create() {
                    int value = 1;
                    return new Example(value);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("widening")
                && d.message().contains("'int'")
                && d.message().contains("'long'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForMultipleMethodArguments() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void accept(long first, long second) {
                }

                void run() {
                    int first = 1;
                    int second = 2;
                    accept(first, second);
                }
            }
            """);

        long count = diagnostics.stream()
            .filter(d -> "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code()))
            .count();
        assertEquals(2L, count);
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForArithmeticCompoundAssignmentNarrowing() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    short value = 1;
                    value += 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("narrowing")
                && d.message().contains("'int'")
                && d.message().contains("'short'")));
    }

    @Test
    void coreImplicitNumericConversionRuleEmitsDiagnosticForShiftCompoundAssignmentNarrowing() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    short value = 1;
                    value <<= 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())
                && d.message().contains("narrowing")
                && d.message().contains("'int'")
                && d.message().contains("'short'")));
    }

    @Test
    void coreImplicitNumericConversionRuleDoesNotEmitForSameTypeAssignment() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    int source = 1;
                    int target = source;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())));
    }

    @Test
    void coreImplicitNumericConversionRuleDoesNotEmitForExplicitCast() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    int source = 1;
                    long target = (long) source;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())));
    }

    @Test
    void coreImplicitNumericConversionRuleDoesNotEmitForCompoundAssignmentWithoutNarrowing() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            class Example {
                void run() {
                    int value = 1;
                    value += 2;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())));
    }

    @Test
    void coreImplicitNumericConversionRuleDoesNotEmitForLambdaReturn() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImplicitNumericConversionInspection(), """
            interface Factory {
                long create();
            }

            class Example {
                Factory createFactory() {
                    return () -> {
                        int value = 1;
                        return value;
                    };
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_IMPLICIT_NUMERIC_CONVERSION".equals(d.code())));
    }


}
