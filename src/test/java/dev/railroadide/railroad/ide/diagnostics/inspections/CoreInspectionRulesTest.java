package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettings;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CoreInspectionRulesTest {

    @Test
    void coreProvidersExposeExpectedRuleIds() {
        assertRuleIds(new CoreDuplicateDeclarationInspection(), Set.of("SEM_DUPLICATE_DECLARATION"));
        assertRuleIds(new CoreImportInspection(), Set.of("SEM_DUPLICATE_IMPORT", "SEM_AMBIGUOUS_IMPORT", "SEM_UNRESOLVED_IMPORT"));
        assertRuleIds(new CoreNameResolutionInspection(), Set.of("SEM_UNRESOLVED_NAME", "SEM_AMBIGUOUS_NAME"));
        assertRuleIds(new CoreTypeResolutionInspection(), Set.of("SEM_UNRESOLVED_TYPE"));
        assertRuleIds(new CoreMemberResolutionInspection(), Set.of("SEM_UNRESOLVED_MEMBER"));
        assertRuleIds(new CoreCallResolutionInspection(), Set.of("SEM_UNRESOLVED_CALL"));
        assertRuleIds(new CoreAccessibilityInspection(), Set.of("SEM_INACCESSIBLE_TYPE", "SEM_INACCESSIBLE_MEMBER", "SEM_INACCESSIBLE_CALL"));
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
        assertRuleIds(new CoreDefiniteAssignmentInspection(), Set.of("SEM_UNASSIGNED_VARIABLE", "SEM_ILLEGAL_FINAL_ASSIGNMENT", "SEM_UNINITIALIZED_FINAL_FIELD"));
        assertRuleIds(new CoreAssignmentInspection(), Set.of("SEM_INCOMPATIBLE_ASSIGNMENT"));
        assertRuleIds(new CoreNegativeHexIntInLongContextInspection(), Set.of("SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT"));
        assertRuleIds(new CoreOverlyStrongTypeCastInspection(), Set.of("SEM_OVERLY_STRONG_TYPE_CAST"));
        assertRuleIds(new CoreCastConflictingWithInstanceofInspection(), Set.of("SEM_CAST_CONFLICTING_WITH_INSTANCEOF"));
        assertRuleIds(new CoreWildcardImportInspection(), Set.of("SEM_WILDCARD_IMPORT"));
        assertRuleIds(new CoreEmptyCatchInspection(), Set.of("SEM_EMPTY_CATCH"));
        assertRuleIds(new CorePublicClassNotNamedAfterFileInspection(), Set.of("SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE"));
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
        assertRuleIds(new CoreAssertionCanBeReplacedWithIfStatementInspection(), Set.of("SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT"));
        assertRuleIds(new CoreAssertionWithSideEffectsInspection(), Set.of("SEM_ASSERTION_WITH_SIDE_EFFECTS"));
        assertRuleIds(new CoreFeatureEnvyInspection(), Set.of("SEM_FEATURE_ENVY_MANIPULATE", "SEM_FEATURE_ENVY_TIGHTLY_COUPLED"));
        assertRuleIds(new CoreInitializationInspection(), Set.of(
            "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION",
            "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION"));
        assertRuleIds(new CoreThisReferenceEscapedObjectConstructionInspection(), Set.of("SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION"));
        assertRuleIds(new CoreFieldCanBeLocalVariableInspection(), Set.of("SEM_FIELD_CAN_BE_LOCAL_VARIABLE"));
        assertRuleIds(new CoreFunctionalInterfaceInspection(), Set.of("SEM_INTERFACE_SHOULD_BE_FUNCTIONAL"));
        assertRuleIds(new CoreOptionalGetWithoutIsPresentCheckInspection(), Set.of("SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK"));
        assertRuleIds(new CoreAutoCloseableWithoutTryWithResourcesInspection(), Set.of("SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES"));
        assertRuleIds(new CoreInfiniteRecursionInspection(), Set.of("SEM_INFINITE_RECURSION"));
        assertRuleIds(new CoreBigDecimalEqualsInspection(), Set.of("SEM_BIG_DECIMAL_EQUALS"));
        assertRuleIds(new CoreSerializableClassWithUnconstructableAncestorInspection(), Set.of("SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR"));
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
        List<SemanticDiagnostic> diagnostics = runProvider(new CorePublicClassNotNamedAfterFileInspection(), Path.of("Example.java"), """
            public class Wrong {
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Public class 'Wrong' must be declared in a file named 'Example.java'")));
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
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                public void run(int value) {
                    assert value > 0;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticWhenMessagePresent() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                private void validate(int value) {
                    assert value > 0 : "value must be positive";
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForProtectedMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                protected void run(int value) {
                    assert value > 0;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForInterfaceMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            interface Example {
                default void run(int value) {
                    assert value > 0;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleEmitsDiagnosticForPublicConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                public Example(int value) {
                    assert value > 0;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleDoesNotEmitDiagnosticForPrivateHelperWithoutMessage() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                private void validate(int value) {
                    assert value > 0;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
    }

    @Test
    void coreAssertionRuleDoesNotEmitDiagnosticForPackagePrivateMethodWithoutMessage() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssertionCanBeReplacedWithIfStatementInspection(), """
            class Example {
                void validate(int value) {
                    assert value > 0;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT".equals(d.code())));
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

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_INVALID_OVERRIDE".equals(d.code()) && d.message().contains("value")));
    }

    @Test
    void coreInheritanceRuleEmitsInterfaceObjectMethodClashDiagnosticForPrimitiveCloneReturnType() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface BadClone {
                double clone();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())
                && d.message().contains("clone()")));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitInterfaceObjectMethodClashDiagnosticForCovariantCloneReturnType() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface GoodClone {
                String clone();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())));
    }

    @Test
    void coreInheritanceRuleEmitsInterfaceObjectMethodClashDiagnosticForNonVoidFinalize() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface BadFinalize {
                int finalize();
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())
                && d.message().contains("finalize()")));
    }

    @Test
    void coreInheritanceRuleDoesNotEmitInterfaceObjectMethodClashDiagnosticForVoidFinalize() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInheritanceInspection(), """
            interface GoodFinalize {
                void finalize();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD".equals(d.code())));
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

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_OVERLY_STRONG_TYPE_CAST".equals(d.code())
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_OVERLY_STRONG_TYPE_CAST".equals(d.code())));
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

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())));
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE".equals(d.code())));
    }

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

    @Test
    void coreModifierRuleEmitsIllegalTypeAndFieldModifierDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreModifierInspection(), """
            private static class Example {
                final volatile int value = 1;
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_MODIFIER".equals(d.code())));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("top-level type declarations")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'final' cannot be combined with 'volatile'")));
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'abstract' cannot be combined with 'final'")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'default' is only allowed on interface methods")));
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

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'private' is not allowed on interface fields")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'protected' is not allowed on interface methods")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'final' is not allowed on interface methods")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'static' interface methods must have a body")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'private' is not allowed on annotation type elements")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on enum constructors")));
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

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on record components")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'final' is not allowed on record components")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on parameters")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("duplicate modifier 'final'")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'public' is not allowed on local variables")));
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

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'private' is not allowed on interface member types")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'abstract' methods are only allowed in abstract classes and interfaces")));
        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("'default' is only allowed on interface methods")));
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

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("'break' is only allowed inside loops or switch statements")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("continue label 'outer' must target a loop")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("void methods cannot return a value")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("non-void method 'value' must return a value")));
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("method 'run' must return 'int' on all paths")));
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("declared thrown type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("caught type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Unhandled checked exception 'java.io.IOException'")));
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("declares disallowed exception 'java.lang.Exception'")));
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("declares disallowed exception 'java.lang.RuntimeException'")));
    }

    @Test
    void coreExceptionRuleDoesNotEmitDisallowedExceptionDiagnosticForAllowedCheckedException() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreExceptionInspection(), """
            class Example {
                void allowed() throws java.io.IOException {
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
    }

    @Test
    void coreDefiniteAssignmentRuleEmitsUnassignedAndIllegalFinalAssignmentDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run(boolean flag, final int parameter) {
                    int value;
                    if (flag) {
                        value = 1;
                    }

                    final int once;
                    once = 1;
                    once = 2;
                    parameter = 3;
                    System.out.println(value);
                }

                void ok(boolean flag) {
                    final int assignedInBranches;
                    if (flag) {
                        assignedInBranches = 1;
                    } else {
                        assignedInBranches = 2;
                    }
                    System.out.println(assignedInBranches);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("value")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("once")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("parameter")));
        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("assignedInBranches")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesLoopBreakAndContinueExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int fromWhile;
                    while (true) {
                        fromWhile = 1;
                        break;
                    }
                    System.out.println(fromWhile);
                }

                void alsoRun() {
                    int fromDoWhile;
                    do {
                        fromDoWhile = 1;
                        continue;
                    } while (false);
                    System.out.println(fromDoWhile);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromWhile")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromDoWhile")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesLabeledBreakAndContinueExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int fromLabeledBlock;
                    outer: {
                        fromLabeledBlock = 1;
                        break outer;
                    }
                    System.out.println(fromLabeledBlock);
                }

                void loop() {
                    int fromLabeledLoop;
                    outer:
                    do {
                        fromLabeledLoop = 1;
                        continue outer;
                    } while (false);
                    System.out.println(fromLabeledLoop);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromLabeledBlock")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromLabeledLoop")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesSwitchFallthroughAndMissingDefaultExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void assigned(int mode) {
                    int assignedValue;
                    switch (mode) {
                        case 0:
                        case 1:
                            assignedValue = 1;
                            break;
                        default:
                            assignedValue = 2;
                    }
                    System.out.println(assignedValue);
                }

                void fallthroughAssigned(int mode) {
                    int fallthroughValue;
                    switch (mode) {
                        case 0:
                            fallthroughValue = 1;
                        case 1:
                            fallthroughValue = 2;
                            break;
                        default:
                            fallthroughValue = 3;
                    }
                    System.out.println(fallthroughValue);
                }

                void missingDefault(int mode) {
                    int missingDefaultValue;
                    switch (mode) {
                        case 0:
                            missingDefaultValue = 1;
                            break;
                    }
                    System.out.println(missingDefaultValue);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("assignedValue")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fallthroughValue")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("missingDefaultValue")));
    }

    @Test
    void coreDefiniteAssignmentRuleDoesNotTreatForUpdateAsPreLoopAssignment() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int updatedValue;
                    for (;; updatedValue = 1) {
                        break;
                    }
                    System.out.println(updatedValue);
                }

                void ok() {
                    int initializedValue;
                    for (initializedValue = 1;;) {
                        break;
                    }
                    System.out.println(initializedValue);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("updatedValue")));
        assertEquals(1, diagnostics.stream()
            .filter(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("updatedValue"))
            .count());
    }

    @Test
    void coreDefiniteAssignmentRuleEmitsUninitializedFinalFieldDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value;
                final int initialized;

                {
                    initialized = 1;
                }

                Example(boolean flag) {
                    if (flag) {
                        value = 1;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(d.code()) && d.message().contains("value")));
        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("'initialized'")));
    }

    @Test
    void coreDefiniteAssignmentRuleEmitsIllegalFinalFieldAssignmentDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value = 1;

                Example() {
                    value = 2;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("value")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesFinalFieldInitializationThroughSwitchFallthrough() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value;

                Example(int mode) {
                    switch (mode) {
                        case 0:
                        case 1:
                            value = 1;
                            break;
                        default:
                            value = 2;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(d.code()) && d.message().contains("value")));
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

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FEATURE_ENVY_MANIPULATE".equals(d.code())), "Expected SEM_FEATURE_ENVY_MANIPULATE diagnostic");
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

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FEATURE_ENVY_TIGHTLY_COUPLED".equals(d.code())), "Expected SEM_FEATURE_ENVY_TIGHTLY_COUPLED diagnostic");
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
    void coreInitializationRuleEmitsDiagnosticForImplicitThisCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    configure();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleEmitsDiagnosticForExplicitThisCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    this.configure();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForOtherReceiver() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example(Example other) {
                    other.configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForSuperCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Base {
                void configure() {}
            }

            class Example extends Base {
                Example() {
                    super.configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForFinalOrStaticTarget() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                final void configureFinal() {}
                static void configureStatic() {}

                Example() {
                    configureFinal();
                    configureStatic();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleEmitsOverriddenDiagnosticWhenSubclassOverrides() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Base {
                void configure() {}

                Base() {
                    configure();
                }
            }

            class Derived extends Base {
                @Override
                void configure() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitOverriddenDiagnosticWhenNoSubclass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToCollectionPublisher() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            import java.util.ArrayList;
            import java.util.List;

            class Example {
                private final List<Object> items = new ArrayList<>();

                Example() {
                    items.add(this);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToPublishingMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                void register(Object value) {
                }

                Example() {
                    register(this);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToPublishingMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                void execute(Runnable runnable) {
                }

                Example() {
                    execute(() -> System.out.println(this));
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToThreadConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                Example() {
                    new Thread(() -> System.out.println(this));
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForThisAssignedToField() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                private static Example leaked;

                Example() {
                    leaked = this;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForPlainThisUse() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                Example() {
                    this.hashCode();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalVariableInitialization() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                Example() {
                    Object local = this;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalMethodCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                void use(Object value) {
                }

                Example() {
                    use(this);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForNestedLambdaThatDoesNotEscape() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(), """
            class Example {
                Example() {
                    Runnable outer = () -> {
                        Runnable inner = () -> System.out.println(this);
                    };
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }


    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForPrivateFieldUsedOnlyInOneMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    System.out.println(field);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                Example() {
                    field = 2;
                }

                void method() {
                    System.out.println(field);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInMultipleMethods() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method1() {
                    System.out.println(field);
                }

                void method2() {
                    field = 2;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldOnlyReadInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> System.out.println(field);
                    task.run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldReadInMethodAndLambdaWithinSameMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    System.out.println(field);
                    Runnable task = () -> System.out.println(field);
                    task.run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldAssignedInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> field = 2;
                    task.run();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldIncrementedInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> this.field++;
                    task.run();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
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

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
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

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL".equals(d.code())
                && d.message().contains("Child")));
    }

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

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForDirectConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource = new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForVarConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    var resource = new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticInsideTryWithResources() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    try (Resource resource = new Resource()) {
                        System.out.println(resource);
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForDeclarationWithoutInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource;
                    resource = new Resource();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForFieldDeclaration() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                private final Resource resource = new Resource();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForAliasInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run(Resource existing) {
                    Resource alias = existing;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForFieldAccessInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                private final Resource shared = null;

                void run() {
                    Resource alias = this.shared;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForOpenMethodFactory() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource openResource() {
                    return new Resource();
                }

                void run() {
                    Resource resource = openResource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForMethodFactoryOutsideHeuristic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource buildResource() {
                    return new Resource();
                }

                void run() {
                    Resource resource = buildResource();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForOpenMethodReturningNonCloseable() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                Object openValue() {
                    return new Object();
                }

                void run() {
                    Object value = openValue();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForConditionalFactoryBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource openResource() {
                    return new Resource();
                }

                void run(boolean flag, Resource existing) {
                    Resource resource = flag ? existing : openResource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForConditionalAliases() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run(boolean flag, Resource left, Resource right) {
                    Resource resource = flag ? left : right;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForCastWrappedConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource = (Resource) new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreInfiniteRecursionRuleEmitsDiagnosticForDirectRecursiveReturn() {
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
    void coreInfiniteRecursionRuleEmitsDiagnosticForDirectRecursiveExpressionStatement() {
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
    void coreInfiniteRecursionRuleEmitsDiagnosticWhenBothIfBranchesRecurse() {
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
    void coreInfiniteRecursionRuleDoesNotEmitDiagnosticWhenBaseCaseReturns() {
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
    void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForLambdaContainedSelfCall() {
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
    void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForConditionalSingleBranchRecursion() {
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
    void coreInfiniteRecursionRuleDoesNotEmitDiagnosticForDifferentOverloadCall() {
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

    @Test
    void coreBigDecimalEqualsRuleEmitsDiagnosticForBigDecimalEqualsCall() {
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
    void coreBigDecimalEqualsRuleEmitsDiagnosticForImportedBigDecimalEqualsCall() {
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
    void coreBigDecimalEqualsRuleEmitsDiagnosticForBigDecimalLiteralEqualsCall() {
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
    void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticForCompareToEqualityCheck() {
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
    void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticForNonBigDecimalEqualsCall() {
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
    void coreBigDecimalEqualsRuleEmitsDiagnosticWhenArgumentIsBigDecimalSubtype() {
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
    void coreBigDecimalEqualsRuleDoesNotEmitDiagnosticWhenArgumentIsNotBigDecimal() {
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
    void coreBigDecimalEqualsRuleEmitsDiagnosticForParenthesizedBigDecimalEqualsCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreBigDecimalEqualsInspection(), """
            class Example {
                boolean same(java.math.BigDecimal left, java.math.BigDecimal right) {
                    return (left).equals((right));
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_BIG_DECIMAL_EQUALS".equals(d.code())));
    }

    @Test
    void coreSerializationRuleEmitsDiagnosticForDirectAncestorWithoutNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                Base(int value) {}
            }

            class Child extends Base implements Serializable {
                Child() { super(1); }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleEmitsDiagnosticForIndirectNonSerializableAncestor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                Base(String value) {}
            }

            class Mid extends Base implements Serializable {
                Mid() { super("x"); }
            }

            class Leaf extends Mid implements Serializable {
                Leaf() {}
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleEmitsDiagnosticForPrivateNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                private Base() {}
            }

            class Child extends Base implements Serializable {
                Child() { super(); }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForImplicitDefaultConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
            }

            class Child extends Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForAccessibleProtectedNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                protected Base() {}
            }

            class Child extends Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForNestedAncestorImplicitPackagePrivateNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Outer {
                static class Base {
                }
            }

            class Child extends Outer.Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForNonSerializableClass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            class Base {
                Base(int value) {}
            }

            class Child extends Base {
                Child() { super(1); }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
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
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConditionalExpressionWithIdenticalBranchesInspection(), """
            class Example {
                int run(boolean flag, int value) {
                    return flag ? value : value;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES".equals(d.code())));
    }

    @Test
    void coreConditionalExpressionWithIdenticalBranchesDoesNotEmitDiagnosticWhenTernaryHasDifferentBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreConditionalExpressionWithIdenticalBranchesInspection(), """
            class Example {
                int run(boolean flag, int valueA, int valueB) {
                    return flag ? valueA : valueB;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES".equals(d.code())));
    }
    private static List<SemanticDiagnostic> runProvider(JavaInspectionRuleProvider provider, String document) {
        return runProvider(provider, Path.of("Example.java"), document);
    }

    private static List<SemanticDiagnostic> runProvider(JavaInspectionRuleProvider provider, Path filePath, String document) {
        JavaInspectionRuleSettings.resetAll();
        var model = JavaSemanticAnalyzer.analyzeFacts(document);
        JavaRuleContext context = new JavaRuleContext(filePath, document, model);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaInspectionReporter reporter = diagnostics::add;
        JavaInspectionRuleEngine.runRules(provider, context, reporter);
        return List.copyOf(diagnostics);
    }

    private static void assertRuleIds(JavaInspectionRuleProvider provider, Set<String> expectedIds) {
        Set<String> actual = provider.rules().stream().map(JavaInspectionRule::id).collect(Collectors.toSet());
        assertEquals(expectedIds, actual);
    }
}
