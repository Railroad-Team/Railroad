package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.diagnostics.inspections.*;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspection;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;

import java.util.List;

/**
 * Global registries for Java inspection extension points.
 */
public final class JavaInspectionRegistries {
    public static final CoreDuplicateDeclarationInspection CORE_DUPLICATE_DECLARATION_INSPECTION = new CoreDuplicateDeclarationInspection();
    public static final CoreImportInspection CORE_IMPORT_INSPECTION = new CoreImportInspection();
    public static final CoreNameResolutionInspection CORE_NAME_RESOLUTION_INSPECTION = new CoreNameResolutionInspection();
    public static final CoreTypeResolutionInspection CORE_TYPE_RESOLUTION_INSPECTION = new CoreTypeResolutionInspection();
    public static final CoreMemberResolutionInspection CORE_MEMBER_RESOLUTION_INSPECTION = new CoreMemberResolutionInspection();
    public static final CoreCallResolutionInspection CORE_CALL_RESOLUTION_INSPECTION = new CoreCallResolutionInspection();
    public static final CoreAccessibilityInspection CORE_ACCESSIBILITY_INSPECTION = new CoreAccessibilityInspection();
    public static final CoreInheritanceInspection CORE_INHERITANCE_INSPECTION = new CoreInheritanceInspection();
    public static final CoreModifierInspection CORE_MODIFIER_INSPECTION = new CoreModifierInspection();
    public static final CoreControlFlowInspection CORE_CONTROL_FLOW_INSPECTION = new CoreControlFlowInspection();
    public static final CoreExceptionInspection CORE_EXCEPTION_INSPECTION = new CoreExceptionInspection();
    public static final CoreDefiniteAssignmentInspection CORE_DEFINITE_ASSIGNMENT_INSPECTION = new CoreDefiniteAssignmentInspection();
    public static final CoreAssignmentInspection CORE_ASSIGNMENT_INSPECTION = new CoreAssignmentInspection();
    public static final CoreWildcardImportInspection CORE_WILDCARD_IMPORT_INSPECTION = new CoreWildcardImportInspection();
    public static final CoreEmptyCatchInspection CORE_EMPTY_CATCH_INSPECTION = new CoreEmptyCatchInspection();
    public static final CorePublicClassNotNamedAfterFileInspection CORE_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE_INSPECTION = new CorePublicClassNotNamedAfterFileInspection();
    public static final CoreLowerCaseClassNameInspection CORE_LOWER_CASE_CLASS_NAME_INSPECTION = new CoreLowerCaseClassNameInspection();
    public static final CoreMethodNamedTODOInspection CORE_METHOD_NAMED_TODO_INSPECTION = new CoreMethodNamedTODOInspection();
    public static final CoreMethodNamedUnderscoreInspection CORE_METHOD_NAMED_UNDERSCORE_INSPECTION = new CoreMethodNamedUnderscoreInspection();
    public static final CoreEmptySynchronizedInspection CORE_EMPTY_SYNCHRONIZED_INSPECTION = new CoreEmptySynchronizedInspection();
    public static final CoreEmptySwitchInspection CORE_EMPTY_SWITCH_INSPECTION = new CoreEmptySwitchInspection();
    public static final CoreUselessDefaultInSwitchInspection CORE_USELESS_DEFAULT_IN_SWITCH_INSPECTION = new CoreUselessDefaultInSwitchInspection();
    public static final CoreSingleLetterFieldNameInspection CORE_SINGLE_LETTER_FIELD_NAME_INSPECTION = new CoreSingleLetterFieldNameInspection();
    public static final CoreFieldNameSameAsClassInspection CORE_FIELD_NAME_SAME_AS_CLASS_INSPECTION = new CoreFieldNameSameAsClassInspection();
    public static final CoreParameterNamedUnderscoreInspection CORE_PARAMETER_NAMED_UNDERSCORE_INSPECTION = new CoreParameterNamedUnderscoreInspection();
    public static final CoreUnreachableCodeInspection CORE_UNREACHABLE_CODE_INSPECTION = new CoreUnreachableCodeInspection();
    public static final CoreOverlyStrongTypeCastInspection CORE_OVERLY_STRONG_TYPE_CAST_INSPECTION = new CoreOverlyStrongTypeCastInspection();
    public static final CoreCastConflictingWithInstanceofInspection CORE_CAST_CONFLICTING_WITH_INSTANCEOF_INSPECTION = new CoreCastConflictingWithInstanceofInspection();
    public static final CoreAssertionCanBeReplacedWithIfStatementInspection CORE_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT_INSPECTION = new CoreAssertionCanBeReplacedWithIfStatementInspection();
    public static final CoreFeatureEnvyInspection CORE_FEATURE_ENVY_INSPECTION = new CoreFeatureEnvyInspection();
    public static final CoreInitializationInspection CORE_INITIALIZATION_INSPECTION = new CoreInitializationInspection();
    public static final CoreAssertionWithSideEffectsInspection CORE_ASSERTION_WITH_SIDE_EFFECTS_INSPECTION = new CoreAssertionWithSideEffectsInspection();
    public static final CoreThisReferenceEscapedObjectConstructionInspection CORE_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION_INSPECTION = new CoreThisReferenceEscapedObjectConstructionInspection();
    public static final CoreNegativeHexIntInLongContextInspection CORE_NEGATIVE_HEX_INT_IN_LONG_CONTEXT_INSPECTION = new CoreNegativeHexIntInLongContextInspection();
    public static final CoreFieldCanBeLocalVariableInspection CORE_FIELD_CAN_BE_LOCAL_VARIABLE_INSPECTION = new CoreFieldCanBeLocalVariableInspection();
    public static final CoreFunctionalInterfaceInspection CORE_FUNCTIONAL_INTERFACE_INSPECTION = new CoreFunctionalInterfaceInspection();
    public static final CoreConstantConditionalExpressionInspection CORE_CONSTANT_CONDITIONAL_EXPRESSION_INSPECTION = new CoreConstantConditionalExpressionInspection();
    public static final CoreOptionalGetWithoutIsPresentCheckInspection CORE_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK_INSPECTION = new CoreOptionalGetWithoutIsPresentCheckInspection();
    public static final CoreInfiniteRecursionInspection CORE_INFINITE_RECURSION_INSPECTION = new CoreInfiniteRecursionInspection();
    public static final CoreAutoCloseableWithoutTryWithResourcesInspection CORE_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES_INSPECTION = new CoreAutoCloseableWithoutTryWithResourcesInspection();
    public static final CoreFallthroughCaseInSwitchInspection CORE_FALLTHROUGH_CASE_IN_SWITCH_INSPECTION = new CoreFallthroughCaseInSwitchInspection();
    public static final CoreIntegerDivisionInFloatingPointContextInspection CORE_INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT_INSPECTION = new CoreIntegerDivisionInFloatingPointContextInspection();

