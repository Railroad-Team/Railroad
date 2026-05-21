package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisteredInspection
public final class CoreModifierInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-modifiers";

    private static final String JAVA_CLASS_DECLARATION = "JAVA_CLASS_DECLARATION";
    private static final String JAVA_INTERFACE_DECLARATION = "JAVA_INTERFACE_DECLARATION";
    private static final String JAVA_ENUM_DECLARATION = "JAVA_ENUM_DECLARATION";
    private static final String JAVA_ANNOTATION_TYPE_DECLARATION = "JAVA_ANNOTATION_TYPE_DECLARATION";
    private static final String JAVA_RECORD_DECLARATION = "JAVA_RECORD_DECLARATION";
    private static final String JAVA_FIELD_DECLARATION = "JAVA_FIELD_DECLARATION";
    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_RECORD_COMPACT_CONSTRUCTOR = "JAVA_RECORD_COMPACT_CONSTRUCTOR";
    private static final String JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT = "JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT";
    private static final String JAVA_PARAMETER = "JAVA_PARAMETER";
    private static final String JAVA_RECORD_COMPONENT = "JAVA_RECORD_COMPONENT";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";

    private static final List<JavaInspectionRule> RULES = List.of(
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.ILLEGAL_MODIFIER.id(),
                    JavaSemanticRules.ILLEGAL_MODIFIER.defaultSeverity(),
                    JavaSemanticRules.ILLEGAL_MODIFIER.messageTemplate(),
                    Set.of("core", "modifiers"),
                    CoreModifierInspection::reportIllegalModifiers
            )
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportIllegalModifiers(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            switch (node.kind().id()) {
                case JAVA_CLASS_DECLARATION, JAVA_INTERFACE_DECLARATION, JAVA_ENUM_DECLARATION,
                     JAVA_ANNOTATION_TYPE_DECLARATION, JAVA_RECORD_DECLARATION ->
                        reportTypeDeclaration(context, reporter, node);
                case JAVA_FIELD_DECLARATION -> reportFieldDeclaration(context, reporter, node);
                case JAVA_METHOD_DECLARATION -> reportMethodDeclaration(context, reporter, node);
                case JAVA_CONSTRUCTOR_DECLARATION, JAVA_RECORD_COMPACT_CONSTRUCTOR ->
                        reportConstructorDeclaration(context, reporter, node);
                case JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT -> reportLocalVariableDeclaration(context, reporter, node);
                case JAVA_PARAMETER -> reportParameter(context, reporter, node);
                case JAVA_RECORD_COMPONENT -> reportRecordComponent(context, reporter, node);
                default -> {
                }
            }
        });
    }

    private static void reportTypeDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        Map<JavaTokenType, Integer> modifierCounts = context.directModifierTokenCounts(node);
        Set<JavaTokenType> modifiers = modifierCounts.keySet();
        if (modifiers.isEmpty())
            return;

        reportDuplicateModifiers(reporter, node, modifierCounts);
        reportConflictingAccessModifiers(reporter, node, modifiers);

        if (context.enclosingTypeSymbol(node).isEmpty()) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'private' is not allowed on top-level type declarations");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on top-level type declarations");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STATIC_KEYWORD, "'static' is not allowed on top-level type declarations");
        }
        String ownerKindId = ownerDeclarationKind(context, node);
        if (JAVA_INTERFACE_DECLARATION.equals(ownerKindId) || JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerKindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'private' is not allowed on interface member types");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on interface member types");
        }

        reportIfPresent(reporter, node, modifiers, JavaTokenType.DEFAULT_KEYWORD, "'default' is not allowed on type declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on type declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on type declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.TRANSIENT_KEYWORD, "'transient' is not allowed on type declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.VOLATILE_KEYWORD, "'volatile' is not allowed on type declarations");

        String kindId = node.kind().id();
        if (modifiers.contains(JavaTokenType.ABSTRACT_KEYWORD) && modifiers.contains(JavaTokenType.FINAL_KEYWORD))
            reporter.report(node, "'abstract' cannot be combined with 'final'");
        if (modifiers.contains(JavaTokenType.SEALED_KEYWORD) && modifiers.contains(JavaTokenType.NON_SEALED_KEYWORD))
            reporter.report(node, "'sealed' cannot be combined with 'non-sealed'");
        if (modifiers.contains(JavaTokenType.FINAL_KEYWORD) && modifiers.contains(JavaTokenType.SEALED_KEYWORD))
            reporter.report(node, "'final' cannot be combined with 'sealed'");
        if (modifiers.contains(JavaTokenType.FINAL_KEYWORD) && modifiers.contains(JavaTokenType.NON_SEALED_KEYWORD))
            reporter.report(node, "'final' cannot be combined with 'non-sealed'");

        if (JAVA_INTERFACE_DECLARATION.equals(kindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on interface declarations");
        } else if (JAVA_ANNOTATION_TYPE_DECLARATION.equals(kindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on annotation type declarations");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SEALED_KEYWORD, "'sealed' is not allowed on annotation type declarations");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NON_SEALED_KEYWORD, "'non-sealed' is not allowed on annotation type declarations");
        } else if (JAVA_ENUM_DECLARATION.equals(kindId) || JAVA_RECORD_DECLARATION.equals(kindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.ABSTRACT_KEYWORD, "'abstract' is not allowed on %s declarations".formatted(typeKindDisplayName(kindId)));
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on %s declarations".formatted(typeKindDisplayName(kindId)));
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SEALED_KEYWORD, "'sealed' is not allowed on %s declarations".formatted(typeKindDisplayName(kindId)));
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NON_SEALED_KEYWORD, "'non-sealed' is not allowed on %s declarations".formatted(typeKindDisplayName(kindId)));
        }
    }

    private static void reportFieldDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        Map<JavaTokenType, Integer> modifierCounts = context.directModifierTokenCounts(node);
        Set<JavaTokenType> modifiers = modifierCounts.keySet();
        if (modifiers.isEmpty())
            return;

        reportDuplicateModifiers(reporter, node, modifierCounts);
        reportConflictingAccessModifiers(reporter, node, modifiers);
        reportIfPresent(reporter, node, modifiers, JavaTokenType.ABSTRACT_KEYWORD, "'abstract' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.DEFAULT_KEYWORD, "'default' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.STRICTFP_KEYWORD, "'strictfp' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SEALED_KEYWORD, "'sealed' is not allowed on field declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NON_SEALED_KEYWORD, "'non-sealed' is not allowed on field declarations");

        String ownerKindId = ownerDeclarationKind(context, node);
        if (JAVA_INTERFACE_DECLARATION.equals(ownerKindId) || JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerKindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'private' is not allowed on interface fields");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on interface fields");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.TRANSIENT_KEYWORD, "'transient' is not allowed on interface fields");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.VOLATILE_KEYWORD, "'volatile' is not allowed on interface fields");
        }

        if (modifiers.contains(JavaTokenType.FINAL_KEYWORD) && modifiers.contains(JavaTokenType.VOLATILE_KEYWORD))
            reporter.report(node, "'final' cannot be combined with 'volatile' on fields");
    }

    private static void reportMethodDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        Map<JavaTokenType, Integer> modifierCounts = context.directModifierTokenCounts(node);
        Set<JavaTokenType> modifiers = modifierCounts.keySet();
        if (modifiers.isEmpty())
            return;

        reportDuplicateModifiers(reporter, node, modifierCounts);
        reportConflictingAccessModifiers(reporter, node, modifiers);
        reportIfPresent(reporter, node, modifiers, JavaTokenType.TRANSIENT_KEYWORD, "'transient' is not allowed on method declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.VOLATILE_KEYWORD, "'volatile' is not allowed on method declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SEALED_KEYWORD, "'sealed' is not allowed on method declarations");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NON_SEALED_KEYWORD, "'non-sealed' is not allowed on method declarations");

        if (modifiers.contains(JavaTokenType.STATIC_KEYWORD) && modifiers.contains(JavaTokenType.FINAL_KEYWORD))
            reporter.report(node, "'static' cannot be combined with 'final' on methods");

        boolean hasBody = context.directChild(node, JAVA_BLOCK) != null;
        String ownerKindId = ownerDeclarationKind(context, node);

        if (modifiers.contains(JavaTokenType.ABSTRACT_KEYWORD)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'abstract' cannot be combined with 'private'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STATIC_KEYWORD, "'abstract' cannot be combined with 'static'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'abstract' cannot be combined with 'final'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'abstract' cannot be combined with 'native'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'abstract' cannot be combined with 'synchronized'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STRICTFP_KEYWORD, "'abstract' cannot be combined with 'strictfp'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.DEFAULT_KEYWORD, "'abstract' cannot be combined with 'default'");
            if (hasBody)
                reporter.report(node, "'abstract' methods cannot have a body");
            String ownerQualifiedName = context.enclosingTypeSymbol(node).flatMap(symbol -> symbol.qualifiedName()).orElse(null);
            if (ownerQualifiedName != null
                    && JAVA_CLASS_DECLARATION.equals(ownerKindId)
                    && !context.isAbstractType(ownerQualifiedName)) {
                reporter.report(node, "'abstract' methods are only allowed in abstract classes and interfaces");
            }
            if (JAVA_ENUM_DECLARATION.equals(ownerKindId) || JAVA_RECORD_DECLARATION.equals(ownerKindId)) {
                reporter.report(node, "'abstract' methods are not allowed in %s types".formatted(typeKindDisplayName(ownerKindId)));
            }
        }

        boolean implicitAbstractLike = isImplicitAbstractLike(ownerKindId, modifiers, hasBody);
        if (implicitAbstractLike) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on abstract interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on abstract interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on abstract interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on abstract interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STRICTFP_KEYWORD, "'strictfp' is not allowed on abstract interface methods");
        }

        if (JAVA_INTERFACE_DECLARATION.equals(ownerKindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on interface methods");
            if (modifiers.contains(JavaTokenType.PRIVATE_KEYWORD) && !hasBody)
                reporter.report(node, "'private' interface methods must have a body");
            if (modifiers.contains(JavaTokenType.STATIC_KEYWORD) && !hasBody)
                reporter.report(node, "'static' interface methods must have a body");
        }

        if (JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerKindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'private' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STATIC_KEYWORD, "'static' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on annotation type elements");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STRICTFP_KEYWORD, "'strictfp' is not allowed on annotation type elements");
            if (hasBody)
                reporter.report(node, "annotation type elements cannot have a body");
        }

        if (modifiers.contains(JavaTokenType.DEFAULT_KEYWORD)) {
            if (!JAVA_INTERFACE_DECLARATION.equals(ownerKindId))
                reporter.report(node, "'default' is only allowed on interface methods");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.STATIC_KEYWORD, "'default' cannot be combined with 'static'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PRIVATE_KEYWORD, "'default' cannot be combined with 'private'");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'default' cannot be combined with 'final'");
            if (!hasBody)
                reporter.report(node, "'default' methods must have a body");
        }

        if (modifiers.contains(JavaTokenType.NATIVE_KEYWORD) && hasBody)
            reporter.report(node, "'native' methods cannot have a body");
    }

    private static void reportConstructorDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        Map<JavaTokenType, Integer> modifierCounts = context.directModifierTokenCounts(node);
        Set<JavaTokenType> modifiers = modifierCounts.keySet();
        if (modifiers.isEmpty())
            return;

        reportDuplicateModifiers(reporter, node, modifierCounts);
        reportConflictingAccessModifiers(reporter, node, modifiers);
        reportIfPresent(reporter, node, modifiers, JavaTokenType.ABSTRACT_KEYWORD, "'abstract' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.STATIC_KEYWORD, "'static' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.FINAL_KEYWORD, "'final' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.DEFAULT_KEYWORD, "'default' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NATIVE_KEYWORD, "'native' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SYNCHRONIZED_KEYWORD, "'synchronized' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.TRANSIENT_KEYWORD, "'transient' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.VOLATILE_KEYWORD, "'volatile' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.SEALED_KEYWORD, "'sealed' is not allowed on constructors");
        reportIfPresent(reporter, node, modifiers, JavaTokenType.NON_SEALED_KEYWORD, "'non-sealed' is not allowed on constructors");

        String ownerKindId = ownerDeclarationKind(context, node);
        if (JAVA_INTERFACE_DECLARATION.equals(ownerKindId))
            reporter.report(node, "interfaces cannot declare constructors");
        if (JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerKindId))
            reporter.report(node, "annotation types cannot declare constructors");
        if (JAVA_ENUM_DECLARATION.equals(ownerKindId)) {
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PUBLIC_KEYWORD, "'public' is not allowed on enum constructors");
            reportIfPresent(reporter, node, modifiers, JavaTokenType.PROTECTED_KEYWORD, "'protected' is not allowed on enum constructors");
        }
    }

    private static void reportLocalVariableDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        reportRestrictedModifierContext(
                context,
                reporter,
                node,
                "local variables",
                Set.of(JavaTokenType.FINAL_KEYWORD)
        );
    }

    private static void reportParameter(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        reportRestrictedModifierContext(
                context,
                reporter,
                node,
                "parameters",
                Set.of(JavaTokenType.FINAL_KEYWORD)
        );
    }

    private static void reportRecordComponent(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        reportRestrictedModifierContext(
                context,
                reporter,
                node,
                "record components",
                Set.of()
        );
    }

    private static void reportRestrictedModifierContext(
            JavaRuleContext context,
            JavaInspectionRuleReporter reporter,
            SyntaxNode node,
            String subject,
            Set<JavaTokenType> allowedModifiers
    ) {
        Map<JavaTokenType, Integer> modifierCounts = context.directModifierTokenCounts(node);
        if (modifierCounts.isEmpty())
            return;

        reportDuplicateModifiers(reporter, node, modifierCounts);
        for (JavaTokenType modifier : modifierCounts.keySet()) {
            if (!allowedModifiers.contains(modifier))
                reporter.report(node, "'%s' is not allowed on %s".formatted(modifierDisplayName(modifier), subject));
        }
    }

    private static void reportDuplicateModifiers(
            JavaInspectionRuleReporter reporter,
            SyntaxNode node,
            Map<JavaTokenType, Integer> modifierCounts
    ) {
        modifierCounts.forEach((modifier, count) -> {
            if (count > 1)
                reporter.report(node, "duplicate modifier '%s'".formatted(modifierDisplayName(modifier)));
        });
    }

    private static void reportConflictingAccessModifiers(JavaInspectionRuleReporter reporter, SyntaxNode node, Set<JavaTokenType> modifiers) {
        List<String> accessModifiers = new ArrayList<>();
        if (modifiers.contains(JavaTokenType.PUBLIC_KEYWORD))
            accessModifiers.add("public");
        if (modifiers.contains(JavaTokenType.PROTECTED_KEYWORD))
            accessModifiers.add("protected");
        if (modifiers.contains(JavaTokenType.PRIVATE_KEYWORD))
            accessModifiers.add("private");
        if (accessModifiers.size() > 1)
            reporter.report(node, "conflicting access modifiers: %s".formatted(String.join(", ", accessModifiers)));
    }

    private static void reportIfPresent(
            JavaInspectionRuleReporter reporter,
            SyntaxNode node,
            Set<JavaTokenType> modifiers,
            JavaTokenType modifier,
            String message
    ) {
        if (modifiers.contains(modifier))
            reporter.report(node, message);
    }

    private static boolean isImplicitAbstractLike(String ownerKindId, Set<JavaTokenType> modifiers, boolean hasBody) {
        return (JAVA_INTERFACE_DECLARATION.equals(ownerKindId) || JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerKindId))
                && !hasBody
                && !modifiers.contains(JavaTokenType.DEFAULT_KEYWORD)
                && !modifiers.contains(JavaTokenType.STATIC_KEYWORD)
                && !modifiers.contains(JavaTokenType.PRIVATE_KEYWORD);
    }

    private static String ownerDeclarationKind(JavaRuleContext context, SyntaxNode node) {
        return context.enclosingTypeSymbol(node)
                .flatMap(type -> type.declaration())
                .map(owner -> owner.kind().id())
                .orElse(null);
    }

    private static String modifierDisplayName(JavaTokenType modifier) {
        return switch (modifier) {
            case PUBLIC_KEYWORD -> "public";
            case PROTECTED_KEYWORD -> "protected";
            case PRIVATE_KEYWORD -> "private";
            case ABSTRACT_KEYWORD -> "abstract";
            case DEFAULT_KEYWORD -> "default";
            case FINAL_KEYWORD -> "final";
            case STATIC_KEYWORD -> "static";
            case STRICTFP_KEYWORD -> "strictfp";
            case SYNCHRONIZED_KEYWORD -> "synchronized";
            case NATIVE_KEYWORD -> "native";
            case TRANSIENT_KEYWORD -> "transient";
            case VOLATILE_KEYWORD -> "volatile";
            case SEALED_KEYWORD -> "sealed";
            case NON_SEALED_KEYWORD -> "non-sealed";
            default -> modifier.name();
        };
    }

    private static String typeKindDisplayName(String kindId) {
        return switch (kindId) {
            case JAVA_ENUM_DECLARATION -> "enum";
            case JAVA_RECORD_DECLARATION -> "record";
            default -> "type";
        };
    }
}
