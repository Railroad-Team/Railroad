package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuleContextTest {

    @Test
    void exposesImportAndStaticResolutionHelpers() {
        String source = """
                import java.util.List;
                import static java.lang.Math.max;

                class Example {
                    List<String> values;

                    int run() {
                        return max(1, 2);
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        List<SyntaxNode> invocations = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION");
        SyntaxNode maxInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("max(1, 2)"))
                .findFirst()
                .orElse(null);

        assertNotNull(maxInvocation);
        assertEquals(2, context.importEntries().size());
        assertTrue(context.isResolvableType("java.util.List"));
        assertTrue(context.hasResolvableStaticMember("java.lang.Math", "max"));
        assertEquals(SymbolKind.METHOD, context.resolveStaticImportedMethods("max", maxInvocation, 2).getFirst().kind());
    }

    @Test
    void exposesTypeAndTraversalHelpers() {
        String source = """
                class Example {
                    void run() {
                        int value = 1;
                        boolean bad = value;
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        List<SyntaxNode> variableDeclarators = context.nodesOfKind("JAVA_VARIABLE_DECLARATOR");
        SyntaxNode badDeclarator = variableDeclarators.stream()
                .filter(node -> syntaxText(node).contains("bad = value"))
                .findFirst()
                .orElse(null);
        SyntaxNode initializer = badDeclarator == null ? null : context.firstDirectExpressionChild(badDeclarator);

        assertNotNull(badDeclarator);
        assertNotNull(initializer);
        assertEquals("boolean", context.declaredTypeOfVariable(badDeclarator).displayName());
        assertEquals("int", context.inferredType(initializer).orElseThrow().displayName());
        assertFalse(context.isAssignable(
                context.declaredTypeOfVariable(badDeclarator),
                context.inferredType(initializer).orElseThrow()
        ));

        List<String> kindIds = new ArrayList<>();
        context.traverse(node -> kindIds.add(node.kind().id()));
        assertTrue(kindIds.contains("JAVA_COMPILATION_UNIT"));
        assertTrue(kindIds.contains("JAVA_VARIABLE_DECLARATOR"));
    }

    @Test
    void exposesAccessibilityHelpers() {
        String source = """
                class Secret {
                    private int value;

                    private void ping() {
                    }
                }

                class Other {
                    void run(Secret secret) {
                        secret.value = 1;
                        secret.ping();
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        SyntaxNode fieldAccess = context.nodesOfKind("JAVA_FIELD_ACCESS_EXPRESSION").getFirst();
        SyntaxNode invocation = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION").getFirst();
        Symbol field = context.resolvedSymbol(fieldAccess).orElseThrow();
        Symbol method = context.resolvedSymbol(invocation).orElseThrow();

        assertEquals("", context.currentPackageName());
        assertEquals("Secret", context.ownerQualifiedName(field).orElseThrow());
        assertTrue(java.lang.reflect.Modifier.isPrivate(context.symbolModifiers(field)));
        assertFalse(context.isSymbolAccessible(field, fieldAccess));
        assertFalse(context.isSymbolAccessible(method, invocation));
    }

    @Test
    void exposesHierarchyAndMethodHelpers() {
        String source = """
                interface Worker {
                    void run();
                }

                abstract class Base implements Worker {
                }

                class Child extends Base {
                    public void run() {
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);

        assertEquals(List.of("Worker"), context.directSuperTypeNames("Base"));
        assertEquals(List.of("Base"), context.directSuperTypeNames("Child"));
        assertTrue(context.isInterfaceType("Worker"));
        assertTrue(context.isAbstractType("Base"));
        assertTrue(context.isSubtype("Child", "Worker"));
        assertEquals(1, context.declaredMethodDescriptors("Child").size());
        assertFalse(context.inheritedMethodDescriptors("Child").isEmpty());
    }

    @Test
    void exposesFieldHelpers() {
        String source = """
                class Base {
                    protected Number baseValue;
                }

                class Child extends Base {
                    private String name;
                }
                """;

        JavaRuleContext context = contextFor(source);

        assertEquals(1, context.declaredFieldDescriptors("Child").size());
        assertEquals("name", context.declaredFieldDescriptors("Child").getFirst().name());
        assertEquals("java.lang.String", context.declaredFieldDescriptors("Child").getFirst().type().displayName());
        assertEquals(1, context.inheritedFieldDescriptors("Child").size());
        assertEquals("baseValue", context.inheritedFieldDescriptors("Child").getFirst().name());
        assertEquals("java.lang.Number", context.inheritedFieldDescriptors("Child").getFirst().type().displayName());
    }

    @Test
    void exposesDirectModifierHelpers() {
        String source = """
                public public abstract class Example {
                    private static final int VALUE = 1;
                }
                """;

        JavaRuleContext context = contextFor(source);
        SyntaxNode typeDeclaration = context.nodesOfKind("JAVA_CLASS_DECLARATION").getFirst();
        SyntaxNode fieldDeclaration = context.nodesOfKind("JAVA_FIELD_DECLARATION").getFirst();

        assertTrue(context.hasDirectModifierToken(typeDeclaration, JavaTokenType.PUBLIC_KEYWORD));
        assertTrue(context.hasDirectModifierToken(typeDeclaration, JavaTokenType.ABSTRACT_KEYWORD));
        assertEquals(2, context.directModifierTokenCounts(typeDeclaration).get(JavaTokenType.PUBLIC_KEYWORD));
        assertTrue(context.directModifierTokens(fieldDeclaration).contains(JavaTokenType.STATIC_KEYWORD));
        assertTrue(context.directModifierTokens(fieldDeclaration).contains(JavaTokenType.FINAL_KEYWORD));
    }

    @Test
    void exposesExceptionHelpers() {
        String source = """
                class Example {
                    void run() throws java.io.IOException {
                        try (java.io.FileInputStream in = new java.io.FileInputStream("x")) {
                            Thread.sleep(1L);
                        } catch (java.lang.InterruptedException | java.io.IOException exception) {
                            throw new java.io.IOException();
                        }
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        SyntaxNode method = context.nodesOfKind("JAVA_METHOD_DECLARATION").getFirst();
        SyntaxNode invocation = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION").getFirst();
        SyntaxNode catchClause = context.nodesOfKind("JAVA_CATCH_CLAUSE").getFirst();
        SyntaxNode tryResource = context.nodesOfKind("JAVA_TRY_RESOURCE").getFirst();
        Symbol resolved = context.resolvedSymbol(invocation).orElseThrow();

        assertEquals(List.of("java.io.IOException"), context.declaredThrownTypeNames(method));
        assertEquals(
                List.of("java.lang.InterruptedException", "java.io.IOException"),
                context.catchParameterTypeNames(catchClause)
        );
        assertTrue(context.thrownTypeNames(resolved).contains("java.lang.InterruptedException"));
        assertTrue(context.isCheckedExceptionType("java.io.IOException"));
        assertFalse(context.isCheckedExceptionType("java.lang.RuntimeException"));
        assertEquals("java.io.FileInputStream", context.tryResourceTypeName(tryResource));
        assertTrue(context.closeThrownTypeNames("java.io.FileInputStream").contains("java.io.IOException"));
    }

    @Test
    void exposesInvocationHelpers() {
        String source = """
                class Example {
                    void run(java.util.Optional<String> opt) {
                        opt.isPresent();
                        opt.get();
                        ping(1);
                    }

                    void ping(int value) {
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        List<SyntaxNode> invocations = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION");
        SyntaxNode isPresentInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("opt.isPresent()"))
                .findFirst()
                .orElse(null);
        SyntaxNode getInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("opt.get()"))
                .findFirst()
                .orElse(null);
        SyntaxNode pingInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("ping(1)"))
                .findFirst()
                .orElse(null);

        assertNotNull(isPresentInvocation);
        assertNotNull(getInvocation);
        assertNotNull(pingInvocation);
        assertTrue(context.isMethodInvocationNamed(isPresentInvocation, "isPresent"));
        assertTrue(context.isMethodInvocationNamed(getInvocation, "get"));
        assertFalse(context.isMethodInvocationNamed(getInvocation, "isPresent"));
        assertTrue(context.hasNoArguments(isPresentInvocation));
        assertTrue(context.hasNoArguments(getInvocation));
        assertFalse(context.hasNoArguments(pingInvocation));
        assertEquals("opt", context.simpleReceiverName(isPresentInvocation));
        assertEquals("opt", context.simpleReceiverName(getInvocation));
        assertNull(context.simpleReceiverName(pingInvocation));
    }

    @Test
    void exposesConditionAndOperatorHelpers() {
        String source = """
                class Example {
                    void run(boolean flag) {
                        if (flag && true) {
                        } else {
                        }

                        while (flag) {
                        }

                        do {
                        } while (flag);

                        for (; flag; ) {
                        }
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        SyntaxNode ifStatement = context.nodesOfKind("JAVA_IF_STATEMENT").getFirst();
        SyntaxNode whileStatement = context.nodesOfKind("JAVA_WHILE_STATEMENT").getFirst();
        SyntaxNode doWhileStatement = context.nodesOfKind("JAVA_DO_WHILE_STATEMENT").getFirst();
        SyntaxNode forStatement = context.nodesOfKind("JAVA_FOR_STATEMENT").getFirst();
        SyntaxNode ifCondition = context.conditionOf(ifStatement);
        SyntaxNode whileCondition = context.conditionOf(whileStatement);
        SyntaxNode doWhileCondition = context.conditionOf(doWhileStatement);
        SyntaxNode forCondition = context.conditionOf(forStatement);

        assertNotNull(ifCondition);
        assertNotNull(whileCondition);
        assertNotNull(doWhileCondition);
        assertNotNull(forCondition);
        assertTrue(context.hasOperatorToken(context.unwrapTransparentExpression(ifCondition), JavaTokenType.AND));
        assertFalse(context.hasOperatorToken(whileCondition, JavaTokenType.AND));
        assertEquals("flag", context.simpleExpressionKey(whileCondition));
        assertEquals("flag", context.simpleExpressionKey(doWhileCondition));
        assertEquals("flag", context.simpleExpressionKey(forCondition));
    }

    @Test
    void exposesOptionalGetInvocationTypingHelpers() {
        String source = """
                class Example {
                    String run(java.util.Optional<String> opt) {
                        return opt.get();
                    }
                }
                """;

        JavaRuleContext context = contextFor(source);
        SyntaxNode invocation = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION").getFirst();
        SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));

        assertTrue(context.isMethodInvocationNamed(invocation, "get"));
        assertTrue(context.hasNoArguments(invocation));
        assertEquals("opt", context.simpleReceiverName(invocation));
        assertNotNull(receiver);
        Symbol receiverSymbol = context.resolvedSymbol(receiver).orElseThrow();
        SyntaxNode declaration = receiverSymbol.declaration().orElseThrow();
        SyntaxNode typeReference = context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());

        assertNotNull(typeReference);
        assertTrue(context.resolveQualifiedTypeName(typeReference).startsWith("java.util.Optional"));
    }

    private static JavaRuleContext contextFor(String source) {
        return new JavaRuleContext(
                Path.of("Example.java"),
                source,
                JavaSemanticAnalyzer.analyzeFacts(source)
        );
    }

    private static String syntaxText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return token.text();

        StringBuilder builder = new StringBuilder();
        for (SyntaxNode child : node.children())
            builder.append(syntaxText(child));
        return builder.toString();
    }
}