    public static final Registry<JavaInspection> JAVA_INSPECTION_REGISTRY =
        RegistryManager.createRegistry("railroad:java_inspection", JavaInspection.class);
    public static final Registry<JavaInspectionRuleProvider> JAVA_INSPECTION_RULE_PROVIDER_REGISTRY =
        RegistryManager.createRegistry("railroad:java_inspection_rule_provider", JavaInspectionRuleProvider.class);

    static {
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreDuplicateDeclarationInspection.ID, CORE_DUPLICATE_DECLARATION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreImportInspection.ID, CORE_IMPORT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreNameResolutionInspection.ID, CORE_NAME_RESOLUTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreTypeResolutionInspection.ID, CORE_TYPE_RESOLUTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreMemberResolutionInspection.ID, CORE_MEMBER_RESOLUTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreCallResolutionInspection.ID, CORE_CALL_RESOLUTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreAccessibilityInspection.ID, CORE_ACCESSIBILITY_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreInheritanceInspection.ID, CORE_INHERITANCE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreModifierInspection.ID, CORE_MODIFIER_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreControlFlowInspection.ID, CORE_CONTROL_FLOW_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreExceptionInspection.ID, CORE_EXCEPTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreDefiniteAssignmentInspection.ID, CORE_DEFINITE_ASSIGNMENT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreAssignmentInspection.ID, CORE_ASSIGNMENT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreWildcardImportInspection.ID, CORE_WILDCARD_IMPORT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreEmptyCatchInspection.ID, CORE_EMPTY_CATCH_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CorePublicClassNotNamedAfterFileInspection.ID, CORE_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreLowerCaseClassNameInspection.ID, CORE_LOWER_CASE_CLASS_NAME_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreMethodNamedTODOInspection.ID, CORE_METHOD_NAMED_TODO_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreMethodNamedUnderscoreInspection.ID, CORE_METHOD_NAMED_UNDERSCORE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreEmptySynchronizedInspection.ID, CORE_EMPTY_SYNCHRONIZED_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreEmptySwitchInspection.ID, CORE_EMPTY_SWITCH_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreUselessDefaultInSwitchInspection.ID, CORE_USELESS_DEFAULT_IN_SWITCH_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreSingleLetterFieldNameInspection.ID, CORE_SINGLE_LETTER_FIELD_NAME_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreFieldNameSameAsClassInspection.ID, CORE_FIELD_NAME_SAME_AS_CLASS_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreParameterNamedUnderscoreInspection.ID, CORE_PARAMETER_NAMED_UNDERSCORE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreUnreachableCodeInspection.ID, CORE_UNREACHABLE_CODE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreOverlyStrongTypeCastInspection.ID, CORE_OVERLY_STRONG_TYPE_CAST_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreCastConflictingWithInstanceofInspection.ID, CORE_CAST_CONFLICTING_WITH_INSTANCEOF_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreAssertionCanBeReplacedWithIfStatementInspection.ID, CORE_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreFeatureEnvyInspection.ID, CORE_FEATURE_ENVY_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreInitializationInspection.ID, CORE_INITIALIZATION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreAssertionWithSideEffectsInspection.ID, CORE_ASSERTION_WITH_SIDE_EFFECTS_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreThisReferenceEscapedObjectConstructionInspection.ID, CORE_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreNegativeHexIntInLongContextInspection.ID, CORE_NEGATIVE_HEX_INT_IN_LONG_CONTEXT_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreFieldCanBeLocalVariableInspection.ID, CORE_FIELD_CAN_BE_LOCAL_VARIABLE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreFunctionalInterfaceInspection.ID, CORE_FUNCTIONAL_INTERFACE_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreConstantConditionalExpressionInspection.ID, CORE_CONSTANT_CONDITIONAL_EXPRESSION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreOptionalGetWithoutIsPresentCheckInspection.ID, CORE_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreInfiniteRecursionInspection.ID, CORE_INFINITE_RECURSION_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreAutoCloseableWithoutTryWithResourcesInspection.ID, CORE_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreFallthroughCaseInSwitchInspection.ID, CORE_FALLTHROUGH_CASE_IN_SWITCH_INSPECTION);
        JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(CoreIntegerDivisionInFloatingPointContextInspection.ID, CORE_INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT_INSPECTION);
    }

    private JavaInspectionRegistries() {
    }

    public static List<JavaInspectionRuleProvider> coreRuleProviders() {
        return List.of(
            CORE_DUPLICATE_DECLARATION_INSPECTION,
            CORE_IMPORT_INSPECTION,
            CORE_NAME_RESOLUTION_INSPECTION,
            CORE_TYPE_RESOLUTION_INSPECTION,
            CORE_MEMBER_RESOLUTION_INSPECTION,
            CORE_CALL_RESOLUTION_INSPECTION,
            CORE_ACCESSIBILITY_INSPECTION,
            CORE_INHERITANCE_INSPECTION,
            CORE_MODIFIER_INSPECTION,
            CORE_CONTROL_FLOW_INSPECTION,
            CORE_EXCEPTION_INSPECTION,
            CORE_DEFINITE_ASSIGNMENT_INSPECTION,
            CORE_ASSIGNMENT_INSPECTION,
            CORE_WILDCARD_IMPORT_INSPECTION,
            CORE_EMPTY_CATCH_INSPECTION,
            CORE_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE_INSPECTION,
            CORE_LOWER_CASE_CLASS_NAME_INSPECTION,
            CORE_METHOD_NAMED_TODO_INSPECTION,
            CORE_METHOD_NAMED_UNDERSCORE_INSPECTION,
            CORE_EMPTY_SYNCHRONIZED_INSPECTION,
            CORE_EMPTY_SWITCH_INSPECTION,
            CORE_USELESS_DEFAULT_IN_SWITCH_INSPECTION,
            CORE_SINGLE_LETTER_FIELD_NAME_INSPECTION,
            CORE_FIELD_NAME_SAME_AS_CLASS_INSPECTION,
            CORE_PARAMETER_NAMED_UNDERSCORE_INSPECTION,
            CORE_UNREACHABLE_CODE_INSPECTION,
            CORE_OVERLY_STRONG_TYPE_CAST_INSPECTION,
            CORE_CAST_CONFLICTING_WITH_INSTANCEOF_INSPECTION,
            CORE_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT_INSPECTION,
            CORE_FEATURE_ENVY_INSPECTION,
            CORE_INITIALIZATION_INSPECTION,
            CORE_ASSERTION_WITH_SIDE_EFFECTS_INSPECTION,
            CORE_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION_INSPECTION,
            CORE_NEGATIVE_HEX_INT_IN_LONG_CONTEXT_INSPECTION,
            CORE_FIELD_CAN_BE_LOCAL_VARIABLE_INSPECTION,
            CORE_FUNCTIONAL_INTERFACE_INSPECTION,
            CORE_CONSTANT_CONDITIONAL_EXPRESSION_INSPECTION,
            CORE_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK_INSPECTION,
            CORE_INFINITE_RECURSION_INSPECTION,
            CORE_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES_INSPECTION,
            CORE_FALLTHROUGH_CASE_IN_SWITCH_INSPECTION,
            CORE_INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT_INSPECTION
        );
    }
}
