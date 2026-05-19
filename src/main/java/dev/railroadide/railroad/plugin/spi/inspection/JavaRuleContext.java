package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.classparser.Type.*;
import dev.railroadide.railroad.ide.classparser.Type.ArrayType;
import dev.railroadide.railroad.ide.classparser.Type.PrimitiveType;
import dev.railroadide.railroad.ide.classparser.Type.WildcardType;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.ConstructorStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.Type.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Stable rule evaluation context with semantic model access and Java-specific helpers.
 * <p>
 * This is the main convenience API for writing Java inspections. It wraps the parsed file,
 * semantic model, and a large set of helper operations that cover common inspection tasks:
 * <ul>
 *     <li>walking the syntax tree via {@link #traverse(Consumer)}</li>
 *     <li>querying nodes by parser kind via {@link #nodesOfKind(String)}</li>
 *     <li>reading declared and resolved symbols</li>
 *     <li>reading inferred types</li>
 *     <li>extracting names, packages, modifiers, and Java-specific structure</li>
 * </ul>
 * <p>
 * Recommended workflow for a new inspection:
 * <ol>
 *     <li>Find candidate nodes using {@link #nodesOfKind(String)} or {@link #traverse(Consumer)}.</li>
 *     <li>Use {@link #resolvedSymbol(SyntaxNode)}, {@link #declaredSymbol(SyntaxNode)}, and
 *     {@link #inferredType(SyntaxNode)} only when semantic information is required.</li>
 *     <li>Report diagnostics against the narrowest relevant syntax node.</li>
 * </ol>
 */
public final class JavaRuleContext implements LanguageRuleContext {
    public static final int DEFAULT_MODIFIER = 0x00010000;
    public static final int SEALED_MODIFIER = 0x00020000;
    public static final int NON_SEALED_MODIFIER = 0x00040000;

    private static final Set<String> NUMERIC_PRIMITIVES = Set.of("byte", "short", "char", "int", "long", "float", "double");
    private static final String JAVA_IMPORT_DECLARATION = "JAVA_IMPORT_DECLARATION";
    private static final String JAVA_IMPORT_TARGET = "JAVA_IMPORT_TARGET";
    private static final String JAVA_PARAMETER_LIST = "JAVA_PARAMETER_LIST";
    private static final String JAVA_PARAMETER = "JAVA_PARAMETER";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String JAVA_METHOD_INVOCATION_EXPRESSION = "JAVA_METHOD_INVOCATION_EXPRESSION";
    private static final String JAVA_ARGUMENT_LIST = "JAVA_ARGUMENT_LIST";
    private static final String JAVA_PACKAGE_DECLARATION = "JAVA_PACKAGE_DECLARATION";
    private static final String JAVA_QUALIFIED_NAME = "JAVA_QUALIFIED_NAME";
    private static final String JAVA_EXTENDS_CLAUSE = "JAVA_EXTENDS_CLAUSE";
    private static final String JAVA_IMPLEMENTS_CLAUSE = "JAVA_IMPLEMENTS_CLAUSE";
    private static final String JAVA_CLASS_DECLARATION = "JAVA_CLASS_DECLARATION";
    private static final String JAVA_INTERFACE_DECLARATION = "JAVA_INTERFACE_DECLARATION";
    private static final String JAVA_ENUM_DECLARATION = "JAVA_ENUM_DECLARATION";
    private static final String JAVA_ANNOTATION_TYPE_DECLARATION = "JAVA_ANNOTATION_TYPE_DECLARATION";
    private static final String JAVA_RECORD_DECLARATION = "JAVA_RECORD_DECLARATION";
    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_RECORD_COMPACT_CONSTRUCTOR = "JAVA_RECORD_COMPACT_CONSTRUCTOR";
    private static final String JAVA_THROWS_CLAUSE = "JAVA_THROWS_CLAUSE";
    private static final String JAVA_UNION_TYPE_REFERENCE = "JAVA_UNION_TYPE_REFERENCE";
    private static final String JAVA_CATCH_CLAUSE = "JAVA_CATCH_CLAUSE";
    private static final String JAVA_CLASS_INSTANCE_CREATION_EXPRESSION = "JAVA_CLASS_INSTANCE_CREATION_EXPRESSION";
    private static final String JAVA_LAMBDA_EXPRESSION = "JAVA_LAMBDA_EXPRESSION";
    private static final String JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT = "JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT";
    private static final String JAVA_VARIABLE_DECLARATOR = "JAVA_VARIABLE_DECLARATOR";
    private static final String JAVA_TRY_RESOURCE = "JAVA_TRY_RESOURCE";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";

    private final Path filePath;
    private final String documentText;
    private final SemanticModel semanticModel;
    private volatile @Nullable ImportIndex cachedImportIndex;
    private volatile @Nullable Set<String> cachedAvailableTypeNames;
    private volatile @Nullable String cachedCurrentPackageName;
    private volatile @Nullable Map<String, SyntaxNode> cachedLocalTypeDeclarations;
    private volatile @Nullable Map<String, Symbol> cachedLocalTypeSymbolsByQualifiedName;
    private volatile @Nullable Map<String, List<String>> cachedDirectSuperTypesByQualifiedName;
    private volatile @Nullable Map<String, List<FieldDescriptor>> cachedDeclaredFieldsByOwner;
    private volatile @Nullable Map<String, List<MethodDescriptor>> cachedDeclaredMethodsByOwner;

    /**
     * Creates a rule context from raw file and semantic analysis inputs.
     *
     * @param filePath      file path being inspected
     * @param documentText  full source text
     * @param semanticModel semantic model for the file
     * @throws NullPointerException if any argument is {@code null}
     */
    public JavaRuleContext(Path filePath, String documentText, SemanticModel semanticModel) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.documentText = Objects.requireNonNull(documentText, "documentText");
        this.semanticModel = Objects.requireNonNull(semanticModel, "semanticModel");
    }

    public Map<String, SyntaxNode> localTypeDeclarations() {
        Map<String, SyntaxNode> cached = cachedLocalTypeDeclarations;
        if (cached != null)
            return cached;

        Map<String, SyntaxNode> result = new LinkedHashMap<>();
        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName != null && isTypeSymbol(symbol.kind()))
                result.putIfAbsent(qualifiedName, node);
        }));

        Map<String, SyntaxNode> copy = Map.copyOf(result);
        cachedLocalTypeDeclarations = copy;
        return copy;
    }

    public @Nullable SyntaxNode forBodyOf(SyntaxNode forNode) {
        boolean seenHeader = false;
        for (SyntaxNode child : forNode.children()) {
            String kindId = child.kind().id();
            if (!seenHeader && (
                JavaSyntaxKinds.BASIC_FOR_STATEMENT.id().equals(kindId)
                    || JavaSyntaxKinds.ENHANCED_FOR_STATEMENT.id().equals(kindId))) {
                seenHeader = true;
                continue;
            }

            if (seenHeader)
                return child;
        }

        return null;
    }

    public SyntaxNode lambdaBodyOf(SyntaxNode lambda) {
        boolean seenArrow = false;
        for (SyntaxNode child : lambda.children()) {
            if (!seenArrow && child instanceof SyntaxToken token && "->".equals(token.text())) {
                seenArrow = true;
                continue;
            }

            if (seenArrow && !(child instanceof SyntaxToken))
                return child;
        }

        return null;
    }

    public @Nullable SyntaxNode thenBranchOf(SyntaxNode ifNode) {
        List<SyntaxNode> children = ifNode.children();
        boolean seenCondition = false;

        for (SyntaxNode child : children) {
            if (!seenCondition && isExpressionNode(child)) {
                seenCondition = true;
                continue;
            }

            if (seenCondition)
                return child;
        }

        return null;
    }

    public @Nullable SyntaxNode elseBranchOf(SyntaxNode ifNode) {
        boolean sawElse = false;
        for (SyntaxNode child : ifNode.children()) {
            if (!sawElse) {
                if (isElseToken(child))
                    sawElse = true;
                continue;
            }

            if (!(child instanceof SyntaxToken))
                return child;
        }

        return null;
    }

    private boolean isElseToken(SyntaxNode node) {
        return node instanceof SyntaxToken token
            && JavaSyntaxKinds.tokenKind(JavaTokenType.ELSE_KEYWORD).id().equals(token.kind().id());
    }

    /**
     * Returns whether a block node contains only tokens and no nested syntax nodes.
     *
     * @param block block node to inspect
     * @return {@code true} when the block has no nested syntax children
     */
    public boolean isEmptyBlock(SyntaxNode block) {
        if (block == null)
            return true;

        for (SyntaxNode child : block.children()) {
            if (!(child instanceof SyntaxToken))
                return false;
        }

        return true;
    }

    @Override
    public String languageId() {
        return JavaLanguageSupport.LANGUAGE_ID;
    }

    @Override
    public Path filePath() {
        return filePath;
    }

    @Override
    public String documentText() {
        return documentText;
    }

    /**
     * Returns the underlying semantic model.
     *
     * @return semantic model for the current file
     */
    public SemanticModel semanticModel() {
        return semanticModel;
    }

    /**
     * Convenience accessor for {@code semanticModel().syntaxTree()}.
     *
     * @return syntax tree for the current file
     */
    public SyntaxTree syntaxTree() {
        return semanticModel.syntaxTree();
    }

    /**
     * Returns the symbol declared by the supplied node when one exists.
     *
     * @param node declaration-like node
     * @return declared symbol, if present
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Symbol> declaredSymbol(SyntaxNode node) {
        return semanticModel.declaredSymbol(node);
    }

    /**
     * Returns the symbol resolved for the supplied node when one exists.
     *
     * @param node reference-like node
     * @return resolved symbol, if present
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Symbol> resolvedSymbol(SyntaxNode node) {
        return semanticModel.resolvedSymbol(node);
    }

    /**
     * Returns the inferred type for the supplied node when one exists.
     *
     * @param node typed node
     * @return inferred type, if present
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Type> inferredType(SyntaxNode node) {
        return semanticModel.inferredType(node);
    }

    /**
     * Returns known JDK type names as fully qualified names.
     *
     * @return immutable set of known JDK qualified type names
     */
    public Set<String> jdkQualifiedTypeNames() {
        return JavaSemanticAnalyzer.loadJdkQualifiedTypeNames();
    }

    /**
     * Returns parsed JDK class stubs keyed by fully qualified name.
     *
     * @return immutable map of JDK class stubs
     */
    public Map<String, ClassStub> jdkClassStubsByQualifiedName() {
        return JavaSemanticAnalyzer.loadJdkClassStubsByQualifiedName();
    }

    /**
     * Returns the canonical qualified name for a node when one can be derived.
     *
     * @param node node to inspect
     * @return canonical qualified name, or {@code null} when unavailable
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public @Nullable String canonicalQualifiedName(SyntaxNode node) {
        return JavaSemanticAnalyzer.canonicalQualifiedName(node);
    }

    /**
     * Returns canonical type text for a type-like node when one can be derived.
     *
     * @param node node to inspect
     * @return canonical type text, or {@code null} when unavailable
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public @Nullable String canonicalTypeText(SyntaxNode node) {
        return JavaSemanticAnalyzer.canonicalTypeText(node);
    }

    /**
     * Returns the first identifier-like token text within a subtree, or {@code null}.
     *
     * @param node subtree root
     * @return first identifier-like token text, or {@code null}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public @Nullable String firstIdentifierLikeTokenText(SyntaxNode node) {
        return JavaSemanticAnalyzer.firstIdentifierLikeTokenText(node);
    }

    /**
     * Returns the last identifier-like token text within a subtree, or {@code null}.
     *
     * @param node subtree root
     * @return last identifier-like token text, or {@code null}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public @Nullable String lastIdentifierLikeTokenText(SyntaxNode node) {
        return JavaSemanticAnalyzer.lastIdentifierLikeTokenText(node);
    }

    /**
     * Returns the direct child with the supplied parser kind, or {@code null}.
     *
     * @param node   parent node to search
     * @param kindId parser kind id to match
     * @return matching direct child, or {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public @Nullable SyntaxNode directChild(SyntaxNode node, String kindId) {
        return JavaSemanticAnalyzer.directChild(node, kindId);
    }

    /**
     * Returns whether the subtree contains a token of the supplied Java token type.
     *
     * @param node      subtree root
     * @param tokenType token type to search for
     * @return {@code true} if the token type occurs in the subtree
     * @throws NullPointerException if any argument is {@code null}
     */
    public boolean hasTokenKind(SyntaxNode node, JavaTokenType tokenType) {
        return JavaSemanticAnalyzer.hasTokenKind(node, tokenType);
    }

    /**
     * Returns whether the declaration prefix contains the supplied modifier token.
     *
     * @param node      declaration node
     * @param tokenType modifier token to check
     * @return {@code true} if the modifier is present in the declaration prefix
     * @throws NullPointerException if any argument is {@code null}
     */
    public boolean hasDirectModifierToken(SyntaxNode node, JavaTokenType tokenType) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(tokenType, "tokenType");
        return directModifierTokens(node).contains(tokenType);
    }

    /**
     * Returns the set of direct modifier tokens attached to a declaration node.
     *
     * @param node declaration node
     * @return immutable set of direct modifier tokens
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Set<JavaTokenType> directModifierTokens(SyntaxNode node) {
        return directModifierTokenCounts(node).keySet();
    }

    /**
     * Returns direct modifier tokens and their repetition counts on a declaration node.
     *
     * @param node declaration node
     * @return immutable map of direct modifier token counts
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Map<JavaTokenType, Integer> directModifierTokenCounts(SyntaxNode node) {
        Objects.requireNonNull(node, "node");
        Map<JavaTokenType, Integer> modifiers = new LinkedHashMap<>();
        boolean scanningPrefix = true;
        for (SyntaxNode child : node.children()) {
            if (!scanningPrefix)
                break;

            if (child instanceof SyntaxToken token) {
                if (JavaSemanticAnalyzer.isMissingTokenKind(token.kind().id()))
                    continue;

                String kindId = token.kind().id();
                if (kindId.endsWith("_WHITESPACE")
                    || kindId.endsWith("_LINE_COMMENT")
                    || kindId.endsWith("_BLOCK_COMMENT")
                    || kindId.endsWith("_JAVADOC_COMMENT")) {
                    continue;
                }

                JavaTokenType modifier = directModifierTokenType(token.text());
                if (modifier != null) {
                    modifiers.merge(modifier, 1, Integer::sum);
                    continue;
                }

                scanningPrefix = false;
                continue;
            }

            if ("JAVA_ANNOTATION".equals(child.kind().id()))
                continue;

            scanningPrefix = false;
        }
        return Map.copyOf(modifiers);
    }

    /**
     * Returns whether the supplied node is treated as an expression node.
     *
     * @param node node to test
     * @return {@code true} if the node is an expression
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public boolean isExpressionNode(SyntaxNode node) {
        return JavaSemanticAnalyzer.isExpressionNode(node);
    }

    /**
     * Returns the last segment of a qualified name.
     *
     * @param qualifiedName qualified name
     * @return last segment of the name
     * @throws NullPointerException if {@code qualifiedName} is {@code null}
     */
    public String lastSegment(String qualifiedName) {
        return JavaSemanticAnalyzer.lastSegment(qualifiedName);
    }

    /**
     * Returns the simple name portion of a type name.
     *
     * @param typeName type name
     * @return simple type name
     * @throws NullPointerException if {@code typeName} is {@code null}
     */
    public String simpleTypeName(String typeName) {
        return JavaSemanticAnalyzer.simpleTypeName(typeName);
    }

    /**
     * Returns the package prefix of a qualified name.
     *
     * @param qualifiedName qualified name
     * @return package prefix, possibly empty
     * @throws NullPointerException if {@code qualifiedName} is {@code null}
     */
    public String packagePrefix(String qualifiedName) {
        return JavaSemanticAnalyzer.packagePrefix(qualifiedName);
    }

    /**
     * Traverses the full syntax tree in pre-order and calls the supplied visitor for each
     * node.
     *
     * @param visitor callback invoked for each node
     * @throws NullPointerException if {@code visitor} is {@code null}
     */
    public void traverse(Consumer<SyntaxNode> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        traverseNode(syntaxTree().root(), visitor);
    }

    /**
     * Returns all nodes whose {@code kind().id()} matches the supplied parser kind id.
     *
     * @param kindId parser kind id to match
     * @return immutable list of matching nodes
     * @throws NullPointerException if {@code kindId} is {@code null}
     */
    public List<SyntaxNode> nodesOfKind(String kindId) {
        Objects.requireNonNull(kindId, "kindId");
        List<SyntaxNode> nodes = new ArrayList<>();
        traverse(node -> {
            if (kindId.equals(node.kind().id()))
                nodes.add(node);
        });
        return List.copyOf(nodes);
    }

    /**
     * Returns all nodes whose {@code kind().id()} is contained in the supplied set of parser kind ids.
     *
     * @param kindIds set of parser kind ids to match
     * @return immutable list of matching nodes
     * @throws NullPointerException if {@code kindIds} is {@code null}
     */
    public List<SyntaxNode> nodesOfKinds(Set<String> kindIds) {
        Objects.requireNonNull(kindIds, "kindIds");
        List<SyntaxNode> nodes = new ArrayList<>();
        traverse(node -> {
            if (kindIds.contains(node.kind().id()))
                nodes.add(node);
        });
        return List.copyOf(nodes);
    }

    /**
     * Returns all nodes whose {@code kind().id()} is contained in the supplied array of parser kind ids.
     *
     * @param kindIds array of parser kind ids to match
     * @return immutable list of matching nodes
     * @throws NullPointerException if {@code kindIds} is {@code null}
     */
    public List<SyntaxNode> nodesOfKinds(String... kindIds) {
        return nodesOfKinds(Set.of(kindIds));
    }

    public @Nullable SyntaxNode firstDirectExpressionChild(SyntaxNode node) {
        for (SyntaxNode child : node.children()) {
            if (isExpressionNode(child))
                return child;
        }
        return null;
    }

    public List<SyntaxNode> directExpressionChildren(SyntaxNode node) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (isExpressionNode(child))
                children.add(child);
        }
        return List.copyOf(children);
    }

    public boolean isMethodInvocationNamed(SyntaxNode node, String methodName) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(methodName, "methodName");

        if (!JAVA_METHOD_INVOCATION_EXPRESSION.equals(node.kind().id()))
            return false;

        SyntaxNode selectorName = selectorNameNode(node);
        String actualName = selectorName == null ? null : firstIdentifierLikeTokenText(selectorName);
        return methodName.equals(actualName);
    }

    public @Nullable SyntaxNode invocationReceiver(SyntaxNode invocation) {
        Objects.requireNonNull(invocation, "invocation");
        return explicitReceiver(invocation);
    }

    public boolean hasNoArguments(SyntaxNode invocation) {
        Objects.requireNonNull(invocation, "invocation");
        SyntaxNode argumentList = directChild(invocation, JAVA_ARGUMENT_LIST);
        if (argumentList == null)
            return true;

        for (SyntaxNode child : argumentList.children()) {
            if (isExpressionNode(child))
                return false;
        }

        return true;
    }

    public @Nullable String simpleReceiverName(SyntaxNode invocation) {
        Objects.requireNonNull(invocation, "invocation");
        SyntaxNode receiver = unwrapTransparentExpression(invocationReceiver(invocation));
        return simpleExpressionKey(receiver);
    }

    public @Nullable String simpleExpressionKey(@Nullable SyntaxNode expression) {
        SyntaxNode current = unwrapTransparentExpression(expression);
        if (current == null)
            return null;

        if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(current.kind().id()))
            return firstIdentifierLikeTokenText(current);

        return null;
    }

    public @Nullable SyntaxNode conditionOf(SyntaxNode node) {
        Objects.requireNonNull(node, "node");

        return switch (node.kind().id()) {
            case "JAVA_IF_STATEMENT", "JAVA_WHILE_STATEMENT", "JAVA_DO_WHILE_STATEMENT" ->
                firstDirectExpressionChild(node);
            case "JAVA_FOR_STATEMENT" -> basicForConditionOf(node);
            default -> null;
        };
    }

    public boolean hasOperatorToken(SyntaxNode node, JavaTokenType tokenType) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(tokenType, "tokenType");

        String expectedKindId = JavaSyntaxKinds.tokenKind(tokenType).id();
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token && expectedKindId.equals(token.kind().id()))
                return true;
        }

        return false;
    }

    public @Nullable SyntaxNode selectorNameNode(SyntaxNode node) {
        return JavaSemanticAnalyzer.selectorNameNode(node);
    }

    public @Nullable SyntaxNode explicitReceiver(SyntaxNode node) {
        return JavaSemanticAnalyzer.explicitReceiver(node);
    }

    public boolean isSelectorNameExpression(SyntaxNode node) {
        return JavaSemanticAnalyzer.isSelectorNameExpression(node);
    }

    public boolean isTypeSymbol(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
            default -> false;
        };
    }

    public boolean isMethodNameReference(SyntaxNode node) {
        var parent = node.parent();
        if (parent.isEmpty())
            return false;
        if (!JAVA_METHOD_INVOCATION_EXPRESSION.equals(parent.get().kind().id()))
            return false;
        for (SyntaxNode child : parent.get().children()) {
            if (JAVA_ARGUMENT_LIST.equals(child.kind().id()))
                return false;
            if (child == node)
                return true;
        }
        return false;
    }

    public Type declaredTypeOfVariable(SyntaxNode variableDeclarator) {
        var parent = variableDeclarator.parent();
        while (parent.isPresent()) {
            SyntaxNode candidate = parent.get();
            SyntaxNode typeRef = directChild(candidate, JAVA_TYPE_REFERENCE);
            if (typeRef != null)
                return inferredType(typeRef).orElse(new UnknownType("<unknown>"));
            parent = candidate.parent();
        }
        return new UnknownType("<unknown>");
    }

    public boolean isAssignable(Type target, Type source) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");

        if (target.kind() == Kind.UNKNOWN || source.kind() == Kind.UNKNOWN)
            return true;
        if (target.displayName().equals(source.displayName()))
            return true;

        if (isNumericType(target) && isNumericType(source))
            return numericRank(target.displayName()) >= numericRank(source.displayName());

        if (target.kind() == Kind.DECLARED && source.kind() == Kind.DECLARED) {
            String targetName = simpleTypeName(target.displayName());
            String sourceName = simpleTypeName(source.displayName());
            return targetName.equals(sourceName) || "Object".equals(targetName);
        }
        return false;
    }

    public String currentPackageName() {
        String cached = cachedCurrentPackageName;
        if (cached != null)
            return cached;

        String packageName = "";
        for (SyntaxNode child : syntaxTree().root().children()) {
            if (!JAVA_PACKAGE_DECLARATION.equals(child.kind().id()))
                continue;
            SyntaxNode qualifiedName = directChild(child, JAVA_QUALIFIED_NAME);
            String resolved = qualifiedName == null ? null : canonicalQualifiedName(qualifiedName);
            if (resolved != null) {
                packageName = resolved;
                break;
            }
        }

        cachedCurrentPackageName = packageName;
        return packageName;
    }

    public Optional<Symbol> enclosingTypeSymbol(SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            var parent = current.parent();
            if (parent.isEmpty())
                return Optional.empty();

            current = parent.get();
            Symbol declared = semanticModel.declaredSymbol(current).orElse(null);
            if (declared != null && isTypeSymbol(declared.kind()))
                return Optional.of(declared);
        }
    }

    public Optional<Symbol> topLevelEnclosingTypeSymbol(SyntaxNode node) {
        Symbol topLevel = null;
        SyntaxNode current = node;
        while (true) {
            var parent = current.parent();
            if (parent.isEmpty())
                return Optional.ofNullable(topLevel);

            current = parent.get();
            Symbol declared = semanticModel.declaredSymbol(current).orElse(null);
            if (declared != null && isTypeSymbol(declared.kind()))
                topLevel = declared;
        }
    }

    public Optional<String> ownerQualifiedName(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (isTypeSymbol(symbol.kind()))
            return symbol.qualifiedName();

        String qualifiedName = symbol.qualifiedName().orElse(null);
        if (qualifiedName != null) {
            int separator = qualifiedName.indexOf('#');
            if (separator > 0)
                return Optional.of(qualifiedName.substring(0, separator));
        }

        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration == null)
            return Optional.empty();
        return enclosingTypeSymbol(declaration).flatMap(Symbol::qualifiedName);
    }

    public @Nullable String resolveQualifiedTypeName(SyntaxNode typeNode) {
        return resolveQualifiedTypeName(canonicalTypeText(typeNode));
    }

    public @Nullable String resolveQualifiedTypeName(@Nullable String typeText) {
        if (typeText == null || typeText.isBlank())
            return null;

        String text = typeText;
        while (text.endsWith("[]"))
            text = text.substring(0, text.length() - 2);
        if ("void".equals(text) || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
            return text;
        if (localTypeSymbolsByQualifiedName().containsKey(text) || jdkQualifiedTypeNames().contains(text))
            return text;

        String simpleName = simpleTypeName(text);
        for (String localQualifiedTypeName : localTypeSymbolsByQualifiedName().keySet()) {
            if (simpleTypeName(localQualifiedTypeName).equals(simpleName))
                return localQualifiedTypeName;
        }

        for (ImportEntry entry : importEntries()) {
            if (entry.isStatic() || entry.isWildcard())
                continue;
            if (simpleName.equals(entry.importedName()))
                return entry.qualifiedTarget();
        }

        String currentPackageType = currentPackageName().isBlank() ? simpleName : currentPackageName() + "." + simpleName;
        if (localTypeSymbolsByQualifiedName().containsKey(currentPackageType) || jdkQualifiedTypeNames().contains(currentPackageType))
            return currentPackageType;

        String javaLangType = "java.lang." + simpleName;
        if (jdkQualifiedTypeNames().contains(javaLangType))
            return javaLangType;

        for (ImportEntry entry : importEntries()) {
            if (entry.isStatic() || !entry.isWildcard())
                continue;
            String imported = entry.ownerName() + "." + simpleName;
            if (localTypeSymbolsByQualifiedName().containsKey(imported) || jdkQualifiedTypeNames().contains(imported))
                return imported;
        }

        return text;
    }

    public int symbolModifiers(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");

        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration != null)
            return sourceSymbolModifiers(symbol, declaration);

        if (isTypeSymbol(symbol.kind())) {
            ClassStub stub = jdkClassStubsByQualifiedName().get(symbol.qualifiedName().orElse(null));
            return stub == null ? Modifier.PUBLIC : stub.modifiers();
        }

        String ownerQualifiedName = ownerQualifiedName(symbol).orElse(null);
        if (ownerQualifiedName == null)
            return Modifier.PUBLIC;

        ClassStub stub = jdkClassStubsByQualifiedName().get(ownerQualifiedName);
        if (stub == null)
            return symbol.kind() == SymbolKind.CONSTRUCTOR ? typeModifiers(ownerQualifiedName) : Modifier.PUBLIC;

        return switch (symbol.kind()) {
            case FIELD -> stub.fields().stream()
                .filter(field -> field.name().equals(symbol.simpleName()))
                .findFirst()
                .map(FieldStub::modifiers)
                .orElse(Modifier.PUBLIC);
            case METHOD -> stub.methods().stream()
                .filter(method -> method.name().equals(symbol.simpleName()))
                .findFirst()
                .map(MethodStub::modifiers)
                .orElse(Modifier.PUBLIC);
            case CONSTRUCTOR -> stub.constructors().stream()
                .findFirst()
                .map(ConstructorStub::modifiers)
                .orElse(typeModifiers(ownerQualifiedName));
            default -> Modifier.PUBLIC;
        };
    }

    public boolean isSubtype(String candidateQualifiedTypeName, String targetQualifiedTypeName) {
        Objects.requireNonNull(candidateQualifiedTypeName, "candidateQualifiedTypeName");
        Objects.requireNonNull(targetQualifiedTypeName, "targetQualifiedTypeName");
        return isSubtype(candidateQualifiedTypeName, targetQualifiedTypeName, new HashSet<>());
    }

    public boolean isInterfaceType(String qualifiedTypeName) {
        Symbol localType = localTypeSymbol(qualifiedTypeName).orElse(null);
        if (localType != null)
            return localType.kind() == SymbolKind.INTERFACE || localType.kind() == SymbolKind.ANNOTATION;

        ClassStub stub = jdkClassStubsByQualifiedName().get(qualifiedTypeName);
        return stub != null && Modifier.isInterface(stub.modifiers());
    }

    public boolean isFinalType(String qualifiedTypeName) {
        return Modifier.isFinal(typeModifiers(qualifiedTypeName));
    }

    public boolean isAbstractType(String qualifiedTypeName) {
        Symbol localType = localTypeSymbol(qualifiedTypeName).orElse(null);
        if (localType != null && localType.declaration().isPresent()) {
            String kindId = localType.declaration().orElseThrow().kind().id();
            if (JAVA_INTERFACE_DECLARATION.equals(kindId) || JAVA_ANNOTATION_TYPE_DECLARATION.equals(kindId))
                return true;
        }
        return Modifier.isAbstract(typeModifiers(qualifiedTypeName)) || isInterfaceType(qualifiedTypeName);
    }

    public List<String> directSuperTypeNames(String qualifiedTypeName) {
        return directSuperTypeNamesInternal(qualifiedTypeName);
    }

    public List<MethodDescriptor> declaredMethodDescriptors(String ownerQualifiedTypeName) {
        return declaredMethodsByOwner().getOrDefault(ownerQualifiedTypeName, List.of());
    }

    public List<FieldDescriptor> declaredFieldDescriptors(String ownerQualifiedTypeName) {
        return declaredFieldsByOwner().getOrDefault(ownerQualifiedTypeName, List.of());
    }

    public List<FieldDescriptor> inheritedFieldDescriptors(String ownerQualifiedTypeName) {
        List<FieldDescriptor> fields = new ArrayList<>();
        collectInheritedFieldDescriptors(ownerQualifiedTypeName, fields, new HashSet<>());
        return List.copyOf(fields);
    }

    public List<MethodDescriptor> inheritedMethodDescriptors(String ownerQualifiedTypeName) {
        List<MethodDescriptor> methods = new ArrayList<>();
        collectInheritedMethodDescriptors(ownerQualifiedTypeName, methods, new HashSet<>());
        return List.copyOf(methods);
    }

    public boolean isTypeAccessible(String qualifiedTypeName, SyntaxNode usageSite) {
        Objects.requireNonNull(qualifiedTypeName, "qualifiedTypeName");
        Objects.requireNonNull(usageSite, "usageSite");

        if (qualifiedTypeName.isBlank())
            return true;
        if (Set.of("void", "boolean", "byte", "short", "char", "int", "long", "float", "double").contains(qualifiedTypeName))
            return true;

        int modifiers = typeModifiers(qualifiedTypeName);
        if (Modifier.isPublic(modifiers))
            return true;

        String currentPackage = currentPackageName();
        String declaringPackage = packageNameOfType(qualifiedTypeName);
        if (Modifier.isPrivate(modifiers)) {
            String currentTopLevel = topLevelEnclosingTypeSymbol(usageSite)
                .flatMap(Symbol::qualifiedName)
                .orElse(null);
            String targetTopLevel = topLevelTypeQualifiedName(qualifiedTypeName);
            return currentTopLevel != null && currentTopLevel.equals(targetTopLevel);
        }

        if (Modifier.isProtected(modifiers)) {
            if (Objects.equals(currentPackage, declaringPackage))
                return true;
            String currentType = enclosingTypeSymbol(usageSite)
                .flatMap(Symbol::qualifiedName)
                .orElse(null);
            return currentType != null && isSubtype(currentType, qualifiedTypeName);
        }

        return Objects.equals(currentPackage, declaringPackage);
    }

    public boolean isSymbolAccessible(Symbol symbol, SyntaxNode usageSite) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(usageSite, "usageSite");

        if (isTypeSymbol(symbol.kind()))
            return isTypeAccessible(symbol.qualifiedName().orElse(symbol.simpleName()), usageSite);

        String ownerQualifiedName = ownerQualifiedName(symbol).orElse(null);
        if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
            return true;

        int modifiers = symbolModifiers(symbol);
        if (Modifier.isPublic(modifiers))
            return true;

        String currentPackage = currentPackageName();
        String declaringPackage = packageNameOfType(ownerQualifiedName);
        if (Modifier.isPrivate(modifiers)) {
            String currentTopLevel = topLevelEnclosingTypeSymbol(usageSite)
                .flatMap(Symbol::qualifiedName)
                .orElse(null);
            String targetTopLevel = topLevelTypeQualifiedName(ownerQualifiedName);
            return currentTopLevel != null && currentTopLevel.equals(targetTopLevel);
        }

        if (Modifier.isProtected(modifiers)) {
            if (Objects.equals(currentPackage, declaringPackage))
                return true;
            String currentType = enclosingTypeSymbol(usageSite)
                .flatMap(Symbol::qualifiedName)
                .orElse(null);
            return currentType != null && isSubtype(currentType, ownerQualifiedName);
        }

        return Objects.equals(currentPackage, declaringPackage);
    }

    public Set<String> availableTypeNames() {
        Set<String> cached = cachedAvailableTypeNames;
        if (cached != null)
            return cached;

        Set<String> names = new HashSet<>();
        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            if (isTypeSymbol(symbol.kind())) {
                names.add(symbol.simpleName());
                symbol.qualifiedName().ifPresent(names::add);
            }
        }));
        names.add("String");
        names.add("Object");
        Set<String> copy = Set.copyOf(names);
        cachedAvailableTypeNames = copy;
        return copy;
    }

    public ImportIndex importIndex() {
        ImportIndex cached = cachedImportIndex;
        if (cached != null)
            return cached;

        ImportIndex computed = ImportIndex.create(this);
        cachedImportIndex = computed;
        return computed;
    }

    public List<ImportEntry> importEntries() {
        return importIndex().imports();
    }

    public boolean isResolvableType(String qualifiedTypeName) {
        return importIndex().isResolvableType(qualifiedTypeName);
    }

    public boolean isResolvablePackagePrefix(String packagePrefix) {
        return importIndex().isResolvablePackagePrefix(packagePrefix);
    }

    public boolean hasResolvableStaticMember(String ownerQualifiedName, String memberName) {
        return importIndex().hasResolvableStaticMember(ownerQualifiedName, memberName);
    }

    public List<Symbol> resolveStaticImportedFields(String fieldName, SyntaxNode referenceNode) {
        return importIndex().resolveStaticImportedFields(fieldName, referenceNode);
    }

    public List<Symbol> resolveStaticImportedMethods(String methodName, SyntaxNode invocationNode, int argumentCountOrUnknown) {
        return importIndex().resolveStaticImportedMethods(methodName, invocationNode, argumentCountOrUnknown);
    }

    public @Nullable SyntaxNode nearestEnclosingCallableOrLambda(SyntaxNode node) {
        Objects.requireNonNull(node, "node");
        SyntaxNode current = node;
        while (true) {
            var parent = current.parent();
            if (parent.isEmpty())
                return null;

            current = parent.get();
            String kindId = current.kind().id();
            if (JAVA_METHOD_DECLARATION.equals(kindId)
                || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                || JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(kindId)
                || JAVA_LAMBDA_EXPRESSION.equals(kindId)) {
                return current;
            }
        }
    }

    public List<String> declaredThrownTypeNames(SyntaxNode callableDeclaration) {
        Objects.requireNonNull(callableDeclaration, "callableDeclaration");
        SyntaxNode throwsClause = directChild(callableDeclaration, JAVA_THROWS_CLAUSE);
        if (throwsClause == null)
            return List.of();
        return topLevelReferencedTypeNames(throwsClause);
    }

    public List<String> catchParameterTypeNames(SyntaxNode catchClause) {
        Objects.requireNonNull(catchClause, "catchClause");
        if (!JAVA_CATCH_CLAUSE.equals(catchClause.kind().id()))
            return List.of();

        SyntaxNode parameter = directChild(catchClause, JAVA_PARAMETER);
        if (parameter == null)
            return List.of();

        for (SyntaxNode child : parameter.children()) {
            String kindId = child.kind().id();
            if (JAVA_TYPE_REFERENCE.equals(kindId) || JAVA_UNION_TYPE_REFERENCE.equals(kindId))
                return topLevelReferencedTypeNames(child);
        }
        return List.of();
    }

    public List<String> thrownTypeNames(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");

        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration != null) {
            String kindId = declaration.kind().id();
            if (JAVA_METHOD_DECLARATION.equals(kindId)
                || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                || JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(kindId)) {
                return declaredThrownTypeNames(declaration);
            }
            return List.of();
        }

        String ownerQualifiedName = ownerQualifiedName(symbol).orElse(null);
        String qualifiedName = symbol.qualifiedName().orElse(null);
        if (ownerQualifiedName == null || qualifiedName == null || qualifiedName.isBlank())
            return List.of();

        int separator = qualifiedName.indexOf('#');
        int signatureStart = qualifiedName.indexOf('(', separator + 1);
        if (separator < 0 || signatureStart < 0)
            return List.of();

        String callableName = qualifiedName.substring(separator + 1, signatureStart);
        String signature = qualifiedName.substring(signatureStart);
        ClassStub stub = jdkClassStubsByQualifiedName().get(ownerQualifiedName);
        if (stub == null)
            return List.of();

        if (symbol.kind() == SymbolKind.CONSTRUCTOR) {
            for (var constructor : stub.constructors()) {
                if (signature.equals(signatureSuffix(constructor.parameters().stream().map(parameter -> toSemanticType(parameter.type())).toList())))
                    return thrownTypeNames(constructor.thrownTypes());
            }
            return List.of();
        }

        if (symbol.kind() != SymbolKind.METHOD)
            return List.of();

        for (var method : stub.methods()) {
            if (!method.name().equals(callableName))
                continue;
            if (signature.equals(signatureSuffix(method.parameters().stream().map(parameter -> toSemanticType(parameter.type())).toList())))
                return thrownTypeNames(method.thrownTypes());
        }
        return List.of();
    }

    /**
     * Returns parameter types for a callable symbol.
     * <p>
     * For source declarations, this reads the declaration tree. For synthetic or external
     * members, this falls back to parsing the callable signature from the qualified name
     * when available.
     *
     * @param symbol callable symbol
     * @return parameter types, or an empty list when unavailable
     */
    public List<Type> callableParameterTypes(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");

        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration != null) {
            String kindId = declaration.kind().id();
            if (JAVA_METHOD_DECLARATION.equals(kindId) || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)) {
                SyntaxNode parameterList = directChild(declaration, JAVA_PARAMETER_LIST);
                if (parameterList == null)
                    return List.of();

                List<Type> parameterTypes = new ArrayList<>();
                for (SyntaxNode child : parameterList.children()) {
                    if (!JAVA_PARAMETER.equals(child.kind().id()))
                        continue;

                    SyntaxNode typeRef = directChild(child, JAVA_TYPE_REFERENCE);
                    if (typeRef == null) {
                        parameterTypes.add(new UnknownType("<unknown>"));
                        continue;
                    }

                    parameterTypes.add(inferredType(typeRef).orElse(new UnknownType("<unknown>")));
                }

                return List.copyOf(parameterTypes);
            }
        }

        String qualifiedName = symbol.qualifiedName().orElse(null);
        if (qualifiedName == null || qualifiedName.isBlank())
            return List.of();

        int open = qualifiedName.indexOf('(');
        int close = qualifiedName.lastIndexOf(')');
        if (open < 0 || close < open)
            return List.of();

        String params = qualifiedName.substring(open + 1, close).trim();
        if (params.isEmpty())
            return List.of();

        List<Type> parameterTypes = new ArrayList<>();
        for (String token : params.split(",")) {
            String typeName = token.trim();
            if (typeName.isEmpty()) {
                parameterTypes.add(new UnknownType("<unknown>"));
                continue;
            }

            parameterTypes.add(parseCallableParameterType(typeName));
        }

        return List.copyOf(parameterTypes);
    }

    private static Type parseCallableParameterType(String typeName) {
        String text = typeName.trim();
        if (text.isEmpty())
            return new UnknownType("<unknown>");

        int arrayDimensions = 0;
        while (text.endsWith("[]")) {
            arrayDimensions++;
            text = text.substring(0, text.length() - 2).trim();
        }

        Type result = switch (text) {
            case "boolean", "byte", "short", "char", "int", "long", "float", "double", "void" ->
                new Type.PrimitiveType(text);
            default -> new DeclaredType(text, List.of());
        };
        for (int index = 0; index < arrayDimensions; index++) {
            result = new Type.ArrayType(result);
        }

        return result;
    }

    public boolean isThrowableType(String qualifiedTypeName) {
        Objects.requireNonNull(qualifiedTypeName, "qualifiedTypeName");
        return "java.lang.Throwable".equals(qualifiedTypeName) || isSubtype(qualifiedTypeName, "java.lang.Throwable");
    }

    public boolean isUncheckedExceptionType(String qualifiedTypeName) {
        Objects.requireNonNull(qualifiedTypeName, "qualifiedTypeName");
        return "java.lang.RuntimeException".equals(qualifiedTypeName)
            || "java.lang.Error".equals(qualifiedTypeName)
            || isSubtype(qualifiedTypeName, "java.lang.RuntimeException")
            || isSubtype(qualifiedTypeName, "java.lang.Error");
    }

    public boolean isCheckedExceptionType(String qualifiedTypeName) {
        Objects.requireNonNull(qualifiedTypeName, "qualifiedTypeName");
        return isThrowableType(qualifiedTypeName) && !isUncheckedExceptionType(qualifiedTypeName);
    }

    public boolean isAutoCloseableType(String qualifiedTypeName) {
        Objects.requireNonNull(qualifiedTypeName, "qualifiedTypeName");
        return "java.lang.AutoCloseable".equals(qualifiedTypeName) || isSubtype(qualifiedTypeName, "java.lang.AutoCloseable");
    }

    public @Nullable String tryResourceTypeName(SyntaxNode tryResource) {
        Objects.requireNonNull(tryResource, "tryResource");
        if (!JAVA_TRY_RESOURCE.equals(tryResource.kind().id()))
            return null;

        SyntaxNode localVariableDeclaration = directChild(tryResource, JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT);
        if (localVariableDeclaration != null) {
            SyntaxNode typeReference = directChild(localVariableDeclaration, JAVA_TYPE_REFERENCE);
            if (typeReference != null)
                return resolveQualifiedTypeName(typeReference);
        }

        SyntaxNode expression = firstDirectExpressionChild(tryResource);
        if (expression == null)
            return null;

        Type inferred = inferredType(expression).orElse(new UnknownType("<unknown>"));
        return inferred.kind() == Kind.UNKNOWN ? null : resolveQualifiedTypeName(inferred.displayName());
    }

    public List<String> closeThrownTypeNames(String resourceQualifiedTypeName) {
        Objects.requireNonNull(resourceQualifiedTypeName, "resourceQualifiedTypeName");

        List<String> thrown = new ArrayList<>();
        for (MethodDescriptor descriptor : declaredMethodDescriptors(resourceQualifiedTypeName)) {
            if ("close".equals(descriptor.name()) && descriptor.parameterTypes().isEmpty())
                thrown.addAll(descriptor.thrownTypes());
        }
        for (MethodDescriptor descriptor : inheritedMethodDescriptors(resourceQualifiedTypeName)) {
            if ("close".equals(descriptor.name()) && descriptor.parameterTypes().isEmpty())
                thrown.addAll(descriptor.thrownTypes());
        }
        return List.copyOf(new LinkedHashSet<>(thrown));
    }

    public List<String> topLevelReferencedTypeNames(SyntaxNode node) {
        Objects.requireNonNull(node, "node");
        List<String> typeNames = new ArrayList<>();
        collectTopLevelReferencedTypeNames(node, typeNames);
        return List.copyOf(typeNames);
    }

    public @Nullable SyntaxNode unwrapTransparentExpression(@Nullable SyntaxNode node) {
        SyntaxNode current = node;
        while (current != null) {
            String kindId = current.kind().id();
            if (JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(kindId)
                || JavaSyntaxKinds.PRIMARY_EXPRESSION.id().equals(kindId)) {
                current = firstExpressionChild(current);
                continue;
            }
            return current;
        }

        return null;
    }

    public @Nullable SyntaxNode firstExpressionChild(SyntaxNode node) {
        for (SyntaxNode child : node.children()) {
            if (JavaSemanticAnalyzer.isExpressionNode(child))
                return child;
        }

        return null;
    }

    private @Nullable SyntaxNode basicForConditionOf(SyntaxNode forNode) {
        SyntaxNode basicFor = directChild(forNode, JavaSyntaxKinds.BASIC_FOR_STATEMENT.id());
        if (basicFor == null)
            return null;

        int semicolonCount = 0;
        for (SyntaxNode child : basicFor.children()) {
            if (child instanceof SyntaxToken token && ";".equals(token.text())) {
                semicolonCount++;
                continue;
            }

            if (semicolonCount == 1 && isExpressionNode(child))
                return child;
        }

        return null;
    }

    private Map<String, Symbol> localTypeSymbolsByQualifiedName() {
        Map<String, Symbol> cached = cachedLocalTypeSymbolsByQualifiedName;
        if (cached != null)
            return cached;

        Map<String, Symbol> collected = new LinkedHashMap<>();
        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            if (!isTypeSymbol(symbol.kind()))
                return;

            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName != null && !qualifiedName.isBlank()) {
                collected.putIfAbsent(qualifiedName, symbol);
            }
        }));

        Map<String, Symbol> copy = Map.copyOf(collected);
        cachedLocalTypeSymbolsByQualifiedName = copy;
        return copy;
    }

    private Map<String, List<String>> directSuperTypesByQualifiedName() {
        Map<String, List<String>> cached = cachedDirectSuperTypesByQualifiedName;
        if (cached != null)
            return cached;

        Map<String, List<String>> collected = new LinkedHashMap<>();
        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            if (!isTypeSymbol(symbol.kind()))
                return;

            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName == null || qualifiedName.isBlank())
                return;

            List<String> directSupers = new ArrayList<>();
            collectDirectSuperTypes(node, JAVA_EXTENDS_CLAUSE, directSupers);
            collectDirectSuperTypes(node, JAVA_IMPLEMENTS_CLAUSE, directSupers);
            collected.put(qualifiedName, List.copyOf(directSupers));
        }));
        Map<String, List<String>> copy = Map.copyOf(collected);
        cachedDirectSuperTypesByQualifiedName = copy;
        return copy;
    }

    private Map<String, List<MethodDescriptor>> declaredMethodsByOwner() {
        Map<String, List<MethodDescriptor>> cached = cachedDeclaredMethodsByOwner;
        if (cached != null)
            return cached;

        Map<String, List<MethodDescriptor>> collected = new LinkedHashMap<>();

        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            if (symbol.kind() != SymbolKind.METHOD)
                return;
            String ownerQualifiedName = ownerQualifiedName(symbol).orElse(null);
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (ownerQualifiedName == null || declaration == null || !JAVA_METHOD_DECLARATION.equals(declaration.kind().id()))
                return;

            collected.computeIfAbsent(ownerQualifiedName, ignored -> new ArrayList<>())
                .add(sourceMethodDescriptor(ownerQualifiedName, symbol, declaration));
        }));

        Map<String, List<MethodDescriptor>> withJdk = new LinkedHashMap<>();
        collected.forEach((owner, methods) -> withJdk.put(owner, List.copyOf(methods)));
        Map<String, List<MethodDescriptor>> copy = Map.copyOf(withJdk);
        cachedDeclaredMethodsByOwner = copy;
        return copy;
    }

    private Map<String, List<FieldDescriptor>> declaredFieldsByOwner() {
        Map<String, List<FieldDescriptor>> cached = cachedDeclaredFieldsByOwner;
        if (cached != null)
            return cached;

        Map<String, List<FieldDescriptor>> collected = new LinkedHashMap<>();

        traverse(node -> declaredSymbol(node).ifPresent(symbol -> {
            if (symbol.kind() != SymbolKind.FIELD)
                return;
            String ownerQualifiedName = ownerQualifiedName(symbol).orElse(null);
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (ownerQualifiedName == null || declaration == null)
                return;

            collected.computeIfAbsent(ownerQualifiedName, ignored -> new ArrayList<>())
                .add(sourceFieldDescriptor(ownerQualifiedName, symbol, declaration));
        }));

        Map<String, List<FieldDescriptor>> withJdk = new LinkedHashMap<>();
        collected.forEach((owner, fields) -> withJdk.put(owner, List.copyOf(fields)));
        Map<String, List<FieldDescriptor>> copy = Map.copyOf(withJdk);
        cachedDeclaredFieldsByOwner = copy;
        return copy;
    }

    private void collectDirectSuperTypes(SyntaxNode declarationNode, String clauseKindId, List<String> out) {
        SyntaxNode clause = directChild(declarationNode, clauseKindId);
        if (clause == null)
            return;

        traverseNode(clause, node -> {
            if (!JAVA_TYPE_REFERENCE.equals(node.kind().id()))
                return;
            String qualified = resolveQualifiedTypeName(node);
            if (qualified != null && !qualified.isBlank())
                out.add(qualified);
        });
    }

    private int sourceSymbolModifiers(Symbol symbol, SyntaxNode declaration) {
        int modifiers = 0;
        SyntaxNode modifierSource = switch (symbol.kind()) {
            case FIELD, LOCAL_VARIABLE -> declaration.parent().orElse(declaration);
            default -> declaration;
        };

        if (hasDirectModifierToken(modifierSource, JavaTokenType.PUBLIC_KEYWORD))
            modifiers |= Modifier.PUBLIC;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.PROTECTED_KEYWORD))
            modifiers |= Modifier.PROTECTED;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.PRIVATE_KEYWORD))
            modifiers |= Modifier.PRIVATE;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.STATIC_KEYWORD))
            modifiers |= Modifier.STATIC;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.FINAL_KEYWORD))
            modifiers |= Modifier.FINAL;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.ABSTRACT_KEYWORD))
            modifiers |= Modifier.ABSTRACT;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.NATIVE_KEYWORD))
            modifiers |= Modifier.NATIVE;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.SYNCHRONIZED_KEYWORD))
            modifiers |= Modifier.SYNCHRONIZED;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.TRANSIENT_KEYWORD))
            modifiers |= Modifier.TRANSIENT;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.VOLATILE_KEYWORD))
            modifiers |= Modifier.VOLATILE;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.STRICTFP_KEYWORD))
            modifiers |= Modifier.STRICT;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.DEFAULT_KEYWORD))
            modifiers |= DEFAULT_MODIFIER;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.SEALED_KEYWORD))
            modifiers |= SEALED_MODIFIER;
        if (hasDirectModifierToken(modifierSource, JavaTokenType.NON_SEALED_KEYWORD))
            modifiers |= NON_SEALED_MODIFIER;

        Symbol ownerSymbol = ownerQualifiedName(symbol).flatMap(this::localTypeSymbol).orElse(null);
        if ((symbol.kind() == SymbolKind.FIELD || symbol.kind() == SymbolKind.METHOD)
            && ownerSymbol != null
            && ownerSymbol.declaration().isPresent()) {
            String ownerKindId = ownerSymbol.declaration().orElseThrow().kind().id();
            if ("JAVA_INTERFACE_DECLARATION".equals(ownerKindId) || "JAVA_ANNOTATION_TYPE_DECLARATION".equals(ownerKindId))
                modifiers |= Modifier.PUBLIC;
        }

        return modifiers;
    }

    private @Nullable JavaTokenType directModifierTokenType(String tokenText) {
        return switch (tokenText) {
            case "public" -> JavaTokenType.PUBLIC_KEYWORD;
            case "protected" -> JavaTokenType.PROTECTED_KEYWORD;
            case "private" -> JavaTokenType.PRIVATE_KEYWORD;
            case "static" -> JavaTokenType.STATIC_KEYWORD;
            case "final" -> JavaTokenType.FINAL_KEYWORD;
            case "abstract" -> JavaTokenType.ABSTRACT_KEYWORD;
            case "default" -> JavaTokenType.DEFAULT_KEYWORD;
            case "native" -> JavaTokenType.NATIVE_KEYWORD;
            case "synchronized" -> JavaTokenType.SYNCHRONIZED_KEYWORD;
            case "transient" -> JavaTokenType.TRANSIENT_KEYWORD;
            case "volatile" -> JavaTokenType.VOLATILE_KEYWORD;
            case "strictfp" -> JavaTokenType.STRICTFP_KEYWORD;
            case "sealed" -> JavaTokenType.SEALED_KEYWORD;
            case "non-sealed" -> JavaTokenType.NON_SEALED_KEYWORD;
            default -> null;
        };
    }

    private int typeModifiers(String qualifiedTypeName) {
        Symbol localType = localTypeSymbol(qualifiedTypeName).orElse(null);
        if (localType != null && localType.declaration().isPresent())
            return sourceSymbolModifiers(localType, localType.declaration().orElseThrow());

        ClassStub stub = jdkClassStubsByQualifiedName().get(qualifiedTypeName);
        if (stub != null)
            return stub.modifiers();

        return Modifier.PUBLIC;
    }

    private Optional<Symbol> localTypeSymbol(String qualifiedTypeName) {
        return Optional.ofNullable(localTypeSymbolsByQualifiedName().get(qualifiedTypeName));
    }

    private String packageNameOfType(String qualifiedTypeName) {
        Symbol localType = localTypeSymbol(qualifiedTypeName).orElse(null);
        if (localType != null)
            return currentPackageName();

        ClassStub stub = jdkClassStubsByQualifiedName().get(qualifiedTypeName);
        if (stub != null)
            return stub.packageName();

        int lastDot = qualifiedTypeName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedTypeName.substring(0, lastDot);
    }

    private String topLevelTypeQualifiedName(String qualifiedTypeName) {
        Symbol localType = localTypeSymbol(qualifiedTypeName).orElse(null);
        if (localType != null && localType.declaration().isPresent()) {
            return topLevelEnclosingTypeSymbol(localType.declaration().orElseThrow())
                .flatMap(Symbol::qualifiedName)
                .orElse(qualifiedTypeName);
        }
        return qualifiedTypeName;
    }

    private boolean isSubtype(String candidateQualifiedTypeName, String targetQualifiedTypeName, Set<String> visited) {
        if (candidateQualifiedTypeName.equals(targetQualifiedTypeName))
            return true;
        if (!visited.add(candidateQualifiedTypeName))
            return false;

        for (String directSuper : directSuperTypeNamesInternal(candidateQualifiedTypeName)) {
            if (directSuper.equals(targetQualifiedTypeName) || isSubtype(directSuper, targetQualifiedTypeName, visited))
                return true;
        }
        return false;
    }

    private List<String> directSuperTypeNamesInternal(String qualifiedTypeName) {
        List<String> sourceSupers = directSuperTypesByQualifiedName().get(qualifiedTypeName);
        if (sourceSupers != null)
            return sourceSupers;

        ClassStub stub = jdkClassStubsByQualifiedName().get(qualifiedTypeName);
        if (stub == null)
            return List.of();

        List<String> supers = new ArrayList<>();
        if (stub.superClass() != null) {
            String superName = resolveQualifiedClassParserTypeName(stub.superClass());
            if (superName != null)
                supers.add(superName);
        }
        stub.interfaces().stream()
            .map(this::resolveQualifiedClassParserTypeName)
            .filter(Objects::nonNull)
            .forEach(supers::add);
        return List.copyOf(supers);
    }

    private void collectInheritedMethodDescriptors(String ownerQualifiedTypeName, List<MethodDescriptor> out, Set<String> visited) {
        if (!visited.add(ownerQualifiedTypeName))
            return;

        for (String directSuper : directSuperTypeNamesInternal(ownerQualifiedTypeName)) {
            out.addAll(methodDescriptorsForType(directSuper));
            collectInheritedMethodDescriptors(directSuper, out, visited);
        }
    }

    private void collectInheritedFieldDescriptors(String ownerQualifiedTypeName, List<FieldDescriptor> out, Set<String> visited) {
        if (!visited.add(ownerQualifiedTypeName))
            return;

        for (String directSuper : directSuperTypeNamesInternal(ownerQualifiedTypeName)) {
            out.addAll(fieldDescriptorsForType(directSuper));
            collectInheritedFieldDescriptors(directSuper, out, visited);
        }
    }

    private List<MethodDescriptor> methodDescriptorsForType(String ownerQualifiedTypeName) {
        List<MethodDescriptor> source = declaredMethodsByOwner().get(ownerQualifiedTypeName);
        if (source != null)
            return source;

        ClassStub stub = jdkClassStubsByQualifiedName().get(ownerQualifiedTypeName);
        if (stub == null)
            return List.of();

        List<MethodDescriptor> methods = new ArrayList<>();
        for (var method : stub.methods()) {
            List<Type> parameterTypes = method.parameters().stream()
                .map(parameter -> toSemanticType(parameter.type()))
                .toList();
            methods.add(new MethodDescriptor(
                ownerQualifiedTypeName,
                method.name(),
                parameterTypes,
                toSemanticType(method.returnType()),
                thrownTypeNames(method.thrownTypes()),
                method.modifiers(),
                null,
                null
            ));
        }
        return List.copyOf(methods);
    }

    private List<FieldDescriptor> fieldDescriptorsForType(String ownerQualifiedTypeName) {
        List<FieldDescriptor> source = declaredFieldsByOwner().get(ownerQualifiedTypeName);
        if (source != null)
            return source;

        ClassStub stub = jdkClassStubsByQualifiedName().get(ownerQualifiedTypeName);
        if (stub == null)
            return List.of();

        List<FieldDescriptor> fields = new ArrayList<>();
        for (var field : stub.fields()) {
            fields.add(new FieldDescriptor(
                ownerQualifiedTypeName,
                field.name(),
                toSemanticType(field.type()),
                field.modifiers(),
                null,
                null
            ));
        }
        return List.copyOf(fields);
    }

    private MethodDescriptor sourceMethodDescriptor(String ownerQualifiedName, Symbol symbol, SyntaxNode declaration) {
        List<Type> parameterTypes = new ArrayList<>();
        SyntaxNode parameterList = directChild(declaration, JAVA_PARAMETER_LIST);
        if (parameterList != null) {
            for (SyntaxNode child : parameterList.children()) {
                if (!JAVA_PARAMETER.equals(child.kind().id()))
                    continue;
                SyntaxNode typeRef = directChild(child, JAVA_TYPE_REFERENCE);
                String qualifiedTypeName = typeRef == null ? null : resolveQualifiedTypeName(typeRef);
                parameterTypes.add(typeRef == null || qualifiedTypeName == null
                    ? new UnknownType("<unknown>")
                    : new DeclaredType(qualifiedTypeName, List.of()));
            }
        }

        SyntaxNode returnTypeRef = directChild(declaration, JAVA_TYPE_REFERENCE);
        String qualifiedReturnType = returnTypeRef == null ? null : resolveQualifiedTypeName(returnTypeRef);
        Type returnType;
        if (returnTypeRef == null) {
            returnType = new VoidType();
        } else if (qualifiedReturnType == null) {
            returnType = new UnknownType("<unknown>");
        } else if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(qualifiedReturnType)) {
            returnType = new Type.PrimitiveType(qualifiedReturnType);
        } else if ("void".equals(qualifiedReturnType)) {
            returnType = new VoidType();
        } else {
            returnType = new DeclaredType(qualifiedReturnType, List.of());
        }

        int modifiers = sourceSymbolModifiers(symbol, declaration);
        if (declaration.children().stream().noneMatch(child -> JAVA_BLOCK.equals(child.kind().id()))
            && !Modifier.isStatic(modifiers)
            && !isPrivateModifier(modifiers)) {
            SyntaxNode ownerDeclaration = localTypeSymbol(ownerQualifiedName).flatMap(Symbol::declaration).orElse(null);
            if (ownerDeclaration != null
                && (JAVA_INTERFACE_DECLARATION.equals(ownerDeclaration.kind().id())
                || JAVA_ANNOTATION_TYPE_DECLARATION.equals(ownerDeclaration.kind().id()))) {
                modifiers |= Modifier.ABSTRACT;
            }
        }

        return new MethodDescriptor(
            ownerQualifiedName,
            symbol.simpleName(),
            List.copyOf(parameterTypes),
            returnType,
            declaredThrownTypeNames(declaration),
            modifiers,
            declaration,
            symbol
        );
    }

    private FieldDescriptor sourceFieldDescriptor(String ownerQualifiedName, Symbol symbol, SyntaxNode declaration) {
        Type type = declaredTypeOfVariable(declaration);
        return new FieldDescriptor(
            ownerQualifiedName,
            symbol.simpleName(),
            type,
            sourceSymbolModifiers(symbol, declaration),
            declaration,
            symbol
        );
    }

    private static boolean isPrivateModifier(int modifiers) {
        return Modifier.isPrivate(modifiers);
    }

    private void collectTopLevelReferencedTypeNames(SyntaxNode node, List<String> out) {
        String kindId = node.kind().id();
        if (JAVA_TYPE_REFERENCE.equals(kindId)) {
            String qualifiedTypeName = resolveQualifiedTypeName(node);
            if (qualifiedTypeName != null && !qualifiedTypeName.isBlank())
                out.add(qualifiedTypeName);
            return;
        }

        if (JAVA_UNION_TYPE_REFERENCE.equals(kindId)) {
            for (SyntaxNode child : node.children()) {
                if (JAVA_TYPE_REFERENCE.equals(child.kind().id()))
                    collectTopLevelReferencedTypeNames(child, out);
            }
            return;
        }

        for (SyntaxNode child : node.children()) {
            String childKindId = child.kind().id();
            if (JAVA_TYPE_REFERENCE.equals(childKindId) || JAVA_UNION_TYPE_REFERENCE.equals(childKindId))
                collectTopLevelReferencedTypeNames(child, out);
        }
    }

    private static List<String> thrownTypeNames(List<dev.railroadide.railroad.ide.classparser.Type> types) {
        List<String> names = new ArrayList<>();
        for (dev.railroadide.railroad.ide.classparser.Type type : types) {
            Type semanticType = toSemanticType(type);
            if (semanticType.kind() == Kind.DECLARED || semanticType.kind() == Kind.PRIMITIVE)
                names.add(semanticType.displayName());
        }
        return List.copyOf(names);
    }

    private static String signatureSuffix(List<Type> parameterTypes) {
        if (parameterTypes.isEmpty())
            return "()";
        StringBuilder builder = new StringBuilder("(");
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0)
                builder.append(',');
            builder.append(parameterTypes.get(index).displayName());
        }
        builder.append(')');
        return builder.toString();
    }

    private static Type toSemanticType(dev.railroadide.railroad.ide.classparser.Type type) {
        if (type == null)
            return new UnknownType("<unknown>");

        return switch (type) {
            case PrimitiveType primitive -> "void".equals(primitive.name())
                ? new VoidType()
                : new Type.PrimitiveType(primitive.name());
            case ArrayType array -> new Type.ArrayType(toSemanticType(array.componentType()));
            case ClassType clazz -> new DeclaredType(clazz.name(), List.of());
            case TypeVariable variable -> new TypeVariableType(variable.name());
            case WildcardType wildcard -> {
                Type bound = wildcard.bound() == null ? new UnknownType("<unknown>") : toSemanticType(wildcard.bound());
                yield wildcard.isUpperBound()
                    ? new Type.WildcardType(bound, null)
                    : new Type.WildcardType(null, bound);
            }
        };
    }

    private @Nullable String resolveQualifiedClassParserTypeName(dev.railroadide.railroad.ide.classparser.Type type) {
        return switch (type) {
            case null -> null;
            case ClassType clazz -> clazz.name();
            case ArrayType array -> resolveQualifiedClassParserTypeName(array.componentType());
            case TypeVariable ignored -> null;
            case WildcardType wildcard ->
                wildcard.bound() == null ? null : resolveQualifiedClassParserTypeName(wildcard.bound());
            case PrimitiveType primitive -> primitive.name();
        };
    }

    private static void traverseNode(SyntaxNode node, Consumer<SyntaxNode> visitor) {
        visitor.accept(node);
        for (SyntaxNode child : node.children())
            traverseNode(child, visitor);
    }

    private static boolean isNumericType(Type type) {
        return type.kind() == Kind.PRIMITIVE && NUMERIC_PRIMITIVES.contains(type.displayName());
    }

    private static int numericRank(String primitive) {
        return switch (primitive) {
            case "byte" -> 0;
            case "short", "char" -> 1;
            case "int" -> 2;
            case "long" -> 3;
            case "float" -> 4;
            case "double" -> 5;
            default -> -1;
        };
    }

    public void traverseDescendants(SyntaxNode root, Consumer<SyntaxNode> visitor) {
        visitor.accept(root);
        for (SyntaxNode child : root.children()) {
            traverseDescendants(child, visitor);
        }
    }

    public @Nullable SyntaxNode guardedBodyOf(SyntaxNode loopNode) {
        SyntaxNode condition = conditionOf(loopNode);
        boolean seenCondition = false;
        for (SyntaxNode child : loopNode.children()) {
            if (!seenCondition && Objects.equals(child, condition)) {
                seenCondition = true;
                continue;
            }

            if (seenCondition && !(child instanceof SyntaxToken))
                return child;
        }

        return null;
    }

    public NegationUnwrapResult unwrapLeadingNegations(SyntaxNode expression) {
        SyntaxNode current = unwrapTransparentExpression(expression);
        if (current == null)
            return null;

        int negationCount = 0;
        while (current != null && Objects.equals(JavaSyntaxKinds.UNARY_EXPRESSION.id(), current.kind().id())) {
            if (!hasOperatorToken(current, JavaTokenType.EXCLAMATION_MARK))
                break;

            negationCount++;
            current = unwrapTransparentExpression(firstExpressionChild(current));
        }

        return new NegationUnwrapResult(current, negationCount);
    }

    public record NegationUnwrapResult(
        SyntaxNode expression,
        int negationCount
    ) {
        public boolean isNegated() {
            return negationCount % 2 != 0;
        }
    }

    public record ImportEntry(
        SyntaxNode declarationNode,
        SyntaxNode targetNode,
        String qualifiedTarget,
        String ownerName,
        String importedName,
        boolean isStatic,
        boolean isWildcard
    ) {
    }

    public record MethodDescriptor(
        String ownerQualifiedName,
        String name,
        List<Type> parameterTypes,
        Type returnType,
        List<String> thrownTypes,
        int modifiers,
        @Nullable SyntaxNode declaration,
        @Nullable Symbol symbol
    ) {
        public String signatureKey() {
            StringBuilder builder = new StringBuilder(name).append('(');
            for (int index = 0; index < parameterTypes.size(); index++) {
                if (index > 0)
                    builder.append(',');
                builder.append(parameterTypes.get(index).displayName());
            }
            builder.append(')');
            return builder.toString();
        }

        public boolean isAbstract() {
            return Modifier.isAbstract(modifiers) && !isDefault();
        }

        public boolean isDefault() {
            return (modifiers & DEFAULT_MODIFIER) != 0;
        }
    }

    public record FieldDescriptor(
        String ownerQualifiedName,
        String name,
        Type type,
        int modifiers,
        @Nullable SyntaxNode declaration,
        @Nullable Symbol symbol
    ) {
    }

    public static final class ImportIndex {
        private final List<ImportEntry> imports;
        private final Map<String, List<ImportEntry>> staticSingleImportsByMemberName;
        private final List<ImportEntry> onDemandStaticImports;
        private final Set<String> localQualifiedTypeNames;
        private final Set<String> availableQualifiedTypeNames;
        private final Map<String, ClassStub> jdkClassStubsByQualifiedName;
        private final Map<String, Set<String>> localStaticFieldsByOwner;
        private final Map<String, Map<String, Set<Integer>>> localStaticMethodAritiesByOwner;

        private ImportIndex(
            List<ImportEntry> imports,
            Map<String, List<ImportEntry>> staticSingleImportsByMemberName,
            List<ImportEntry> onDemandStaticImports,
            Set<String> localQualifiedTypeNames,
            Set<String> availableQualifiedTypeNames,
            Map<String, ClassStub> jdkClassStubsByQualifiedName,
            Map<String, Set<String>> localStaticFieldsByOwner,
            Map<String, Map<String, Set<Integer>>> localStaticMethodAritiesByOwner
        ) {
            this.imports = imports;
            this.staticSingleImportsByMemberName = staticSingleImportsByMemberName;
            this.onDemandStaticImports = onDemandStaticImports;
            this.localQualifiedTypeNames = localQualifiedTypeNames;
            this.availableQualifiedTypeNames = availableQualifiedTypeNames;
            this.jdkClassStubsByQualifiedName = jdkClassStubsByQualifiedName;
            this.localStaticFieldsByOwner = localStaticFieldsByOwner;
            this.localStaticMethodAritiesByOwner = localStaticMethodAritiesByOwner;
        }

        public List<ImportEntry> imports() {
            return imports;
        }

        public boolean isResolvableType(String qualifiedTypeName) {
            if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                return false;
            if (localQualifiedTypeNames.contains(qualifiedTypeName))
                return true;
            return availableQualifiedTypeNames.contains(qualifiedTypeName);
        }

        public boolean isResolvablePackagePrefix(String packagePrefix) {
            if (packagePrefix == null || packagePrefix.isBlank())
                return false;
            for (String qualifiedType : availableQualifiedTypeNames) {
                if (qualifiedType.startsWith(packagePrefix + "."))
                    return true;
            }
            return false;
        }

        public boolean hasResolvableStaticMember(String ownerQualifiedName, String memberName) {
            return hasResolvableStaticField(ownerQualifiedName, memberName)
                || hasResolvableStaticMethod(ownerQualifiedName, memberName, -1);
        }

        public List<Symbol> resolveStaticImportedFields(String fieldName, SyntaxNode referenceNode) {
            List<Symbol> resolved = new ArrayList<>();
            List<ImportEntry> singleStaticImports = staticSingleImportsByMemberName.get(fieldName);
            if (singleStaticImports != null) {
                for (ImportEntry importEntry : singleStaticImports) {
                    if (hasResolvableStaticField(importEntry.ownerName(), fieldName)) {
                        resolved.add(new SimpleSymbol(
                            SymbolKind.FIELD,
                            fieldName,
                            importEntry.ownerName() + "#" + fieldName,
                            importEntry.targetNode()
                        ));
                    }
                }
            }

            for (ImportEntry onDemandImport : onDemandStaticImports) {
                if (hasResolvableStaticField(onDemandImport.ownerName(), fieldName)) {
                    resolved.add(new SimpleSymbol(
                        SymbolKind.FIELD,
                        fieldName,
                        onDemandImport.ownerName() + "#" + fieldName,
                        referenceNode
                    ));
                }
            }

            return uniqueByQualifiedName(resolved);
        }

        public List<Symbol> resolveStaticImportedMethods(String methodName, SyntaxNode invocationNode, int argumentCountOrUnknown) {
            List<Symbol> resolved = new ArrayList<>();
            List<ImportEntry> singleStaticImports = staticSingleImportsByMemberName.get(methodName);
            if (singleStaticImports != null) {
                for (ImportEntry importEntry : singleStaticImports) {
                    if (hasResolvableStaticMethod(importEntry.ownerName(), methodName, argumentCountOrUnknown)) {
                        resolved.add(new SimpleSymbol(
                            SymbolKind.METHOD,
                            methodName,
                            importEntry.ownerName() + "#" + methodName,
                            importEntry.targetNode()
                        ));
                    }
                }
            }

            for (ImportEntry onDemandImport : onDemandStaticImports) {
                if (hasResolvableStaticMethod(onDemandImport.ownerName(), methodName, argumentCountOrUnknown)) {
                    resolved.add(new SimpleSymbol(
                        SymbolKind.METHOD,
                        methodName,
                        onDemandImport.ownerName() + "#" + methodName,
                        invocationNode
                    ));
                }
            }

            return uniqueByQualifiedName(resolved);
        }

        private static ImportIndex create(JavaRuleContext context) {
            List<ImportEntry> imports = collectImports(context);
            Map<String, List<ImportEntry>> staticSingleImportsByMemberName = new LinkedHashMap<>();
            List<ImportEntry> onDemandStaticImports = new ArrayList<>();
            for (ImportEntry importEntry : imports) {
                if (importEntry.isWildcard()) {
                    if (importEntry.isStatic())
                        onDemandStaticImports.add(importEntry);
                    continue;
                }
                if (importEntry.isStatic()) {
                    staticSingleImportsByMemberName
                        .computeIfAbsent(importEntry.importedName(), ignored -> new ArrayList<>())
                        .add(importEntry);
                }
            }

            Set<String> localQualifiedTypeNames = collectLocalQualifiedTypeNames(context);
            Set<String> availableQualifiedTypeNames = new HashSet<>(localQualifiedTypeNames);
            availableQualifiedTypeNames.addAll(context.jdkQualifiedTypeNames());

            Map<String, Set<String>> localStaticFieldsByOwner = new LinkedHashMap<>();
            Map<String, Map<String, Set<Integer>>> localStaticMethodAritiesByOwner = new LinkedHashMap<>();
            collectLocalStaticMembers(context, localStaticFieldsByOwner, localStaticMethodAritiesByOwner);

            return new ImportIndex(
                List.copyOf(imports),
                copyListMap(staticSingleImportsByMemberName),
                List.copyOf(onDemandStaticImports),
                Set.copyOf(localQualifiedTypeNames),
                Set.copyOf(availableQualifiedTypeNames),
                context.jdkClassStubsByQualifiedName(),
                copySetMap(localStaticFieldsByOwner),
                copyNestedSetMap(localStaticMethodAritiesByOwner)
            );
        }

        private static List<ImportEntry> collectImports(JavaRuleContext context) {
            List<ImportEntry> imports = new ArrayList<>();
            context.traverse(node -> {
                if (!JAVA_IMPORT_DECLARATION.equals(node.kind().id()))
                    return;
                SyntaxNode target = context.directChild(node, JAVA_IMPORT_TARGET);
                if (target == null)
                    return;
                String qualifiedTarget = context.canonicalQualifiedName(target);
                if (qualifiedTarget == null || qualifiedTarget.isBlank())
                    return;
                boolean isStatic = context.hasTokenKind(node, JavaTokenType.STATIC_KEYWORD);
                boolean isWildcard = qualifiedTarget.endsWith(".*");
                String ownerName = isWildcard
                    ? qualifiedTarget.substring(0, qualifiedTarget.length() - 2)
                    : context.packagePrefix(qualifiedTarget);
                String importedName = isWildcard ? "*" : context.lastSegment(qualifiedTarget);
                imports.add(new ImportEntry(node, target, qualifiedTarget, ownerName, importedName, isStatic, isWildcard));
            });
            return imports;
        }

        private static Set<String> collectLocalQualifiedTypeNames(JavaRuleContext context) {
            Set<String> names = new HashSet<>();
            context.traverse(node -> context.declaredSymbol(node).ifPresent(symbol -> {
                if (context.isTypeSymbol(symbol.kind()))
                    symbol.qualifiedName().ifPresent(names::add);
            }));
            return names;
        }

        private static void collectLocalStaticMembers(
            JavaRuleContext context,
            Map<String, Set<String>> localStaticFieldsByOwner,
            Map<String, Map<String, Set<Integer>>> localStaticMethodAritiesByOwner
        ) {
            context.traverse(node -> {
                Symbol symbol = context.declaredSymbol(node).orElse(null);
                if (symbol == null)
                    return;
                if (symbol.kind() != SymbolKind.FIELD && symbol.kind() != SymbolKind.METHOD)
                    return;

                String qualifiedName = symbol.qualifiedName().orElse(null);
                if (qualifiedName == null || qualifiedName.isBlank())
                    return;

                int separator = qualifiedName.indexOf('#');
                if (separator <= 0 || separator >= qualifiedName.length() - 1)
                    return;
                if (!isStaticMemberSymbol(context, symbol))
                    return;

                String ownerName = qualifiedName.substring(0, separator);
                String memberName = qualifiedName.substring(separator + 1);
                if (symbol.kind() == SymbolKind.FIELD) {
                    localStaticFieldsByOwner.computeIfAbsent(ownerName, ignored -> new HashSet<>()).add(memberName);
                } else {
                    int arity = methodDeclarationArity(context, symbol);
                    localStaticMethodAritiesByOwner
                        .computeIfAbsent(ownerName, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(memberName, ignored -> new HashSet<>())
                        .add(arity);
                }
            });
        }

        private static boolean isStaticMemberSymbol(JavaRuleContext context, Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return false;
            if (context.hasTokenKind(declaration, JavaTokenType.STATIC_KEYWORD))
                return true;
            return declaration.parent()
                .map(parent -> context.hasTokenKind(parent, JavaTokenType.STATIC_KEYWORD))
                .orElse(false);
        }

        private static int methodDeclarationArity(JavaRuleContext context, Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return -1;
            SyntaxNode parameterList = context.directChild(declaration, JAVA_PARAMETER_LIST);
            if (parameterList == null)
                return -1;

            int count = 0;
            for (SyntaxNode child : parameterList.children()) {
                if (JAVA_PARAMETER.equals(child.kind().id()))
                    count++;
            }
            return count;
        }

        private boolean hasResolvableStaticField(String ownerQualifiedName, String fieldName) {
            Set<String> localFields = localStaticFieldsByOwner.get(ownerQualifiedName);
            if (localFields != null && localFields.contains(fieldName))
                return true;

            ClassStub jdkStub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.fields().stream()
                .anyMatch(field -> field.name().equals(fieldName) && Modifier.isStatic(field.modifiers()));
        }

        private boolean hasResolvableStaticMethod(String ownerQualifiedName, String methodName, int argumentCountOrUnknown) {
            Map<String, Set<Integer>> localMethods = localStaticMethodAritiesByOwner.get(ownerQualifiedName);
            if (localMethods != null) {
                Set<Integer> arities = localMethods.get(methodName);
                if (arities != null && !arities.isEmpty()) {
                    if (argumentCountOrUnknown < 0 || arities.contains(argumentCountOrUnknown))
                        return true;
                }
            }

            ClassStub jdkStub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.methods().stream()
                .anyMatch(method ->
                    method.name().equals(methodName)
                        && Modifier.isStatic(method.modifiers())
                        && (argumentCountOrUnknown < 0 || method.parameters().size() == argumentCountOrUnknown)
                );
        }

        private static Map<String, List<ImportEntry>> copyListMap(Map<String, List<ImportEntry>> source) {
            Map<String, List<ImportEntry>> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            return Map.copyOf(copy);
        }

        private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> source) {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            return Map.copyOf(copy);
        }

        private static Map<String, Map<String, Set<Integer>>> copyNestedSetMap(Map<String, Map<String, Set<Integer>>> source) {
            Map<String, Map<String, Set<Integer>>> copy = new LinkedHashMap<>();
            source.forEach((owner, members) -> {
                Map<String, Set<Integer>> memberCopy = new LinkedHashMap<>();
                members.forEach((member, arities) -> memberCopy.put(member, Set.copyOf(arities)));
                copy.put(owner, Map.copyOf(memberCopy));
            });
            return Map.copyOf(copy);
        }

        private static List<Symbol> uniqueByQualifiedName(List<Symbol> symbols) {
            Map<String, Symbol> deduped = new LinkedHashMap<>();
            for (Symbol symbol : symbols) {
                String key = symbol.qualifiedName().orElse(symbol.simpleName());
                deduped.putIfAbsent(key, symbol);
            }
            return List.copyOf(deduped.values());
        }
    }
}
