package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreInspectionRulesTest {

    @Test
    void coreProvidersExposeExpectedRuleIds() {
        assertRuleIds(new CoreDuplicateDeclarationInspection(), Set.of("SEM_DUPLICATE_DECLARATION"));
        assertRuleIds(new CoreImportInspection(),
            Set.of("SEM_DUPLICATE_IMPORT", "SEM_AMBIGUOUS_IMPORT", "SEM_UNRESOLVED_IMPORT"));
        assertRuleIds(new CoreNameResolutionInspection(), Set.of("SEM_UNRESOLVED_NAME", "SEM_AMBIGUOUS_NAME"));
        assertRuleIds(new CoreTypeResolutionInspection(), Set.of("SEM_UNRESOLVED_TYPE"));
        assertRuleIds(new CoreMemberResolutionInspection(), Set.of("SEM_UNRESOLVED_MEMBER"));
        assertRuleIds(new CoreCallResolutionInspection(), Set.of("SEM_UNRESOLVED_CALL"));
        assertRuleIds(new CoreAccessibilityInspection(),
            Set.of("SEM_INACCESSIBLE_TYPE", "SEM_INACCESSIBLE_MEMBER", "SEM_INACCESSIBLE_CALL"));
        assertRuleIds(new CoreInheritanceInspection(), Set.of(
            "SEM_INVALID_INHERITANCE",
            "SEM_MISSING_IMPLEMENTATION",
            "SEM_INVALID_OVERRIDE",
            "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD",
            "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE"));
        assertRuleIds(new CoreModifierInspection(), Set.of("SEM_ILLEGAL_MODIFIER"));
        assertRuleIds(new CoreControlFlowInspection(), Set.of("SEM_INVALID_CONTROL_FLOW", "SEM_MISSING_RETURN"));
        assertRuleIds(new CoreExceptionInspection(), Set.of(
            "SEM_UNCAUGHT_CHECKED_EXCEPTION",
            "SEM_UNREACHABLE_CATCH",
            "SEM_INVALID_EXCEPTION_TYPE",
            "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE"));
        assertRuleIds(new CoreDefiniteAssignmentInspection(),
            Set.of("SEM_UNASSIGNED_VARIABLE", "SEM_ILLEGAL_FINAL_ASSIGNMENT", "SEM_UNINITIALIZED_FINAL_FIELD"));
        assertRuleIds(new CoreAssignmentInspection(), Set.of("SEM_INCOMPATIBLE_ASSIGNMENT"));
        assertRuleIds(new CoreImplicitNumericConversionInspection(), Set.of("SEM_IMPLICIT_NUMERIC_CONVERSION"));
        assertRuleIds(new CoreNegativeHexIntInLongContextInspection(), Set.of("SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT"));
        assertRuleIds(new CoreOverlyStrongTypeCastInspection(), Set.of("SEM_OVERLY_STRONG_TYPE_CAST"));
        assertRuleIds(new CoreCastConflictingWithInstanceofInspection(),
            Set.of("SEM_CAST_CONFLICTING_WITH_INSTANCEOF"));
        assertRuleIds(new CoreWildcardImportInspection(), Set.of("SEM_WILDCARD_IMPORT"));
        assertRuleIds(new CoreEmptyCatchInspection(), Set.of("SEM_EMPTY_CATCH"));
        assertRuleIds(new CorePublicClassNotNamedAfterFileInspection(),
            Set.of("SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE"));
        assertRuleIds(new CoreLowerCaseClassNameInspection(), Set.of("SEM_LOWERCASE_CLASS_NAME"));
        assertRuleIds(new CoreMethodNamedTODOInspection(), Set.of("SEM_METHOD_NAMED_TODO"));
        assertRuleIds(new CoreMethodNamedUnderscoreInspection(), Set.of("SEM_METHOD_NAMED_UNDERSCORE"));
        assertRuleIds(new CoreEmptySynchronizedInspection(), Set.of("SEM_EMPTY_SYNCHRONIZED"));
        assertRuleIds(new CoreEmptySwitchInspection(), Set.of("SEM_EMPTY_SWITCH"));
        assertRuleIds(new CoreUselessDefaultInSwitchInspection(), Set.of("SEM_USELESS_DEFAULT_IN_SWITCH"));
        assertRuleIds(new CoreFallthroughCaseInSwitchInspection(), Set.of("SEM_FALLTHROUGH_CASE_IN_SWITCH"));
        assertRuleIds(new CoreSingleLetterFieldNameInspection(), Set.of("SEM_SINGLE_LETTER_FIELD_NAME"));
        assertRuleIds(new CoreFieldNameSameAsClassInspection(), Set.of("SEM_FIELD_NAME_SAME_AS_CLASS_NAME"));
        assertRuleIds(new CoreParameterNamedUnderscoreInspection(), Set.of("SEM_PARAMETER_NAME_UNDERSCORE"));
        assertRuleIds(new CoreUnreachableCodeInspection(), Set.of("SEM_UNREACHABLE_CODE"));
        assertRuleIds(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            Set.of("SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT"));
        assertRuleIds(new CoreAssertionWithSideEffectsInspection(), Set.of("SEM_ASSERTION_WITH_SIDE_EFFECTS"));
        assertRuleIds(new CoreFeatureEnvyInspection(),
            Set.of("SEM_FEATURE_ENVY_MANIPULATE", "SEM_FEATURE_ENVY_TIGHTLY_COUPLED"));
        assertRuleIds(new CoreInitializationInspection(), Set.of(
            "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION",
            "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION"));
        assertRuleIds(new CoreThisReferenceEscapedObjectConstructionInspection(),
            Set.of("SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION"));
        assertRuleIds(new CoreFieldCanBeLocalVariableInspection(), Set.of("SEM_FIELD_CAN_BE_LOCAL_VARIABLE"));
        assertRuleIds(new CoreFunctionalInterfaceInspection(), Set.of("SEM_INTERFACE_SHOULD_BE_FUNCTIONAL"));
        assertRuleIds(new CoreOptionalGetWithoutIsPresentCheckInspection(),
            Set.of("SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK"));
        assertRuleIds(new CoreAutoCloseableWithoutTryWithResourcesInspection(),
            Set.of("SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES"));
        assertRuleIds(new CoreInfiniteRecursionInspection(), Set.of("SEM_INFINITE_RECURSION"));
        assertRuleIds(new CoreBigDecimalEqualsInspection(), Set.of("SEM_BIG_DECIMAL_EQUALS"));
        assertRuleIds(new CoreSerializableClassWithUnconstructableAncestorInspection(),
            Set.of("SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR"));
        assertRuleIds(new CoreRedundantInterfaceDeclarationInspection(), Set.of("SEM_REDUNDANT_INTERFACE_DECLARATION"));
    }

    @Test
    void coreNameRuleEmitsUnresolvedNameDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNameResolutionInspection(), """
            class Example {
                void run() {
                    missing = 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNRESOLVED_NAME".equals(d.code())));
    }

    @Test
    void coreNameRuleResolvesInheritedUnqualifiedFields() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreNameResolutionInspection(), """
            class Base {
                protected String dataKey;
                protected static String sharedKey;
            }

            class Child extends Base {
                String instanceValue() {
                    return dataKey + sharedKey;
                }

                static String staticValue() {
                    return sharedKey;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreImportRuleEmitsUnresolvedImportDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreImportInspection(), """
            import missing.pkg.Type;
            class Example {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNRESOLVED_IMPORT".equals(d.code())));
    }

    @Test
    void coreWildcardImportRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreWildcardImportInspection(), """
            import java.util.*;
            class Example {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_WILDCARD_IMPORT".equals(d.code())));
    }

    @Test
    void coreEmptyCatchRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreEmptyCatchInspection(), """
            class Example {
                void run() {
                    try {
                        work();
                    } catch (Exception exception) {
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_EMPTY_CATCH".equals(d.code())));
    }

    @Test
    void corePublicClassNotNamedAfterFileRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CorePublicClassNotNamedAfterFileInspection(),
            Path.of("Example.java"), """
                public class Wrong {
                }
                """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(
            d -> d.message().contains("Public class 'Wrong' must be declared in a file named 'Example.java'")));
    }

    @Test
    void coreLowerCaseClassNameRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreLowerCaseClassNameInspection(), """
            class example {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_LOWERCASE_CLASS_NAME".equals(d.code())));
    }

    @Test
    void coreMethodNamedTODORuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreMethodNamedTODOInspection(), """
            class Example {
                void TODO() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_METHOD_NAMED_TODO".equals(d.code())));
    }

    @Test
    void coreMethodNamedUnderscoreRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreMethodNamedUnderscoreInspection(), """
            class Example {
                void _() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_METHOD_NAMED_UNDERSCORE".equals(d.code())));
    }

    @Test
    void coreEmptySynchronizedRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreEmptySynchronizedInspection(), """
            class Example {
                void run() {
                    synchronized (this) {
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_EMPTY_SYNCHRONIZED".equals(d.code())));
    }

    @Test
    void coreEmptySwitchRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreEmptySwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_EMPTY_SWITCH".equals(d.code())));
    }

    @Test
    void coreUselessDefaultInSwitchRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUselessDefaultInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        default:
                            System.out.println(value);
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_USELESS_DEFAULT_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleEmitsDiagnosticForPlainFallthrough() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            System.out.println("one");
                        case 2:
                            System.out.println("two");
                            break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticForStackedLabels() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                        case 2:
                            System.out.println("grouped");
                            break;
                        default:
                            break;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticWhenCaseBreaks() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            System.out.println("one");
                            break;
                        case 2:
                            System.out.println("two");
                            break;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticWhenCaseReturns() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                int run(int value) {
                    switch (value) {
                        case 1:
                            return 1;
                        case 2:
                            return 2;
                        default:
                            return 0;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticWhenCaseThrows() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            throw new RuntimeException();
                        case 2:
                            System.out.println("two");
                            break;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticForArrowSwitchRules() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1 -> System.out.println("one");
                        case 2 -> System.out.println("two");
                        default -> System.out.println("default");
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleEmitsDiagnosticWhenOnlySomePathsBreak() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value, boolean stop) {
                    switch (value) {
                        case 1:
                            if (stop) {
                                break;
                            }
                            System.out.println("falls through");
                        case 2:
                            System.out.println("two");
                            break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleEmitsDiagnosticForFallthroughIntoDefault() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            System.out.println("one");
                        default:
                            System.out.println("default");
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreFallthroughCaseInSwitchRuleDoesNotEmitDiagnosticForLastCaseWithoutFollowingRule() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFallthroughCaseInSwitchInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            System.out.println("one");
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FALLTHROUGH_CASE_IN_SWITCH".equals(d.code())));
    }

    @Test
    void coreSingleLetterFieldNameRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSingleLetterFieldNameInspection(), """
            class Example {
                int x;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_SINGLE_LETTER_FIELD_NAME".equals(d.code())));
    }

    @Test
    void coreFieldNameSameAsClassRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldNameSameAsClassInspection(), """
            class Example {
                int Example;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_NAME_SAME_AS_CLASS_NAME".equals(d.code())));
    }

    @Test
    void coreParameterNamedUnderscoreRuleEmitsDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreParameterNamedUnderscoreInspection(), """
            class Example {
                void run(int _) {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_PARAMETER_NAME_UNDERSCORE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleEmitsDiagnosticAfterReturn() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run() {
                    return;
                    int value = 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleEmitsDiagnosticAfterThrow() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run() {
                    throw new RuntimeException();
                    int value = 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleEmitsDiagnosticWhenBothIfBranchesExit() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(boolean flag) {
                    if (flag) {
                        return;
                    } else {
                        throw new RuntimeException();
                    }
                    int value = 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleDoesNotEmitDiagnosticWhenOnlyOneIfBranchExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(boolean flag) {
                    if (flag) {
                        return;
                    }
                    int value = 1;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleDoesNotEmitDiagnosticAfterWhileLoopThatMayNotRun() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(boolean flag) {
                    while (flag) {
                        return;
                    }
                    int value = 1;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleDoesNotEmitDiagnosticAfterForLoopThatMayNotRun() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(boolean flag) {
                    for (; flag;) {
                        return;
                    }
                    int value = 1;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleEmitsDiagnosticAfterBreakInsideSwitchRule() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            break;
                            int dead = 1;
                        default:
                            break;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreUnreachableCodeRuleDoesNotEmitDiagnosticAfterSwitchStatement() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreUnreachableCodeInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1:
                            return;
                        default:
                            break;
                    }
                    int reachable = 1;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CODE".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForPublicMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    public void run(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticWhenMessagePresent() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    private void validate(int value) {
                        assert value > 0 : "value must be positive";
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForProtectedMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    protected void run(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForInterfaceMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                interface Example {
                    default void run(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForPublicConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    public Example(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleDoesNotEmitDiagnosticForPrivateHelperWithoutMessage() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    private void validate(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleDoesNotEmitDiagnosticForPackagePrivateMethodWithoutMessage() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(),
            """
                class Example {
                    void validate(int value) {
                        assert value > 0;
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionSideEffectRuleEmitsDiagnosticForAssignmentInCondition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionWithSideEffectsInspection(), """
            class Example {
                void run() {
                    int value = 0;
                    assert (value = 1) > 0;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_WITH_SIDE_EFFECTS".equals(d.code())));
    }

    @Test
    void coreAssertionSideEffectRuleEmitsDiagnosticForMutatingMethodCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionWithSideEffectsInspection(), """
            class Example {
                private int counter;

                private boolean mutate() {
                    counter++;
                    return true;
                }

                void run() {
                    assert mutate();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_WITH_SIDE_EFFECTS".equals(d.code())));
    }

    @Test
    void coreCallRuleEmitsUnresolvedCallDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), """
            class Example {
                void run() {
                    missing(1);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNRESOLVED_CALL".equals(d.code())));
    }

    @Test
    void coreMemberRuleEmitsUnresolvedMemberDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreMemberResolutionInspection(), """
            class Example {
                void run(String text) {
                    text.missingField;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNRESOLVED_MEMBER".equals(d.code())));
    }

    @Test
    void coreMemberRuleResolvesQualifiedEnclosingInstanceFields() {
        String source = """
            class Outer {
                private int value;

                class Inner {
                    int read() {
                        return Outer.this.value;
                    }
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        var context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> expressions = new ArrayList<>();
        context.traverse(node -> {
            if (node.kind().id().contains("EXPRESSION") && node.start() <= node.end()) {
                expressions.add(node.kind().id() + " " + source.substring(node.start(), node.end()) + " -> "
                    + context.resolvedSymbol(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreMemberResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_MEMBER".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", expressions));
    }

    @Test
    void coreAccessibilityRuleEmitsInaccessibleTypeDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAccessibilityInspection(), """
            class Owner {
                private static class Hidden {
                }
            }

            class Other {
                Owner.Hidden hidden;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INACCESSIBLE_TYPE".equals(d.code())));
    }

    @Test
    void coreAccessibilityRuleEmitsInaccessibleMemberAndCallDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAccessibilityInspection(), """
            class Secret {
                private int value;

                private Secret() {
                }

                private void ping() {
                }
            }

            class Other {
                void run(Secret secret) {
                    secret.value = 1;
                    secret.ping();
                    new Secret();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INACCESSIBLE_MEMBER".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INACCESSIBLE_CALL".equals(d.code())));
    }

    @Test
    void coreAccessibilityRuleAllowsPrivateAccessWithinTheSameTopLevelNest() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAccessibilityInspection(), """
            class Outer {
                static class Data {
                    private String label;
                    private Data() {}
                }

                String read(Data data) {
                    Data created = new Data();
                    return data.label + created.label;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_INACCESSIBLE_MEMBER".equals(diagnostic.code())
            || "SEM_INACCESSIBLE_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreAccessibilityRuleAllowsProtectedAnonymousConstructorsAndAccessibleOverloads() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAccessibilityInspection(), """
            class Example {
                Object visitor() {
                    return new java.nio.file.SimpleFileVisitor<>() {};
                }

                AssertionError error() {
                    return new AssertionError("message");
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_INACCESSIBLE_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreAccessibilityRuleEmitsProtectedCallDiagnosticOutsideSubclass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAccessibilityInspection(), """
            class Example {
                Object run(Thread thread) throws CloneNotSupportedException {
                    return thread.clone();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INACCESSIBLE_CALL".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInvalidInheritanceDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Worker {
            }

            class Base {
            }

            class Wrong extends Worker implements Base {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INVALID_INHERITANCE".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsMissingImplementationDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Worker {
                void run();
            }

            class MissingWorker implements Worker {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_MISSING_IMPLEMENTATION".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInvalidOverrideDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            class Parent {
                public final Object run() {
                    return "";
                }
            }

            class Child extends Parent {
                protected String run() {
                    return "";
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INVALID_OVERRIDE".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInvalidOverrideDiagnosticForBroaderCheckedException() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            class Parent {
                void run() throws java.io.IOException {
                }
            }

            class Child extends Parent {
                void run() throws Exception {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INVALID_OVERRIDE".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInvalidOverrideDiagnosticForConflictingInheritedMethods() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Left {
                Number value();
            }

            interface Right {
                String value();
            }

            class Example implements Left, Right {
            }
            """);

        assertTrue(diagnostics.stream()
            .anyMatch(d -> "SEM_INVALID_OVERRIDE".equals(d.code()) && d.message().contains("value")));
    }

    @Test
    void coreInheritanceRuleEmitsInterfaceObjectMethodClashDiagnosticForPrimitiveCloneReturnType() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface BadClone {
                double clone();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())
            && d.message().contains("clone()")));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitInterfaceObjectMethodClashDiagnosticForCovariantCloneReturnType() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface GoodClone {
                String clone();
            }
            """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInterfaceObjectMethodClashDiagnosticForNonVoidFinalize() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface BadFinalize {
                int finalize();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())
            && d.message().contains("finalize()")));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitInterfaceObjectMethodClashDiagnosticForVoidFinalize() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface GoodFinalize {
                void finalize();
            }
            """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())));
    }

    @Test
    void coreOverlyStrongTypeCastRuleEmitsDiagnosticWhenSupertypeMethodIsSufficient() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOverlyStrongTypeCastInspection(), """
            import java.util.ArrayList;

            class Example {
                void run(Object value) {
                    ((ArrayList<?>) value).size();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERLY_STRONG_TYPE_CAST".equals(d.code())
            && d.message().contains("ArrayList")
            && d.message().contains("List")));
    }

    @Test
    void coreOverlyStrongTypeCastRuleDoesNotEmitDiagnosticWhenSubtypeMethodIsRequired() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreOverlyStrongTypeCastInspection(), """
            import java.util.ArrayList;

            class Example {
                void run(Object value) {
                    ((ArrayList<?>) value).trimToSize();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERLY_STRONG_TYPE_CAST".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsPublicMethodNotExposedByInterfaceDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Worker {
                void run();
            }

            class DefaultWorker implements Worker {
                public void run() {
                }

                public void reset() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())
            && d.message().contains("reset()")));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitPublicMethodNotExposedByInterfaceDiagnosticForInterfaceMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Worker {
                void run();
                void reset();
            }

            class DefaultWorker implements Worker {
                public void run() {
                }

                public void reset() {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitPublicMethodNotExposedByInterfaceDiagnosticForClassWithNonObjectSuperclass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            class BaseWorker {
                public void reset() {
                }
            }

            interface Worker {
                void run();
            }

            class DefaultWorker extends BaseWorker implements Worker {
                public void run() {
                }

                public void extra() {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())));
    }

    @Test
    void coreModifierRuleEmitsIllegalTypeAndFieldModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            private static class Example {
                final volatile int value = 1;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_MODIFIER".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("top-level type declarations")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'final' cannot be combined with 'volatile'")));
    }

    @Test
    void coreModifierRuleEmitsIllegalMethodModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            class Example {
                abstract final void broken();

                default void alsoBroken() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_MODIFIER".equals(d.code())));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'abstract' cannot be combined with 'final'")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'default' is only allowed on interface methods")));
    }

    @Test
    void coreModifierRuleEmitsIllegalConstructorModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            class Example {
                static Example() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_MODIFIER".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'static' is not allowed on constructors")));
    }

    @Test
    void coreModifierRuleEmitsDuplicateModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            public public class Example {
                void run(final final int value) {
                    final final int local = value;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("duplicate modifier 'public'")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("duplicate modifier 'final'")));
    }

    @Test
    void coreModifierRuleEmitsInterfaceAnnotationAndEnumEdgeCaseDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            interface Example {
                private int VALUE = 1;
                protected void run();
                final void stop();
                static void util();
            }

            @interface Flag {
                private int value();
            }

            enum Mode {
                ON;

                public Mode() {
                }
            }
            """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'private' is not allowed on interface fields")));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("'protected' is not allowed on interface methods")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'final' is not allowed on interface methods")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'static' interface methods must have a body")));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("'private' is not allowed on annotation type elements")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on enum constructors")));
    }

    @Test
    void coreModifierRuleEmitsLocalParameterAndRecordComponentModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            record Example(public final int value) {
                void run(public final final int input) {
                    public int local = input;
                }
            }
            """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on record components")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'final' is not allowed on record components")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on parameters")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("duplicate modifier 'final'")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on local variables")));
    }

    @Test
    void coreModifierRuleHandlesNestedTypesConcreteAbstractMethodsAndAnnotationDefaults() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            interface Host {
                private class Hidden {
                }
            }

            @interface Flag {
                int value() default 1;
            }

            class Example {
                abstract int run();
            }
            """);

        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("'private' is not allowed on interface member types")));
        assertTrue(diagnostics.stream().anyMatch(
            d -> d.message().contains("'abstract' methods are only allowed in abstract classes and interfaces")));
        assertFalse(
            diagnostics.stream().anyMatch(d -> d.message().contains("'default' is only allowed on interface methods")));
    }

    @Test
    void coreControlFlowRuleEmitsInvalidBreakContinueAndReturnDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreControlFlowInspection(), """
            class Example {
                {
                    break;
                }

                void run() {
                    outer: {
                        continue outer;
                    }
                }

                void noop() {
                    return 1;
                }

                int value() {
                    return;
                }
            }
            """);

        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("'break' is only allowed inside loops or switch statements")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("continue label 'outer' must target a loop")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("void methods cannot return a value")));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("non-void method 'value' must return a value")));
    }

    @Test
    void coreControlFlowRuleEmitsMissingReturnDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreControlFlowInspection(), """
            class Example {
                int run(boolean flag) {
                    if (flag) {
                        return 1;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_MISSING_RETURN".equals(d.code())));
        assertTrue(
            diagnostics.stream().anyMatch(d -> d.message().contains("method 'run' must return 'int' on all paths")));
    }

    @Test
    void coreInheritanceRuleSubstitutesGenericInterfaceTypesWithoutInheritingTypeArguments() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Builder<R, SELF extends Builder<R, SELF>> {
                SELF configure(String value);
                R run();
            }

            final class ExampleBuilder implements Builder<Process, ExampleBuilder> {
                @Override
                public ExampleBuilder configure(String value) {
                    return this;
                }

                @Override
                public Process run() {
                    return null;
                }
            }
            """);

        List<SemanticDiagnostic> inheritanceErrors = diagnostics.stream()
            .filter(diagnostic -> Set.of("SEM_INVALID_OVERRIDE", "SEM_MISSING_IMPLEMENTATION")
                .contains(diagnostic.code()))
            .toList();
        assertTrue(inheritanceErrors.isEmpty(), () -> inheritanceErrors.stream()
            .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
            .collect(Collectors.joining("\n")));
    }

    @Test
    void realCliBuilderDoesNotInheritItsGenericProcessArgument() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/java/cli/impl/JarCLIBuilder.java");
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreInheritanceInspection(),
            sourceFile,
            Files.readString(sourceFile),
            symbolIndex);
        List<SemanticDiagnostic> inheritanceErrors = diagnostics.stream()
            .filter(diagnostic -> Set.of("SEM_INVALID_OVERRIDE", "SEM_MISSING_IMPLEMENTATION")
                .contains(diagnostic.code()))
            .toList();
        assertTrue(inheritanceErrors.isEmpty(), () -> inheritanceErrors.stream()
            .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
            .collect(Collectors.joining("\n")));
    }

    @Test
    void coreInheritanceRuleRecognizesLombokGeneratedGetters() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface Named {
                String getName();
                boolean isActive();
            }

            @Getter
            final class Example implements Named {
                private final String name = "example";
                private final boolean active = true;
            }
            """);

        List<SemanticDiagnostic> missingImplementations = diagnostics.stream()
            .filter(diagnostic -> "SEM_MISSING_IMPLEMENTATION".equals(diagnostic.code()))
            .toList();
        assertTrue(missingImplementations.isEmpty(), () -> missingImplementations.stream()
            .map(SemanticDiagnostic::message)
            .collect(Collectors.joining("\n")));
    }

    @Test
    void coreControlFlowRuleTreatsNonCompletingConstantTrueLoopAsTerminal() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreControlFlowInspection(), """
            class Example {
                int neverCompletes() {
                    while (true) {
                    }
                }

                int canComplete() {
                    while (true) {
                        break;
                    }
                }
            }
            """);

        List<String> missingReturns = diagnostics.stream()
            .filter(diagnostic -> "SEM_MISSING_RETURN".equals(diagnostic.code()))
            .map(SemanticDiagnostic::message)
            .toList();
        assertFalse(missingReturns.stream().anyMatch(message -> message.contains("neverCompletes")));
        assertTrue(missingReturns.stream().anyMatch(message -> message.contains("canComplete")));
    }

    @Test
    void coreControlFlowRuleEmitsInvalidYieldDiagnosticOutsideSwitchExpression() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreControlFlowInspection(), """
            class Example {
                void run(int value) {
                    switch (value) {
                        case 1 -> {
                            yield 1;
                        }
                        default -> {
                        }
                    }

                    int result = switch (value) {
                        case 1 -> {
                            yield 1;
                        }
                        default -> 2;
                    };
                }
            }
            """);

        long invalidYieldDiagnostics = diagnostics.stream()
            .filter(d -> d.message().contains("'yield' is only allowed inside switch expressions"))
            .count();

        assertEquals(1, invalidYieldDiagnostics);
    }

    @Test
    void coreExceptionRuleEmitsUnhandledCheckedExceptionDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void fail() throws java.io.IOException {
                    throw new java.io.IOException();
                }

                void run() {
                    fail();
                    Thread.sleep(1L);
                }

                void declared() throws java.io.IOException, java.lang.InterruptedException {
                    fail();
                    Thread.sleep(1L);
                }

                void caught() {
                    try {
                        fail();
                    } catch (java.io.IOException exception) {
                    }
                }
            }
            """);

        long uncaught = diagnostics.stream()
            .filter(d -> "SEM_UNCAUGHT_CHECKED_EXCEPTION".equals(d.code()))
            .count();

        assertEquals(2, uncaught);
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("java.io.IOException")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("java.lang.InterruptedException")));
    }

    @Test
    void coreExceptionRuleEmitsUnreachableCatchDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void run() {
                    try {
                        throw new java.io.FileNotFoundException();
                    } catch (java.io.IOException exception) {
                    } catch (java.io.FileNotFoundException exception) {
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNREACHABLE_CATCH".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("java.io.FileNotFoundException")));
    }

    @Test
    void coreExceptionRuleEmitsInvalidExceptionTypeDiagnosticsAndTryResourceCloseDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void invalid() throws String {
                    try {
                        throw "";
                    } catch (String value) {
                    }
                }

                void resource() throws java.io.FileNotFoundException {
                    try (java.io.FileInputStream in = new java.io.FileInputStream("x")) {
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INVALID_EXCEPTION_TYPE".equals(d.code())));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("declared thrown type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("caught type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("Unhandled checked exception 'java.io.IOException'")));
    }

    @Test
    void coreExceptionRuleAppliesCatchClausesToResourcesAndRespectsCloseOverrides() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    Resource() throws java.io.IOException {}
                    @Override public void close() throws java.io.IOException {}
                }

                void caughtResource() {
                    try (Resource resource = new Resource()) {
                    } catch (java.io.IOException exception) {
                    }
                }

                void closeOverrideWithoutCheckedException() {
                    try (java.util.stream.Stream<String> values = java.util.stream.Stream.of("value")) {
                        values.count();
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNCAUGHT_CHECKED_EXCEPTION".equals(d.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreExceptionRuleUsesPreciseTypesForCatchParameterRethrows() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void fail() throws java.io.IOException {}

                void uncheckedOnly() {
                    try {
                        throw new IllegalStateException();
                    } catch (Exception exception) {
                        throw exception;
                    }
                }

                void checked() {
                    try {
                        fail();
                    } catch (Exception exception) {
                        throw exception;
                    }
                }
            }
            """);

        List<SemanticDiagnostic> unhandled = diagnostics.stream()
            .filter(diagnostic -> "SEM_UNCAUGHT_CHECKED_EXCEPTION".equals(diagnostic.code()))
            .toList();
        assertEquals(1, unhandled.size());
        assertTrue(unhandled.getFirst().message().contains("java.io.IOException"));
    }

    @Test
    void coreExceptionRuleEmitsDisallowedExceptionDeclarationDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void banned() throws Exception {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("declares disallowed exception 'java.lang.Exception'")));
    }

    @Test
    void coreExceptionRuleEmitsDisallowedExceptionDeclarationDiagnosticForConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                Example() throws RuntimeException {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
        assertTrue(diagnostics.stream()
            .anyMatch(d -> d.message().contains("declares disallowed exception 'java.lang.RuntimeException'")));
    }

    @Test
    void coreExceptionRuleDoesNotEmitDisallowedExceptionDiagnosticForAllowedCheckedException() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void allowed() throws java.io.IOException {
                }
            }
            """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
    }

    @Test
    void coreExceptionRuleDoesNotTreatRecordPatternTypesAsCaughtExceptions() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            import javafx.application.Preloader.PreloaderNotification;

            class Example {
                void handle(PreloaderNotification notification) {
                    if (notification instanceof ErrorNotification(String message)) {
                        System.out.println(message);
                    }
                }

                record ErrorNotification(String message) implements PreloaderNotification {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INVALID_EXCEPTION_TYPE".equals(d.code())));
    }

    @Test
    void coreExceptionRuleDoesNotEmitDisallowedExceptionDiagnosticForPrivateNestedHelperInterface() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                private interface CheckedRunnable {
                    void run() throws Exception;
                }
            }
            """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
    }

    @Test
    void coreExceptionRuleDoesNotTreatPreloaderRecordPatternAsCaughtTypeInRealisticShape() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            import javafx.application.Preloader.PreloaderNotification;

            public class RailroadPreloader {
                public void handleApplicationNotification(PreloaderNotification notification) {
                    if (notification instanceof StatusNotification(String message, double progress)) {
                        System.out.println(message + progress);
                    } else if (notification instanceof ErrorNotification(String message)) {
                        System.out.println(message);
                    }
                }

                public record StatusNotification(String message, double progress) implements PreloaderNotification {
                }

                public record ErrorNotification(String message) implements PreloaderNotification {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("caught type")));
    }

    @Test
    void coreFeatureEnvyRuleEmitsManipulateDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFeatureEnvyInspection(), """
            class Host {
                void envy(External ext) {
                    ext.a = 1;
                    ext.b = 2;
                    ext.c = 3;
                }
            }
            class External {
                int a, b, c;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FEATURE_ENVY_MANIPULATE".equals(d.code())),
            "Expected SEM_FEATURE_ENVY_MANIPULATE diagnostic");
    }

    @Test
    void coreFeatureEnvyRuleEmitsTightlyCoupledDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFeatureEnvyInspection(), """
            class Host {
                void envy(External ext) {
                    ext.doA();
                    ext.doB();
                    ext.doC();
                }
            }
            class External {
                void doA() {}
                void doB() {}
                void doC() {}
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FEATURE_ENVY_TIGHTLY_COUPLED".equals(d.code())),
            "Expected SEM_FEATURE_ENVY_TIGHTLY_COUPLED diagnostic");
    }

    @Test
    void coreFeatureEnvyRuleDoesNotEmitDiagnosticForStaticMembers() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFeatureEnvyInspection(), """
            class Host {
                void ok(External ext) {
                    External.doA();
                    External.doB();
                    External.doC();
                }
            }
            class External {
                static void doA() {}
                static void doB() {}
                static void doC() {}
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> d.code().startsWith("SEM_FEATURE_ENVY")));
    }

    @Test
    void coreFeatureEnvyRuleDoesNotEmitDiagnosticForLibraryTypes() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFeatureEnvyInspection(), """
            class Host {
                void ok(java.util.List<String> list) {
                    list.add("a");
                    list.add("b");
                    list.add("c");
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> d.code().startsWith("SEM_FEATURE_ENVY")));
    }

    @Test
    void coreFeatureEnvyRuleDoesNotEmitDiagnosticForOwnMembers() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFeatureEnvyInspection(), """
            class Host {
                int a, b, c;
                void ok() {
                    a = 1;
                    b = 2;
                    c = 3;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> d.code().startsWith("SEM_FEATURE_ENVY")));
    }

    @Test
    void coreFunctionalInterfaceRuleEmitsDiagnosticForSingleAbstractMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Worker {
                void run();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticWhenAnnotationPresent() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            @FunctionalInterface
            interface Worker {
                void run();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticWhenFullyQualifiedAnnotationPresent() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            @java.lang.FunctionalInterface
            interface Worker {
                void run();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticForZeroAbstractMethods() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Marker {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticForTwoAbstractMethods() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Worker {
                void run();
                void stop();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotCountDefaultMethodsAsAbstract() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Worker {
                void run();
                default void stop() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotCountObjectMethodsAsAbstract() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Worker {
                void run();
                boolean equals(Object other);
                int hashCode();
                String toString();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())));
    }

    @Test
    void coreFunctionalInterfaceRuleEmitsDiagnosticForInterfaceInheritingOnlyAbstractMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Base {
                void run();
            }

            interface Child extends Base {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
            && d.message().contains("Child")));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticWhenInheritedPlusOwnExceedsOne() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Base {
                void run();
            }

            interface Child extends Base {
                void extra();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
            && d.message().contains("Child")));
    }

    @Test
    void coreFunctionalInterfaceRuleEmitsDiagnosticForInterfaceInheritingSameAbstractMethodFromMultipleParents() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface A {
                void run();
            }

            interface B {
                void run();
            }

            interface Child extends A, B {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
            && d.message().contains("Child")));
    }

    @Test
    void coreFunctionalInterfaceRuleDoesNotEmitDiagnosticWhenDefaultMethodOverridesInheritedAbstractMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFunctionalInterfaceInspection(), """
            interface Base {
                void run();
            }

            interface Child extends Base {
                default void run() {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
            && d.message().contains("Child")));
    }

    @Test
    void coreInheritanceRuleEmitsDiagnosticForInterfaceRedundantThroughSuperclass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Worker {}
            class Base implements Worker {}
            class Child extends Base implements Worker {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsDiagnosticForInterfaceRedundantThroughSiblingInterface() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Base {}
            interface Derived extends Base {}
            class Example implements Base, Derived {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsDiagnosticForSuperinterfaceRedundantThroughAnotherSuperinterface() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Base {}
            interface Derived extends Base {}
            interface Example extends Base, Derived {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsDiagnosticForDuplicateImplementedInterface() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Worker {}
            class Example implements Worker, Worker {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsDiagnosticForDuplicateExtendedInterface() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Worker {}
            interface Example extends Worker, Worker {}
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void realFeatureEnvyMapEntryStreamChainRetainsItsKeyType() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/diagnostics/inspections/CoreFeatureEnvyInspection.java");
        String source = Files.readString(sourceFile);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), sourceFile, source, symbolIndex);

        assertFalse(
            diagnostics.stream().anyMatch(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path parserFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/sst/impl/java/JavaGreenParser.java");
        String parserSource = Files.readString(parserFile);
        List<SemanticDiagnostic> parserDiagnostics = runProvider(
            new CoreAssignmentInspection(), parserFile, parserSource, symbolIndex);
        assertFalse(
            parserDiagnostics.stream().anyMatch(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())
                && diagnostic.message().contains("JavaTokenType")),
            () -> parserDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path indexerFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/language/impl/index/JavaProjectLanguageIndexer.java");
        String indexerSource = Files.readString(indexerFile);
        List<SemanticDiagnostic> indexerDiagnostics = runProvider(
            new CoreAssignmentInspection(), indexerFile, indexerSource, symbolIndex);
        assertFalse(
            indexerDiagnostics.stream().anyMatch(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> indexerDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path gitLocatorFile = sourceRoot.resolve(
            "dev/railroadide/railroad/vcs/git/util/GitLocator.java");
        String gitLocatorSource = Files.readString(gitLocatorFile);
        List<SemanticDiagnostic> gitLocatorDiagnostics = runProvider(
            new CoreAssignmentInspection(), gitLocatorFile, gitLocatorSource, symbolIndex);
        assertFalse(
            gitLocatorDiagnostics.stream()
                .anyMatch(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> gitLocatorDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path formComponentFile = sourceRoot.resolve(
            "dev/railroadide/railroad/form/FormComponent.java");
        String formComponentSource = Files.readString(formComponentFile);
        List<SemanticDiagnostic> formComponentDiagnostics = runProvider(
            new CoreAssignmentInspection(), formComponentFile, formComponentSource, symbolIndex);
        assertFalse(
            formComponentDiagnostics.stream()
                .anyMatch(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())
                    && diagnostic.message().contains("ValidationResult")),
            () -> formComponentDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realDialogBuilderCastsConditionalsAndStreamChainResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve("dev/railroadide/railroad/window/DialogBuilder.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(sourcePath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'toList'",
            "Cannot assign 'boolean' to 'java.lang.Runnable'",
            "Cannot assign 'javafx.scene.Node' to 'javafx.scene.layout.VBox'",
            "Cannot assign 'javafx.scene.Node' to 'javafx.scene.layout.HBox'").contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void switchExpressionsAndEnumLabelsResolve() {
        String source = """
            import java.util.function.Consumer;

            class Base {
                void touch() {}
            }
            class Derived extends Base {}
            enum Kind { INFO, ERROR }
            class Box {
                Box(String value) {}
            }
            class Example {
                void accept(Consumer<? super String> consumer) {}

                void run(boolean flag, Kind kind) {
                    var nullable = flag ? new Base() : null;
                    nullable.touch();
                    var box = new Box(switch (kind) {
                        case INFO -> "info";
                        case ERROR -> "error";
                    });
                    accept(text -> text.length());
                }
            }
            """;

        JavaSymbolIndex symbolIndex = JavaJdkSymbolIndex.fromCurrentRuntime();
        Path sourcePath = Path.of("Example.java").toAbsolutePath().normalize();
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreTypeResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'Box'",
            "Cannot resolve call 'length'",
            "Cannot resolve call 'touch'",
            "Cannot resolve type 'INFO'",
            "Cannot resolve type 'ERROR'",
            "Cannot resolve name 'INFO'",
            "Cannot resolve name 'ERROR'").contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realAlertBuilderVarAndJavaFxLambdaReceiversResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve("dev/railroadide/railroad/window/AlertBuilder.java").normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(sourcePath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'hide'",
            "Cannot resolve call 'getCode'",
            "Cannot resolve call 'consume'",
            "Cannot resolve call 'setOnCloseRequest'",
            "Cannot resolve name 'INFO'",
            "Cannot resolve name 'SUCCESS'",
            "Cannot resolve name 'WARNING'",
            "Cannot resolve name 'ERROR'").contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realFluentBuildersAndSemanticNodeAccessorsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/vcs/git/GitCommands.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreAccessibilityInspection.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/defaults/data/GradleRunConfigurationData.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/impl/index/JavaAnalysisContextProvider.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/ImageViewerPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/RunConfigurationManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/ui/RunConfigurationEditorPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/ui/deps/GradleDependenciesPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/git/commit/details/GitCommitCherryPickButton.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/ui/deps/GradleDependencyTreeBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/ui/task/GradleTaskTreeBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreImportInspection.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreNameResolutionInspection.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/PathTreeCell.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/ProjectExplorerPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/git/commit/changes/FileItem.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/JavaCodeEditorPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/RunControlsPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/java/cli/impl/JavadocCLIBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/MappingChannelRegistry.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/step/OnboardingFormStep.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/CheckBoxComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/ComboBoxComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/DirectoryChooserComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/FileChooserComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/RadioButtonGroupComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/TextAreaComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/TextFieldComponent.java"));

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            String source = Files.readString(sourcePath);
            if (sourcePath.toString().contains("form" + File.separator + "impl")) {
                diagnostics.addAll(runProviders(List.of(
                    new CoreCallResolutionInspection(),
                    new CoreNameResolutionInspection(),
                    new CoreMemberResolutionInspection(),
                    new CoreAccessibilityInspection()), sourcePath, source, symbolIndex));
            } else {
                diagnostics.addAll(runProvider(
                    new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
            }
        }

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "SEM_UNRESOLVED_CALL",
            "SEM_UNRESOLVED_NAME",
            "SEM_UNRESOLVED_MEMBER",
            "SEM_INACCESSIBLE_MEMBER").contains(diagnostic.code())),
            () -> diagnostics.stream()
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realExportedResolutionAndAccessibilityErrorsAreAbsent() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/CheckBoxComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/ComboBoxComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/DirectoryChooserComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/FileChooserComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/RadioButtonGroupComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/TextAreaComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/TextFieldComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/ui/FormDirectoryChooser.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/ui/FormFileChooser.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/ui/deps/GradleDependencyTreeBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/ui/task/GradleTaskTreeBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/classparser/ClassStubVisitor.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/classparser/Type.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreImportInspection.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreNameResolutionInspection.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/JavaDiagnosticsProvider.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/JdtDiagnosticsProvider.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/PathTreeCell.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/ProjectExplorerPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/task/SearchTask.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/task/WatchTask.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/defaults/ShellScriptRunConfigurationType.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/ui/form/RunConfigurationPickerComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/impl/java/JavaLexerSnapshot.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/codeeditor/CodeEditorPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/git/commit/changes/FileItem.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/JavaCodeEditorPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/RunControlsPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/java/cli/impl/JavadocCLIBuilder.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/spi/inspection/JavaRuleContext.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/MappingChannelRegistry.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/step/OnboardingFormStep.java"),
            sourceRoot.resolve("dev/railroadide/railroad/settings/handler/SettingsHandler.java"),
            sourceRoot.resolve("dev/railroadide/railroad/settings/Settings.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/repositories/MinecraftVersionRepository.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/SwitchboardClient.java"),
            sourceRoot.resolve("dev/railroadide/railroad/utility/ImageUtils.java"),
            Path.of("src/test/java/dev/railroadide/railroad/plugin/PluginManagerTest.java").toAbsolutePath()
                .normalize());
        Set<String> errorCodes = Set.of(
            "SEM_UNRESOLVED_MEMBER",
            "SEM_INACCESSIBLE_MEMBER",
            "SEM_UNRESOLVED_TYPE",
            "SEM_UNRESOLVED_CALL",
            "SEM_UNRESOLVED_NAME",
            "SEM_INACCESSIBLE_CALL");
        List<JavaInspectionRuleProvider> providers = List.of(
            new CoreMemberResolutionInspection(),
            new CoreTypeResolutionInspection(),
            new CoreCallResolutionInspection(),
            new CoreNameResolutionInspection(),
            new CoreAccessibilityInspection());

        List<String> errors = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            List<SemanticDiagnostic> diagnostics = runProviders(
                providers, sourcePath, Files.readString(sourcePath), symbolIndex);
            diagnostics.stream()
                .filter(diagnostic -> errorCodes.contains(diagnostic.code()))
                .map(diagnostic -> sourceRoot.relativize(sourcePath) + ":"
                    + diagnostic.startOffset() + " " + diagnostic.code() + " " + diagnostic.message())
                .forEach(errors::add);
        }

        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }

    @Test
    void realTryWithResourcesCatchesCoverInitializationAndCloseExceptions() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/utility/FileUtils.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/ProjectValidators.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/ProjectDiagnosticsScanner.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaLibrarySymbolIndex.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/PluginManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaProjectSemanticPersistence.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/cache/impl/SqlCacheManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/spi/secure_storage/SecureTokenStore.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/cache/impl/JsonCacheManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/minecraft/pistonmeta/Download.java"),
            sourceRoot.resolve("dev/railroadide/railroad/theme/ThemeDownloadManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/indexing/Indexes.java"),
            sourceRoot.resolve("dev/railroadide/railroad/java/JDKManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/service/impl/ToolingGradleExecutionService.java"),
            sourceRoot.resolve("dev/railroadide/railroad/utility/UrlUtils.java"),
            sourceRoot.resolve("dev/railroadide/railroad/java/JDKUtils.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/PathTreeItem.java"),
            sourceRoot.resolve("dev/railroadide/railroad/settings/Setting.java"),
            sourceRoot.resolve("dev/railroadide/railroad/utility/GitUtils.java"),
            sourceRoot.resolve("dev/railroadide/railroad/utility/network/check/HTTPCheck.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/SwitchboardClient.java"),
            sourceRoot.resolve("dev/railroadide/railroad/theme/ThemeManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/PluginLoader.java"),
            sourceRoot.resolve("dev/railroadide/railroad/gradle/service/impl/ToolingGradleModelService.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/ide/language/impl/index/JavaLanguageIndexContextContributor.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/task/WatchTask.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/facet/detector/JavaFacetDetector.java"),
            sourceRoot.resolve("dev/railroadide/railroad/localization/L18n.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/project/onboarding/creation/service/ToolingGradleService.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/data/ProjectDataStore.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/TerminalFactory.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/RunConfiguration.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/ide/runconfig/defaults/JavaApplicationRunConfigurationType.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaJdkSymbolIndex.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/defaults/FileSystemDocument.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaProjectSemanticIndexer.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/index/ProjectLanguageIndexService.java"),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaParserTestSupport.java")
                .toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexerTest.java").toAbsolutePath()
                .normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/plugin/PluginManagerTest.java").toAbsolutePath()
                .normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/project/ProjectLanguageIndexServiceTest.java")
                .toAbsolutePath().normalize(),
            Path.of(
                "src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus/valid/06_statements_control_flow.java")
                .toAbsolutePath().normalize());

        List<String> unhandled = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            List<SemanticDiagnostic> diagnostics = runProvider(
                new CoreExceptionInspection(), sourcePath, Files.readString(sourcePath), symbolIndex);
            diagnostics.stream()
                .filter(diagnostic -> "SEM_UNCAUGHT_CHECKED_EXCEPTION".equals(diagnostic.code()))
                .map(diagnostic -> sourceRoot.relativize(sourcePath) + ":"
                    + diagnostic.startOffset() + " " + diagnostic.message())
                .forEach(unhandled::add);
        }

        assertTrue(unhandled.isEmpty(), () -> String.join("\n", unhandled));
    }

    @Test
    void realGenericPipelinesDoNotReportIncompatibleAssignments() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/form/FormSection.java"),
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/RadioButtonGroupComponent.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/impl/index/JavaAnalysisContextProvider.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/index/ProjectLanguageIndexService.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/dialog/CreateFileDialog.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/signature/JdtJavaSignatureHelpProvider.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/PaneIconBarFactory.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/RunControlsPane.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/spi/inspection/JavaRuleContext.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/impl/NeoforgeProjectOnboarding.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/impl/ForgeProjectOnboarding.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/impl/FabricProjectOnboarding.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/step/OnboardingFormStep.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/cache/impl/SqlCacheManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/switchboard/cache/impl/JsonCacheManager.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/creation/modjson/adapter/MixinListTypeAdapter.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/creation/modjson/adapter/PersonListTypeAdapter.java"),
            sourceRoot
                .resolve("dev/railroadide/railroad/project/creation/modjson/adapter/EntrypointListTypeAdapter.java"),
            sourceRoot.resolve("dev/railroadide/railroad/window/AlertBuilder.java"),
            Path.of(
                "src/test/java/dev/railroadide/railroad/ide/language/index/ProjectLanguageIndexCoordinatorTest.java")
                .toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaProjectParityTest.java")
                .toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/project/ProjectLanguageIndexServiceTest.java")
                .toAbsolutePath().normalize(),
            Path.of(
                "src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus/valid/07_expressions_all_forms.java")
                .toAbsolutePath().normalize());

        List<String> incompatible = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            List<SemanticDiagnostic> diagnostics = runProvider(
                new CoreAssignmentInspection(), sourcePath, Files.readString(sourcePath), symbolIndex);
            diagnostics.stream()
                .filter(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code()))
                .map(diagnostic -> sourceRoot.relativize(sourcePath) + ":"
                    + diagnostic.startOffset() + " " + diagnostic.message())
                .forEach(incompatible::add);
        }

        assertTrue(incompatible.isEmpty(), () -> String.join("\n", incompatible));
    }

    @Test
    void realJavaExecutableNestedEnumInheritanceRemainsResolvable() throws Exception {
        Path sourcePath = Path.of(
            "src/main/java/dev/railroadide/railroad/java/cli/impl/JavaExecutableCLIBuilder.java");
        String source = Files.readString(sourcePath);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(
                Path.of("src/main/java").toAbsolutePath().normalize()),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(
            diagnostics.stream().anyMatch(diagnostic -> "Cannot resolve call 'name'".equals(diagnostic.message())),
            () -> diagnostics.stream()
                .filter(diagnostic -> "Cannot resolve call 'name'".equals(diagnostic.message()))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realLayoutTreeGenericLombokAccessorsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve(
            "dev/railroadide/railroad/ui/layout/LayoutParser.java");
        String source = Files.readString(sourcePath);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_CALL".equals(diagnostic.code())
            && diagnostic.startOffset() < 7_000),
            () -> diagnostics.stream()
                .filter(diagnostic -> diagnostic.startOffset() < 7_000)
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realJavaLexerInheritedRecordAndComparatorLambdaResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/sst/impl/java/JavaLexer.java");
        String source = Files.readString(sourcePath);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'LexError'",
            "Cannot resolve call 'getKey'",
            "Cannot resolve call 'length'").contains(diagnostic.message())),
            () -> diagnostics.stream()
                .filter(diagnostic -> Set.of("LexError", "getKey", "length").stream()
                    .anyMatch(diagnostic.message()::contains))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realJavaInspectionRuleSettingsPaneNestedFieldsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/diagnostics/ui/JavaInspectionRuleSettingsPane.java");
        String source = Files.readString(sourcePath);
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        Set<String> calls = Set.of("rule", "id", "getValue", "setValue", "values");
        assertFalse(diagnostics.stream().anyMatch(diagnostic -> calls.stream()
            .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message()))),
            () -> diagnostics.stream()
                .filter(diagnostic -> calls.stream()
                    .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message())))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realJavaParserBenchmarkExplicitLambdaParameterResolves() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/sst/impl/java/JavaParserBenchmarkRunner.java");
        String source = Files.readString(sourcePath);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        Set<String> calls = Set.of("getValue", "averageNanos");
        assertFalse(diagnostics.stream().anyMatch(diagnostic -> calls.stream()
            .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message()))),
            () -> diagnostics.stream()
                .filter(diagnostic -> calls.stream()
                    .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message())))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realLocalizationProjectLoggerFieldResolves() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve("dev/railroadide/railroad/localization/L18n.java");
        String source = Files.readString(sourcePath);
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(
            diagnostics.stream().anyMatch(diagnostic -> "Cannot resolve call 'debug'".equals(diagnostic.message())
                || "Cannot resolve call 'error'".equals(diagnostic.message())),
            () -> diagnostics.stream()
                .filter(diagnostic -> diagnostic.message().contains("call 'debug'")
                    || diagnostic.message().contains("call 'error'"))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void realConditionalCommonTypeAndSourceFunctionalTargetResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/PaneIconBarFactory.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/codeeditor/CodeEditorPane.java"));

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            diagnostics.addAll(runProvider(
                new CoreCallResolutionInspection(), sourcePath, Files.readString(sourcePath), symbolIndex));
        }

        Set<String> calls = Set.of(
            "getStyleClass", "getChildren", "add", "remove", "namedThreadFactory", "length");
        assertFalse(diagnostics.stream().anyMatch(diagnostic -> calls.stream()
            .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message()))),
            () -> diagnostics.stream()
                .filter(diagnostic -> calls.stream()
                    .anyMatch(call -> ("Cannot resolve call '" + call + "'").equals(diagnostic.message())))
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void duplicateDeclarationRuleAllowsCallableOverloads() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            class Overloads {
                Overloads() {
                }

                Overloads(String value) {
                }

                void run() {
                }

                void run(int value) {
                }

                void check(String value) {
                }

                void check(String... values) {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
    }

    @Test
    void duplicateDeclarationRuleAllowsSameCatchParameterNameInSiblingCatches() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            class Example {
                void run() {
                    try {
                        work();
                    } catch (java.io.IOException exception) {
                        handle(exception);
                    } catch (RuntimeException exception) {
                        handle(exception);
                    }
                }

                void work() throws java.io.IOException {}
                void handle(Exception exception) {}
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
    }

    @Test
    void duplicateDeclarationRuleAllowsSameLoopVariableNameInSiblingLoops() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            class Example {
                void run() {
                    for (int index = 0; index < 1; index++) {}
                    for (int index = 0; index < 1; index++) {}
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
    }

    @Test
    void typeResolutionHandlesAnnotatedTypesAndMultiCatchAlternativesIndividually() {
        String source = """
            import java.io.IOException;
            import java.net.URISyntaxException;

            @interface Mark {}

            class Example<T> {
                @Mark T value;

                void run() {
                    try {
                        throw new IOException();
                    } catch (IOException | URISyntaxException exception) {
                        System.out.println(exception);
                    }
                }

                int classify(int value) {
                    return switch (value) {
                        case java.awt.color.ColorSpace.TYPE_RGB -> 1;
                        default -> 0;
                    };
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreTypeResolutionInspection(), source);
        List<String> unresolvedTypes = diagnostics.stream()
            .filter(diagnostic -> "SEM_UNRESOLVED_TYPE".equals(diagnostic.code()))
            .map(SemanticDiagnostic::message)
            .toList();

        assertTrue(unresolvedTypes.isEmpty(), () -> String.join("\n", unresolvedTypes));
    }

    @Test
    void realConfigHandlerYieldStatementsRemainInsideSwitchExpressions() throws Exception {
        Path sourceFile = Path.of(
            "src/main/java/dev/railroadide/railroad/config/ConfigHandler.java").toAbsolutePath().normalize();
        String source = Files.readString(sourceFile);
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreControlFlowInspection(), sourceFile, source);
        var context = new JavaRuleContext(sourceFile, source, JavaSemanticAnalyzer.analyzeFacts(source));
        List<String> yieldAncestors = new ArrayList<>();
        context.traverse(node -> {
            if (!"JAVA_YIELD_STATEMENT".equals(node.kind().id()))
                return;
            List<String> kinds = new ArrayList<>();
            SyntaxNode current = node;
            while (current != null) {
                kinds.add(current.kind().id());
                current = current.parent().orElse(null);
            }
            yieldAncestors.add(String.join(" -> ", kinds));
        });

        List<String> invalidYields = diagnostics.stream()
            .filter(diagnostic -> diagnostic.message().contains("'yield' is only allowed inside switch expressions"))
            .map(SemanticDiagnostic::message)
            .toList();
        assertTrue(invalidYields.isEmpty(), () -> String.join("\n", invalidYields)
            + "\n" + String.join("\n", yieldAncestors));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitDiagnosticForIndependentInterfaces() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreRedundantInterfaceDeclarationInspection(), """
            interface Left {}
            interface Right {}
            class Example implements Left, Right {}
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_REDUNDANT_INTERFACE_DECLARATION".equals(d.code())));
    }

    @Test
    void coreDoubleNegationDoesEmitDiagnosticWhenDoubleNegative() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDoubleNegationInspection(), """
            class Example {
                boolean run() {
                    return !!true;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_DOUBLE_NEGATION".equals(d.code())));
    }

    @Test
    void coreDoubleNegationDoesEmitDiagnosticWhenDoubleNegativeWithParenthesis() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDoubleNegationInspection(), """
            class Example {
                boolean run() {
                    return !(!true);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_DOUBLE_NEGATION".equals(d.code())));
    }

    @Test
    void coreDoubleNegationDoesNotEmitDiagnosticWhenSingleNegative() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDoubleNegationInspection(), """
            class Example {
                boolean run() {
                    return !true;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_DOUBLE_NEGATION".equals(d.code())));
    }

    @Test
    void coreDoubleNegationDoesNotEmitDiagnosticWhenSingleNegativeWithParenthesis() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDoubleNegationInspection(), """
            class Example {
                boolean run() {
                    return !(true);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_DOUBLE_NEGATION".equals(d.code())));
    }

    @Test
    void coreConditionalExpressionWithIdenticalBranchesEmitsDiagnosticWhenTernaryHasSameBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreConditionalExpressionWithIdenticalBranchesInspection(), """
                class Example {
                    int run(boolean flag, int value) {
                        return flag ? value : value;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES".equals(d.code())));
    }

    @Test
    void coreConditionalExpressionWithIdenticalBranchesDoesNotEmitDiagnosticWhenTernaryHasDifferentBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreConditionalExpressionWithIdenticalBranchesInspection(), """
                class Example {
                    int run(boolean flag, int valueA, int valueB) {
                        return flag ? valueA : valueB;
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES".equals(d.code())));
    }
}
