package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettings;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        assertRuleIds(new CoreImplicitNumericConversionInspection(), Set.of("SEM_IMPLICIT_NUMERIC_CONVERSION"));
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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNRESOLVED_NAME".equals(diagnostic.code())),
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
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> expressions = new ArrayList<>();
        context.traverse(node -> {
            if (node.kind().id().contains("EXPRESSION") && node.start() <= node.end()) {
                expressions.add(node.kind().id() + " " + source.substring(node.start(), node.end()) + " -> "
                    + context.resolvedSymbol(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreMemberResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNRESOLVED_MEMBER".equals(diagnostic.code())),
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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INACCESSIBLE_MEMBER".equals(diagnostic.code())
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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INACCESSIBLE_CALL".equals(diagnostic.code())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreInheritanceInspection(),
            sourceFile,
            Files.readString(sourceFile),
            symbolIndex
        );
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
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("declared thrown type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("caught type 'java.lang.String' must extend Throwable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Unhandled checked exception 'java.io.IOException'")));
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

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_UNCAUGHT_CHECKED_EXCEPTION".equals(d.code())),
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

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE".equals(d.code())));
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
    void coreDefiniteAssignmentRuleAllowsReassigningNonFinalParameters() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                String normalize(String value) {
                    value = value.trim();
                    return value;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(diagnostic.code())));
    }

    @Test
    void coreDefiniteAssignmentRuleRecognizesQualifiedFieldWritesAndConstructorDelegation() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                private final String name;
                private final int count;

                Example(String name) {
                    this(name, 1);
                }

                Example(String name, int count) {
                    this.name = name;
                    this.count = count;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreDefiniteAssignmentRuleRecognizesLombokGeneratedConstructors() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            @AllArgsConstructor
            final class Example {
                private final String name;
                private final int count;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realDelegatingAndLombokConstructorsInitializeFinalFields() throws Exception {
        List<Path> sourceFiles = List.of(
            Path.of("src/main/java/dev/railroadide/railroad/project/RailroadProject.java"),
            Path.of("src/main/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexer.java"),
            Path.of("src/main/java/dev/railroadide/railroad/project/License.java"),
            Path.of("src/main/java/dev/railroadide/railroad/form/FormTransformer.java")
        );
        List<String> errors = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            runProvider(new CoreDefiniteAssignmentInspection(), sourceFile, Files.readString(sourceFile)).stream()
                .filter(diagnostic -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code()))
                .map(diagnostic -> sourceFile + ": " + diagnostic.message())
                .forEach(errors::add);
        }

        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }

    @Test
    void realLoopTryAndSwitchAssignmentsDoNotReportUnassignedVariables() throws Exception {
        List<Path> sourceFiles = List.of(
            Path.of("src/main/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexer.java"),
            Path.of("src/main/java/dev/railroadide/railroad/ide/projectexplorer/task/WatchTask.java"),
            Path.of("src/main/java/dev/railroadide/railroad/config/ConfigHandler.java")
        );
        List<String> errors = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            String source = Files.readString(sourceFile);
            runProvider(new CoreDefiniteAssignmentInspection(), sourceFile, source).stream()
                .filter(diagnostic -> "SEM_UNASSIGNED_VARIABLE".equals(diagnostic.code()))
                .map(diagnostic -> sourceFile + ": " + diagnostic.message())
                .forEach(errors::add);
        }

        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
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
    void realRailroadStartupCodeDoesNotReportKnownFalseCallAndExceptionDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path railroadPath = sourceRoot.resolve("dev/railroadide/railroad/Railroad.java").normalize();
        Path preloaderPath = sourceRoot.resolve("dev/railroadide/railroad/RailroadPreloader.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> railroadCallDiagnostics =
            runProvider(new CoreCallResolutionInspection(), railroadPath, Files.readString(railroadPath), symbolIndex);
        assertFalse(railroadCallDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'InitializationStep'")),
            () -> railroadCallDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
        assertFalse(railroadCallDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'publish'")),
            () -> railroadCallDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> preloaderExceptionDiagnostics =
            runProvider(new CoreExceptionInspection(), preloaderPath, Files.readString(preloaderPath), symbolIndex);
        assertFalse(preloaderExceptionDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("ErrorNotification") && diagnostic.message().contains("must extend Throwable")));
    }

    @Test
    void projectEventBusPublishCallsResolveAcrossRealSources() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<String> unresolvedPublishDiagnostics = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path sourceFile : paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList()) {
                String source = Files.readString(sourceFile);
                if (!source.contains(".publish("))
                    continue;

                List<SemanticDiagnostic> diagnostics =
                    runProvider(new CoreCallResolutionInspection(), sourceFile, source, symbolIndex);
                diagnostics.stream()
                    .filter(diagnostic -> diagnostic.message().equals("Cannot resolve call 'publish'"))
                    .map(diagnostic -> sourceRoot.relativize(sourceFile) + ": " + diagnostic.message())
                    .forEach(unresolvedPublishDiagnostics::add);
            }
        }

        assertTrue(unresolvedPublishDiagnostics.isEmpty(), () -> String.join("\n", unresolvedPublishDiagnostics));
    }

    @Test
    void realRailroadPreloaderVarDeclarationsDoNotReportVoidAssignmentDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path preloaderPath = sourceRoot.resolve("dev/railroadide/railroad/RailroadPreloader.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics =
            runProvider(new CoreAssignmentInspection(), preloaderPath, Files.readString(preloaderPath), symbolIndex);

        assertFalse(diagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains(" to 'void'")),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realServicesAnonymousInterfacesAndGenericServiceLookupDoNotReportKnownFalseDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path servicesPath = sourceRoot.resolve("dev/railroadide/railroad/Services.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> callDiagnostics =
            runProvider(new CoreCallResolutionInspection(), servicesPath, Files.readString(servicesPath), symbolIndex);
        assertFalse(callDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'ApplicationInfoService'")
                    || diagnostic.message().equals("Cannot resolve call 'LocalizationService'")),
            () -> callDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> typeDiagnostics =
            runProvider(new CoreTypeResolutionInspection(), servicesPath, Files.readString(servicesPath), symbolIndex);
        assertFalse(typeDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve type 'T'")),
            () -> typeDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realDefaultGradleEnvironmentRecordAndObjectMembersDoNotReportKnownFalseDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path environmentPath = sourceRoot.resolve("dev/railroadide/railroad/DefaultGradleEnvironment.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(environmentPath);

        List<SemanticDiagnostic> callDiagnostics =
            runProvider(new CoreCallResolutionInspection(), environmentPath, source, symbolIndex);
        assertFalse(callDiagnostics.stream()
                .anyMatch(diagnostic -> Set.of(
                    "Cannot resolve call 'equals'",
                    "Cannot resolve call 'isUseWrapper'",
                    "Cannot resolve call 'getGradleUserHome'",
                    "Cannot resolve call 'getGradleJvm'",
                    "Cannot resolve call 'getConfigurations'",
                    "Cannot resolve call 'getVmOptions'",
                    "Cannot resolve call 'isDaemonEnabled'",
                    "Cannot resolve call 'getDaemonIdleTimeout'",
                    "Cannot resolve call 'getTask'",
                    "Cannot resolve call 'getGradleProjectPath'",
                    "Cannot resolve call 'getJavaHome'"
                ).contains(diagnostic.message())),
            () -> callDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> inheritanceDiagnostics =
            runProvider(new CoreInheritanceInspection(), environmentPath, source, symbolIndex);
        assertFalse(inheritanceDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("project()")),
            () -> inheritanceDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> assignmentDiagnostics =
            runProvider(new CoreDefiniteAssignmentInspection(), environmentPath, source, symbolIndex);
        assertFalse(assignmentDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("Variable 'project'")
                    || diagnostic.message().contains("Variable 'settings'")
                    || diagnostic.message().contains("Variable 'gradleInstallationPath'")),
            () -> assignmentDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> memberDiagnostics =
            runProvider(new CoreMemberResolutionInspection(), environmentPath, source, symbolIndex);
        assertFalse(memberDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve member 'length'")),
            () -> memberDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowManagerFullyQualifiedAccessAndOverloadedConstructorsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path windowManagerPath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowManager.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(windowManagerPath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreMemberResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreDuplicateDeclarationInspection(), windowManagerPath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'getPrimaryStage'",
            "Cannot resolve member 'Railroad'",
            "Cannot resolve member 'railroad'",
            "Cannot resolve member 'railroadide'",
            "Cannot resolve name 'dev'",
            "Duplicate declaration for 'WindowManager'"
        ).contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowEventsPublishCallsAndLambdaParametersResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path windowEventsPath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowEvents.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(windowEventsPath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), windowEventsPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), windowEventsPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreDuplicateDeclarationInspection(), windowEventsPath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.message().equals("Cannot resolve call 'publish'")
                    || diagnostic.message().equals("Cannot resolve name 'event'")
                    || diagnostic.message().equals("Duplicate declaration for 'event'")),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowBuilderFluentGenericsAndLambdaParametersResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowBuilder.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(sourcePath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'title'",
            "Cannot resolve call 'content'",
            "Cannot resolve call 'onClose'",
            "Cannot resolve call 'translateContent'",
            "Cannot resolve call 'onConfirm'",
            "Cannot resolve call 'onCancel'",
            "Cannot assign 'boolean' to 'dev.railroadide.railroad.window.DialogBuilder'"
        ).contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realCoreAccessibilityInspectionLambdaParameterCallsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/diagnostics/inspections/CoreAccessibilityInspection.java");
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        CompositeJavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(sourceFile);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>(runProvider(
            new CoreCallResolutionInspection(), sourceFile, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourceFile, source, symbolIndex));
        List<String> unresolved = diagnostics.stream()
            .filter(diagnostic -> diagnostic.message().contains("'kind'")
                || diagnostic.message().contains("'id'")
                || diagnostic.message().contains("Cannot assign 'java.util.Optional'"))
            .map(SemanticDiagnostic::message)
            .toList();

        assertTrue(unresolved.isEmpty(), () -> String.join(System.lineSeparator(), unresolved));
    }

    @Test
    void realNestedFormDataMembersRemainAccessibleFromTheirEnclosingComponent() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/form/impl/CheckBoxComponent.java");
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAccessibilityInspection(), sourceFile, Files.readString(sourceFile), symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_INACCESSIBLE_MEMBER".equals(diagnostic.code())),
            () -> diagnostics.stream().map(diagnostic ->
                    diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleInfersGenericMethodReturnsFromArgumentsAndClassLiterals() {
        String source = """
            class Example {
                static final class Jdk {}
                static class Node {}
                static final class Symbol {}
                interface Observable<T> { T getValue(); }

                static <T> T lookup(Class<T> type) {
                    return null;
                }

                java.util.Optional<Symbol> localSymbol(String ignored) {
                    return java.util.Optional.empty();
                }

                <U extends Node> void consumeBounded(Observable<U> observable) {
                    Node node = observable.getValue();
                }

                void run() {
                    String text = java.util.Objects.requireNonNull("value");
                    Jdk jdk = lookup(Jdk.class);
                    Symbol symbol = java.util.Optional.of("value")
                        .flatMap(this::localSymbol)
                        .orElse(null);
                    String[] values = null;
                    String first = values[0];
                    int[] bounds = null;
                    int open = bounds[0];
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> inferredTypes = new ArrayList<>();
        context.traverse(node -> {
            if (Set.of("JAVA_CLASS_LITERAL_EXPRESSION", "JAVA_METHOD_INVOCATION_EXPRESSION").contains(node.kind().id())) {
                inferredTypes.add(node.kind().id() + "="
                    + context.inferredType(node).map(type -> type.displayName() + type).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", inferredTypes));
    }

    @Test
    void assignmentRuleUsesTargetTypeForStaticImportedFunctionalOverloads() {
        String source = """
            package sample;

            import static sample.Assertions.call;

            class Assertions {
                interface Executable { void run(); }
                interface ValueSupplier<T> { T get(); }

                static void call(Executable executable) {}
                static <T> T call(ValueSupplier<T> supplier) { return supplier.get(); }
            }

            class Example {
                static final class Result {}
                static Result create() { return new Result(); }

                Result result = call(() -> create());
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleSupportsJavaBoxingAndUnboxingConversions() {
        String source = """
            class Example {
                void run() {
                    Integer boxed = 1;
                    int unboxed = boxed;
                    long widened = boxed;
                    Object object = 2;
                    boolean flag = Boolean.TRUE;
                    Long boxedLong = 5000L;
                    float singlePrecision = 1.0f;
                    int shifted = (1 << 1) >>> 1;
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> binaryTypes = new ArrayList<>();
        context.traverse(node -> {
            if ("JAVA_BINARY_EXPRESSION".equals(node.kind().id())) {
                binaryTypes.add(source.substring(node.start(), node.end()) + "="
                    + context.inferredType(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", binaryTypes));
    }

    @Test
    void assignmentRuleTreatsJvmAndSourceNestedTypeNamesAsEquivalent() {
        String source = "class Example {}";
        JavaRuleContext context = new JavaRuleContext(
            Path.of("Example.java"), source, JavaSemanticAnalyzer.analyzeFacts(source));

        assertTrue(context.isAssignable(
            new dev.railroadide.railroad.ide.sst.semantic.api.Type.DeclaredType("example.Outer.Inner", List.of()),
            new dev.railroadide.railroad.ide.sst.semantic.api.Type.DeclaredType("example.Outer$Inner", List.of())));
    }

    @Test
    void assignmentRuleSpecializesGenericMethodsInheritedThroughBinarySupertypes() throws Exception {
        String source = """
            import javafx.beans.property.SimpleStringProperty;
            import javafx.beans.property.StringProperty;

            class Example {
                String fromDeclaredProperty(StringProperty property) {
                    return property.getValue();
                }

                String fromConcreteProperty() {
                    SimpleStringProperty property = new SimpleStringProperty("value");
                    return property.getValue();
                }
            }
            """;
        Path javafxJar = Path.of(
            javafx.beans.property.StringProperty.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            JavaLibrarySymbolIndex.build(List.of(javafxJar)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), Path.of("Example.java"), source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleAcceptsLambdasAndMethodReferencesForFunctionalInterfaces() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Objects;
            import java.util.function.Function;
            import java.util.function.Predicate;
            import java.util.function.Supplier;

            enum Kind { VALUE }
            class Example {
                Supplier<List<String>> supplier = ArrayList::new;
                Predicate<String> predicate = Objects::nonNull;
                Function<Kind, String> function = Enum::name;
                Function<String, String> lambda = value -> value.trim();
                String invalid = String::trim;
            }
            """);

        List<SemanticDiagnostic> incompatible = diagnostics.stream()
            .filter(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code()))
            .toList();
        assertEquals(1, incompatible.size(),
            () -> incompatible.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
        assertTrue(incompatible.getFirst().message().contains("java.lang.String"));
    }

    @Test
    void assignmentRuleInfersArrayCreationTypesFromTheCreatedTypeAndDimensions() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), """
            class Example {
                void run() {
                    String[] strings = new String[0];
                    byte[] bytes = new byte[4];
                    int[][] matrix = new int[2][3];
                    String[][] initialized = new String[][]{{"value"}};
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleInfersGenericReturnsFromFunctionalArguments() {
        String source = """
            import java.util.Arrays;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.stream.Collectors;

            class Example {
                static class Outer {
                    static class Builder {}
                    static Builder builder() { return new Builder(); }
                }

                void run(String[] source) {
                    String mapped = Optional.of(" value ").map(String::trim).orElse(null);
                    String lambdaMapped = Optional.of("value").map(value -> value.trim()).orElse(null);
                    String[] copied = Arrays.stream(source).map(String::trim).toArray(String[]::new);
                    List<String> collected = Arrays.stream(source).collect(Collectors.toList());
                    Map<String, Integer> values = Map.of("value", 1);
                    String key = values.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                    Outer.Builder builder = Outer.builder();
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> inferredTypes = new ArrayList<>();
        context.traverse(node -> {
            if (Set.of("JAVA_METHOD_INVOCATION_EXPRESSION", "JAVA_METHOD_REFERENCE_EXPRESSION").contains(node.kind().id())) {
                inferredTypes.add(source.substring(node.start(), node.end()) + "="
                    + context.inferredType(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", inferredTypes));
    }

    @Test
    void callResolutionUsesFunctionalTargetTypesForPolyExpressionOverloads() {
        String source = """
            import java.util.Collection;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.Comparator;
            import java.util.List;
            import java.util.Map;
            import java.nio.file.Path;
            import java.util.function.BiConsumer;
            import java.util.function.Consumer;
            import java.util.function.Function;
            import java.util.function.Supplier;
            import java.util.function.UnaryOperator;

            class LambdaBase {
                static String namedThreadFactory(String name) { return name; }
            }

            class Example extends LambdaBase {
                static final class Row {
                    RuleDescriptor rule;
                    Box<String> severityOverride;
                }

                static final class Rule {
                    String id() { return "id"; }
                }

                record RuleDescriptor(String providerId, Rule rule) {}

                static final class Box<T> {
                    Box() {}
                    Box(Function<T, String> formatter) {}
                    T getValue() { return null; }
                }

                record Widget(String id) {}

                static class BaseWidget {}
                interface MutableWidget { void setValue(Object value); }
                static class GenericWidget<T extends BaseWidget & MutableWidget> {
                    T getComponent() { return null; }
                }
                interface Listener<T> {}
                static class Property<T> {
                    void removeListener(Listener<? super T> listener) {}
                }
                static class Reference<T> { T get() { return null; } }

                record FileResult(Path path) {}
                record ValidationEntry(Popup popup) {}
                static final class Popup { void hide() {} }
                enum Mode { DEFAULT }
                interface TextLength { int length(String text); }
                static class CommonPane { void common() {} }
                static final class HorizontalPane extends CommonPane {}
                static final class VerticalPane extends CommonPane {}
                static class ConfigurationData { String getName() { return "name"; } }
                static class Configuration<D extends ConfigurationData> {
                    D data() { return null; }
                }
                interface Module { List<String> getConfigurations(); }

                void items(Collection<String> values) {}
                void items(Supplier<Collection<String>> values) {}
                void listen(Consumer<String> listener) {}
                void listen(BiConsumer<String, String> listener) {}
                void transform(UnaryOperator<String> operator) {}
                void log(String format, Object... arguments) {}

                void flatten(List<? extends String> values) {
                    values.stream()
                        .flatMap(value -> java.util.stream.Stream.of(value.trim()))
                        .toList();
                }

                void nestedSourceFields(List<Row> rows) {
                    for (Row row : rows) {
                        row.rule.rule().id();
                        row.severityOverride.getValue().trim();
                    }
                }

                void nestedRecordBackingField(ValidationEntry entry) {
                    entry.popup.hide();
                }

                void comparatorFactory(ArrayList<FileResult> results) {
                    results.sort(Comparator.comparing(file -> file.path().toString()));
                    ArrayList<Map.Entry<Path, FileResult>> entries = new ArrayList<>();
                    entries.sort(Comparator.comparingLong(
                        (Map.Entry<Path, FileResult> entry) -> entry.getValue().path().toString().length()));
                    ArrayList<RuleDescriptor> descriptors = new ArrayList<>();
                    descriptors.sort(Comparator
                        .comparing(RuleDescriptor::providerId)
                        .thenComparing(rule -> rule.rule().id()));
                    Mode[] modes = Mode.values();
                }

                void contextualGenericLambdas(
                        List<Consumer<Row>> consumers,
                        Map<Widget, Row> rows,
                        Row row,
                        GenericWidget<?> genericWidget,
                        Property<Widget[]> property,
                        Reference<Listener<Widget[]>> listenerReference
                ) {
                    consumers.forEach(consumer -> consumer.accept(row));
                    for (Map.Entry<Widget, Row> entry : rows.entrySet())
                        entry.getKey().id().trim();
                    Box<Widget> box = new Box<>(widget -> widget.id().trim());
                    String[] versions = Arrays.stream(new int[] { 17, 21 })
                        .mapToObj(Integer::toString)
                        .toArray(String[]::new);
                    Comparator.<Widget, String>comparing(widget -> widget.id().trim());
                    genericWidget.getComponent().setValue("value");
                    property.removeListener(listenerReference.get());
                }

                Function<Widget, String> returnedLambda() {
                    return widget -> widget.id().trim();
                }

                void conditionalTargets(boolean horizontal) {
                    var pane = horizontal ? new HorizontalPane() : new VerticalPane();
                    pane.common();
                    TextLength length = horizontal ? null : text -> text.length();
                    namedThreadFactory("worker").trim();
                }

                void wildcardReceiverTypes(
                        Configuration<?> configuration,
                        Collection<? extends Module> modules
                ) {
                    configuration.data().getName().trim();
                    modules.stream()
                        .flatMap(module -> module.getConfigurations().stream())
                        .toList();
                }

                void laterDeclaredFields(List<Mode> modes, List<LaterRow> rows) {
                    for (Mode row : modes)
                        row.toString();
                    for (LaterRow row : rows) {
                        row.rule.rule().id();
                        row.severityOverride.getValue().trim();
                    }
                }

                void run() {
                    int clamped = Math.clamp(5, 0, 10);
                    String last = List.of("value").getLast();
                    items(List::of);
                    items(() -> List.of("value"));
                    items(List.of("value"));
                    listen((oldValue, newValue) -> newValue.trim());
                    transform(value -> value.trim());
                    List.of(" value ").stream()
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
                    List.of(Map.entry((CharSequence) "aa", 1), Map.entry((CharSequence) "b", 2))
                        .stream()
                        .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                        .toList();
                    log("value={}", 1);
                }

                static final class LaterRow {
                    RuleDescriptor rule;
                    Box<String> severityOverride;
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream()
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionContextuallyTypesAssignedLambdasAndPrefersFieldsOverMethods() {
        String source = """
            import java.util.function.Consumer;

            class Example {
                Consumer<String> handler;

                void handler() {}

                void run() {
                    handler = event -> event.trim();
                    handler.accept("value");
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionSpecializesUnqualifiedInheritedGenericMethods() {
        String source = """
            import java.util.List;
            import java.util.function.BiConsumer;

            class Base<T> {
                T get() { return null; }
                void listen(BiConsumer<T, T> listener) {}
                <U> String describe(U value) {
                    return value.toString() + value.getClass().getName();
                }
            }

            class Child extends Base<String> {
                void run() {
                    get().trim();
                    listen((oldValue, newValue) -> newValue.trim());
                }
            }

            interface Named {
                String name();
            }

            class NamedValue<T extends Named> {
                String lower(T value) {
                    return value.name().toLowerCase();
                }
            }

            interface Left {
                void left();
            }

            interface Right {
                void right();
            }

            class Intersection<T extends Left & Right> {
                void use(T value) {
                    value.left();
                    value.right();
                }
            }

            enum Mode {
                VALUE;

                String lower() {
                    return name().toLowerCase();
                }
            }

            class Iteration {
                void run(List<String> values, String[] array) {
                    for (var value : values) {
                        value.trim();
                    }
                    for (var value : array) {
                        value.trim();
                    }
                    Mode.VALUE.name().toLowerCase();
                }
            }

            class Holder {
                enum NestedMode {
                    VALUE;

                    NestedMode() {
                        name().toLowerCase();
                    }
                }
            }

            class GenericBuilder<T, N> {
                static <T, N> GenericBuilder<T, N> builder() {
                    return new GenericBuilder<>();
                }

                GenericBuilder<T, N> use(BiConsumer<T, N> consumer) {
                    return this;
                }
            }

            class ValueNode {
                void setValue(String value) {}
            }

            class ExplicitTypeArguments {
                void run() {
                    GenericBuilder.<String, ValueNode>builder()
                        .use((value, node) -> node.setValue(value));
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), Path.of("Example.java"), source,
            JavaJdkSymbolIndex.fromCurrentRuntime());

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionResolvesExplicitConstructorInvocations() {
        String source = """
            import java.util.ArrayList;
            import java.util.function.Consumer;
            import java.util.function.Function;
            import java.util.function.Supplier;

            class Base {
                Base(String name, Function<String, Integer> length) {}
            }

            class Box<T> {
                Box(Consumer<T> consumer, Supplier<T> supplier) {}
            }

            class Child extends Base {
                Child() {
                    super("value", value -> value.length());
                }

                <U> void box(Supplier<U> supplier) {
                    new Box<>(value -> value.toString(), supplier);
                }
            }

            class Outer {
                static class Inner extends ArrayList<String> {
                    boolean addValue() {
                        return super.add("value");
                    }
                }
            }

            interface LexerContract {
                record LexError(String message, int offset, int line, int column) {
                    LexError(String message, int offset) {
                        this(message, offset, 0, 0);
                    }
                }
            }

            class LexerImplementation implements LexerContract {
                LexError error() {
                    LexError shortError = new LexError("message", 1);
                    return new LexError(shortError.message(), shortError.offset(), 2, 3);
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleFindsSupertypesOfNestedTypesDeclaredInIndexedSources(@TempDir Path sourceRoot) throws Exception {
        Path contractFile = sourceRoot.resolve("api/Contract.java");
        Path keyFile = sourceRoot.resolve("api/Key.java");
        Path contextFile = sourceRoot.resolve("api/Context.java");
        Path keysFile = sourceRoot.resolve("api/Keys.java");
        Path containerFile = sourceRoot.resolve("impl/Container.java");
        Path useFile = sourceRoot.resolve("usage/Use.java");
        Files.createDirectories(contractFile.getParent());
        Files.createDirectories(containerFile.getParent());
        Files.createDirectories(useFile.getParent());
        Files.writeString(contractFile, """
            package api;
            public interface Contract {}
            """);
        Files.writeString(keyFile, """
            package api;
            public final class Key<T> {}
            """);
        Files.writeString(contextFile, """
            package api;
            public interface Context {
                <T> T get(Key<T> key);
            }
            """);
        Files.writeString(keysFile, """
            package api;
            public final class Keys {
                public static final Key<String> NAME = new Key<>();
            }
            """);
        Files.writeString(containerFile, """
            package impl;
            import api.Contract;
            public class Container {
                public record Value() implements Contract {}
            }
            """);
        String useSource = """
            package usage;
            import api.Contract;
            import api.Context;
            import api.Keys;
            import impl.Container;
            class Use {
                Contract value = new Container.Value();
                String read(Context context) {
                    return context.get(Keys.NAME);
                }
            }
            """;
        Files.writeString(useFile, useSource);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), useFile, useSource, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realFeatureEnvyMapEntryStreamChainRetainsItsKeyType() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/diagnostics/inspections/CoreFeatureEnvyInspection.java");
        String source = Files.readString(sourceFile);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), sourceFile, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path parserFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/sst/impl/java/JavaGreenParser.java");
        String parserSource = Files.readString(parserFile);
        List<SemanticDiagnostic> parserDiagnostics = runProvider(
            new CoreAssignmentInspection(), parserFile, parserSource, symbolIndex);
        assertFalse(parserDiagnostics.stream().anyMatch(diagnostic ->
                "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())
                    && diagnostic.message().contains("JavaTokenType")),
            () -> parserDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path indexerFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/language/impl/index/JavaProjectLanguageIndexer.java");
        String indexerSource = Files.readString(indexerFile);
        List<SemanticDiagnostic> indexerDiagnostics = runProvider(
            new CoreAssignmentInspection(), indexerFile, indexerSource, symbolIndex);
        assertFalse(indexerDiagnostics.stream().anyMatch(diagnostic ->
                "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> indexerDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path gitLocatorFile = sourceRoot.resolve(
            "dev/railroadide/railroad/vcs/git/util/GitLocator.java");
        String gitLocatorSource = Files.readString(gitLocatorFile);
        List<SemanticDiagnostic> gitLocatorDiagnostics = runProvider(
            new CoreAssignmentInspection(), gitLocatorFile, gitLocatorSource, symbolIndex);
        assertFalse(gitLocatorDiagnostics.stream().anyMatch(diagnostic ->
                "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> gitLocatorDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        Path formComponentFile = sourceRoot.resolve(
            "dev/railroadide/railroad/form/FormComponent.java");
        String formComponentSource = Files.readString(formComponentFile);
        List<SemanticDiagnostic> formComponentDiagnostics = runProvider(
            new CoreAssignmentInspection(), formComponentFile, formComponentSource, symbolIndex);
        assertFalse(formComponentDiagnostics.stream().anyMatch(diagnostic ->
                "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        String source = Files.readString(sourcePath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'toList'",
            "Cannot assign 'boolean' to 'java.lang.Runnable'",
            "Cannot assign 'javafx.scene.Node' to 'javafx.scene.layout.VBox'",
            "Cannot assign 'javafx.scene.Node' to 'javafx.scene.layout.HBox'"
        ).contains(diagnostic.message())),
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
            "Cannot resolve name 'ERROR'"
        ).contains(diagnostic.message())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
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
            "Cannot resolve name 'ERROR'"
        ).contains(diagnostic.message())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
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
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreNameResolutionInspection.java"),
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
            sourceRoot.resolve("dev/railroadide/railroad/form/impl/TextFieldComponent.java")
        );

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        for (Path sourcePath : sourcePaths) {
            String source = Files.readString(sourcePath);
            if (sourcePath.toString().contains("form" + File.separator + "impl")) {
                diagnostics.addAll(runProviders(List.of(
                    new CoreCallResolutionInspection(),
                    new CoreNameResolutionInspection(),
                    new CoreMemberResolutionInspection(),
                    new CoreAccessibilityInspection()
                ), sourcePath, source, symbolIndex));
            } else {
                diagnostics.addAll(runProvider(
                    new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
            }
        }

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                Set.of(
                    "SEM_UNRESOLVED_CALL",
                    "SEM_UNRESOLVED_NAME",
                    "SEM_UNRESOLVED_MEMBER",
                    "SEM_INACCESSIBLE_MEMBER"
                ).contains(diagnostic.code())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
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
            sourceRoot.resolve("dev/railroadide/railroad/ide/diagnostics/inspections/CoreNameResolutionInspection.java"),
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
            Path.of("src/test/java/dev/railroadide/railroad/plugin/PluginManagerTest.java").toAbsolutePath().normalize()
        );
        Set<String> errorCodes = Set.of(
            "SEM_UNRESOLVED_MEMBER",
            "SEM_INACCESSIBLE_MEMBER",
            "SEM_UNRESOLVED_TYPE",
            "SEM_UNRESOLVED_CALL",
            "SEM_UNRESOLVED_NAME",
            "SEM_INACCESSIBLE_CALL"
        );
        List<JavaInspectionRuleProvider> providers = List.of(
            new CoreMemberResolutionInspection(),
            new CoreTypeResolutionInspection(),
            new CoreCallResolutionInspection(),
            new CoreNameResolutionInspection(),
            new CoreAccessibilityInspection()
        );

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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
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
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/impl/index/JavaLanguageIndexContextContributor.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/projectexplorer/task/WatchTask.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/facet/detector/JavaFacetDetector.java"),
            sourceRoot.resolve("dev/railroadide/railroad/localization/L18n.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/onboarding/creation/service/ToolingGradleService.java"),
            sourceRoot.resolve("dev/railroadide/railroad/project/data/ProjectDataStore.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/TerminalFactory.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/RunConfiguration.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/runconfig/defaults/JavaApplicationRunConfigurationType.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaJdkSymbolIndex.java"),
            sourceRoot.resolve("dev/railroadide/railroad/plugin/defaults/FileSystemDocument.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/sst/project/JavaProjectSemanticIndexer.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/language/index/ProjectLanguageIndexService.java"),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaParserTestSupport.java").toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexerTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/plugin/PluginManagerTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/project/ProjectLanguageIndexServiceTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus/valid/06_statements_control_flow.java").toAbsolutePath().normalize()
        );

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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
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
            sourceRoot.resolve("dev/railroadide/railroad/project/creation/modjson/adapter/EntrypointListTypeAdapter.java"),
            sourceRoot.resolve("dev/railroadide/railroad/window/AlertBuilder.java"),
            Path.of("src/test/java/dev/railroadide/railroad/ide/language/index/ProjectLanguageIndexCoordinatorTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/impl/java/JavaProjectParityTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/java/dev/railroadide/railroad/ide/sst/project/ProjectLanguageIndexServiceTest.java").toAbsolutePath().normalize(),
            Path.of("src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus/valid/07_expressions_all_forms.java").toAbsolutePath().normalize()
        );

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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "Cannot resolve call 'name'".equals(diagnostic.message())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
                "Cannot resolve call 'LexError'",
                "Cannot resolve call 'getKey'",
                "Cannot resolve call 'length'"
            ).contains(diagnostic.message())),
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), sourcePath, source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "Cannot resolve call 'debug'".equals(diagnostic.message())
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
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        List<Path> sourcePaths = List.of(
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/setup/PaneIconBarFactory.java"),
            sourceRoot.resolve("dev/railroadide/railroad/ide/ui/codeeditor/CodeEditorPane.java")
        );

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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
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

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
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
        JavaRuleContext context = new JavaRuleContext(sourceFile, source, JavaSemanticAnalyzer.analyzeFacts(source));
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
    void projectWideCurrentRailroadSourcesHaveNoErrorDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path scanRoot = Path.of("src").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<Path> sourceFiles;
        String scanPathFilter = System.getenv("RAILROAD_SCAN_PATH");
        List<String> scanPathFragments = scanPathFilter == null || scanPathFilter.isBlank()
            ? List.of()
            : Arrays.stream(scanPathFilter.split(",")).map(String::trim).filter(fragment -> !fragment.isEmpty()).toList();
        try (Stream<Path> paths = Files.walk(scanRoot)) {
            sourceFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> !path.toString().contains(
                    "corpus" + File.separator + "recovery" + File.separator))
                .filter(path -> scanPathFragments.isEmpty()
                    || scanPathFragments.stream().anyMatch(fragment -> path.toString().contains(fragment)))
                .toList();
        }

        int parallelism = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        List<java.util.concurrent.Future<List<String>>> futures = new ArrayList<>();
        try {
            for (Path sourceFile : sourceFiles) {
                futures.add(executor.submit(() -> {
                    List<JavaInspectionRuleProvider> errorProviders = List.of(
                        new CoreAccessibilityInspection(),
                        new CoreAssignmentInspection(),
                        new CoreCallResolutionInspection(),
                        new CoreControlFlowInspection(),
                        new CoreDefiniteAssignmentInspection(),
                        new CoreDuplicateDeclarationInspection(),
                        new CoreExceptionInspection(),
                        new CoreImportInspection(),
                        new CoreInheritanceInspection(),
                        new CoreMemberResolutionInspection(),
                        new CoreModifierInspection(),
                        new CoreNameResolutionInspection(),
                        new CoreTypeResolutionInspection()
                    );
                    String source = Files.readString(sourceFile);
                    return runProviders(errorProviders, sourceFile, source, symbolIndex).stream()
                        .filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR)
                        .map(diagnostic -> scanRoot.relativize(sourceFile) + ":"
                            + diagnostic.startOffset() + " " + diagnostic.code() + " " + diagnostic.message())
                        .toList();
                }));
            }

            List<String> errors = new ArrayList<>();
            for (int index = 0; index < futures.size(); index++) {
                try {
                    errors.addAll(futures.get(index).get());
                } catch (java.util.concurrent.ExecutionException exception) {
                    throw new AssertionError("Failed to analyze " + sourceFiles.get(index), exception.getCause());
                }
            }
            assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
        } finally {
            executor.shutdownNow();
        }
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
        return runProvider(provider, filePath, document, null);
    }

    private static List<SemanticDiagnostic> runProvider(
        JavaInspectionRuleProvider provider,
        Path filePath,
        String document,
        JavaSymbolIndex symbolIndex
    ) {
        JavaInspectionRuleSettings.resetAll();
        var model = symbolIndex == null
            ? JavaSemanticAnalyzer.analyzeFacts(document)
            : JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        JavaRuleContext context = new JavaRuleContext(filePath, document, model, symbolIndex);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaInspectionReporter reporter = diagnostics::add;
        JavaInspectionRuleEngine.runRules(provider, context, reporter);
        return List.copyOf(diagnostics);
    }

    private static List<SemanticDiagnostic> runProviders(
        List<? extends JavaInspectionRuleProvider> providers,
        Path filePath,
        String document,
        JavaSymbolIndex symbolIndex
    ) {
        JavaInspectionRuleSettings.resetAll();
        var model = JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        JavaRuleContext context = new JavaRuleContext(filePath, document, model, symbolIndex);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaInspectionReporter reporter = diagnostics::add;
        providers.forEach(provider -> JavaInspectionRuleEngine.runRules(provider, context, reporter));
        return List.copyOf(diagnostics);
    }

    private static void assertRuleIds(JavaInspectionRuleProvider provider, Set<String> expectedIds) {
        Set<String> actual = provider.rules().stream().map(JavaInspectionRule::id).collect(Collectors.toSet());
        assertEquals(expectedIds, actual);
    }
}
