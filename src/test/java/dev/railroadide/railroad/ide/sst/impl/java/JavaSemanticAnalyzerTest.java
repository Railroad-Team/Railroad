package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class JavaSemanticAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void collectsTopLevelAndMemberDeclarations() {
        String source = """
                package demo.sample;
                import java.util.List;

                class Outer {
                    int first, second;

                    Outer(int seed) {
                    }

                    void run(int value) {
                    }

                    class Inner {
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarations(source);

        assertSymbol(model.rootScope().lookupLocal("sample"), SymbolKind.PACKAGE);
        assertSymbol(model.rootScope().lookupLocal("java.util.List"), SymbolKind.IMPORT);
        assertSymbol(model.rootScope().lookupLocal("Outer"), SymbolKind.CLASS);

        List<SyntaxNode> variableDeclarators = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.VARIABLE_DECLARATOR.id());
        assertEquals(2, variableDeclarators.size());
        for (SyntaxNode variableDeclarator : variableDeclarators) {
            Symbol symbol = model.declaredSymbol(variableDeclarator).orElse(null);
            assertNotNull(symbol);
            assertEquals(SymbolKind.FIELD, symbol.kind());
        }

        List<SyntaxNode> constructors = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id());
        assertEquals(1, constructors.size());
        Symbol constructorSymbol = model.declaredSymbol(constructors.getFirst()).orElse(null);
        assertNotNull(constructorSymbol);
        assertEquals(SymbolKind.CONSTRUCTOR, constructorSymbol.kind());
        assertEquals("Outer", constructorSymbol.simpleName());

        List<SyntaxNode> methods = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_DECLARATION.id());
        assertEquals(1, methods.size());
        Symbol methodSymbol = model.declaredSymbol(methods.getFirst()).orElse(null);
        assertNotNull(methodSymbol);
        assertEquals(SymbolKind.METHOD, methodSymbol.kind());
        assertEquals("run", methodSymbol.simpleName());

        List<SyntaxNode> parameters = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.PARAMETER.id());
        assertEquals(2, parameters.size());
        for (SyntaxNode parameter : parameters) {
            Symbol symbol = model.declaredSymbol(parameter).orElse(null);
            assertNotNull(symbol);
            assertEquals(SymbolKind.PARAMETER, symbol.kind());
        }

        List<SyntaxNode> classes = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.CLASS_DECLARATION.id());
        assertEquals(2, classes.size());
        boolean sawInner = classes.stream()
                .map(model::declaredSymbol)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .anyMatch(symbol -> symbol.kind() == SymbolKind.CLASS && "Inner".equals(symbol.simpleName()));
        assertTrue(sawInner);
    }

    @Test
    void emitsDuplicateDeclarationDiagnostics() {
        String source = """
                class DuplicateMembers {
                    int value;
                    int value;

                    void run(int x, int x) {
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarations(source);
        long duplicateDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code()))
                .count();

        assertTrue(duplicateDiagnostics >= 2);
        assertFalse(model.diagnostics().isEmpty());
    }

    @Test
    void resolvesNameExpressionsAndReportsUnresolvedNames() {
        String source = """
                class ResolverSample {
                    int field;

                    void run(int param) {
                        int local = param;
                        field = local;
                        missing = local;
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> nameExpressions = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.NAME_EXPRESSION.id());

        SyntaxNode paramRef = findNameExpression(nameExpressions, "param");
        SyntaxNode localRef = findNameExpression(nameExpressions, "local");
        SyntaxNode fieldRef = findNameExpression(nameExpressions, "field");
        SyntaxNode missingRef = findNameExpression(nameExpressions, "missing");

        assertEquals(SymbolKind.PARAMETER, model.resolvedSymbol(paramRef).orElseThrow().kind());
        assertEquals(SymbolKind.LOCAL_VARIABLE, model.resolvedSymbol(localRef).orElseThrow().kind());
        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(fieldRef).orElseThrow().kind());
        assertTrue(model.resolvedSymbol(missingRef).isEmpty());

        long unresolvedDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code()))
                .count();
        assertTrue(unresolvedDiagnostics >= 1);
    }

    @Test
    void infersBasicTypesAndReportsIncompatibleAssignments() {
        String source = """
                class TypeSample {
                    void run() {
                        int number = 1;
                        String text = "ok";
                        boolean bad = number;
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);

        SyntaxNode intLiteral = findLiteralExpression(model.syntaxTree().root(), "1");
        SyntaxNode stringLiteral = findLiteralExpression(model.syntaxTree().root(), "\"ok\"");

        assertEquals("int", model.inferredType(intLiteral).orElseThrow().displayName());
        assertEquals("String", model.inferredType(stringLiteral).orElseThrow().displayName());

        long incompatibleDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code()))
                .count();
        assertTrue(incompatibleDiagnostics >= 1);
    }

    @Test
    void reportsUnresolvedImportForUnknownNonPlatformType() {
        String source = """
                package demo;
                import demo.LocalType;
                import demo.MissingType;
                import java.util.List;

                class LocalType {
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);

        long unresolvedImportDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_IMPORT".equals(diagnostic.code()))
                .count();

        assertEquals(1, unresolvedImportDiagnostics);
    }

    @Test
    void reportsDuplicateImports() {
        String source = """
                import java.util.List;
                import java.util.List;

                class UsesList {
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        long duplicateImportDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_DUPLICATE_IMPORT".equals(diagnostic.code()))
                .count();

        assertEquals(1, duplicateImportDiagnostics);
    }

    @Test
    void reportsAmbiguousSingleTypeImports() {
        String source = """
                import java.util.List;
                import java.awt.List;

                class UsesList {
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        long ambiguousImportDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_AMBIGUOUS_IMPORT".equals(diagnostic.code()))
                .count();

        assertEquals(1, ambiguousImportDiagnostics);
    }

    @Test
    void reportsUnresolvedStaticImportOwner() {
        String source = """
                import static missing.pkg.Utility.value;

                class UsesStaticImport {
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        long unresolvedImportDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_IMPORT".equals(diagnostic.code()))
                .count();

        assertEquals(1, unresolvedImportDiagnostics);
    }

    @Test
    void resolvesSingleStaticImportsForFieldAndMethod() {
        String source = """
                import static java.lang.Math.PI;
                import static java.lang.Math.max;

                class StaticUses {
                    double p = PI;

                    int bigger() {
                        return max(1, 2);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> nameExpressions = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.NAME_EXPRESSION.id());
        SyntaxNode piRef = findNameExpression(nameExpressions, "PI");
        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(piRef).orElseThrow().kind());

        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());
        SyntaxNode maxInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("max("))
                .findFirst()
                .orElse(null);
        assertNotNull(maxInvocation);
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(maxInvocation).orElseThrow().kind());
    }

    @Test
    void resolvesWildcardStaticImportsForFieldAndMethod() {
        String source = """
                import static java.lang.Math.*;

                class StaticWildcardUses {
                    double p = PI;

                    int bigger() {
                        return max(1, 2);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> nameExpressions = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.NAME_EXPRESSION.id());
        SyntaxNode piRef = findNameExpression(nameExpressions, "PI");
        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(piRef).orElseThrow().kind());

        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());
        SyntaxNode maxInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("max("))
                .findFirst()
                .orElse(null);
        assertNotNull(maxInvocation);
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(maxInvocation).orElseThrow().kind());
    }

    @Test
    void reportsUnresolvedStaticImportMember() {
        String source = """
                import static java.lang.Math.notARealMember;

                class StaticImportMissingMember {
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        long unresolvedImportDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_IMPORT".equals(diagnostic.code()))
                .count();

        assertEquals(1, unresolvedImportDiagnostics);
    }

    @Test
    void staticImportMethodResolutionUsesArity() {
        String source = """
                import static java.lang.Math.max;

                class ArityCheck {
                    int ok() {
                        return max(1, 2);
                    }

                    int bad() {
                        return max(1);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());
        SyntaxNode okInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("max(1, 2)"))
                .findFirst()
                .orElse(null);
        SyntaxNode badInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("max(1)"))
                .findFirst()
                .orElse(null);
        assertNotNull(okInvocation);
        assertNotNull(badInvocation);

        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(okInvocation).orElseThrow().kind());
        assertTrue(model.resolvedSymbol(badInvocation).isEmpty());
    }

    @Test
    void reportsAmbiguousNameFromStaticOnDemandImports() {
        String source = """
                import static java.lang.Math.*;
                import static java.lang.StrictMath.*;

                class AmbiguousName {
                    int value() {
                        return max(1, 2);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        long ambiguousNameDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_AMBIGUOUS_NAME".equals(diagnostic.code()))
                .count();

        assertTrue(ambiguousNameDiagnostics >= 1);
    }

    @Test
    void resolvesTypeNamesFromProjectIndexInSamePackage() throws IOException {
        JavaProjectSemanticIndex index = buildProjectIndex(
                "src/main/java/demo/Shared.java", """
                        package demo;

                        class Shared {
                        }
                        """
        );

        String source = """
                package demo;

                class UsesShared {
                    Shared value;
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source, index);
        SyntaxNode sharedTypeRef = findTypeReference(model.syntaxTree().root(), "Shared");
        assertEquals("demo.Shared", model.inferredType(sharedTypeRef).orElseThrow().displayName());
    }

    @Test
    void resolvesTypeNamesFromProjectIndexViaExplicitImport() throws IOException {
        JavaProjectSemanticIndex index = buildProjectIndex(
                "src/main/java/lib/Shared.java", """
                        package lib;

                        class Shared {
                        }
                        """
        );

        String source = """
                package app;
                import lib.Shared;

                class UsesShared {
                    Shared value;
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source, index);
        SyntaxNode sharedTypeRef = findTypeReference(model.syntaxTree().root(), "Shared");
        assertEquals("lib.Shared", model.inferredType(sharedTypeRef).orElseThrow().displayName());
    }

    @Test
    void resolvesTypeNamesFromProjectIndexViaWildcardImport() throws IOException {
        JavaProjectSemanticIndex index = buildProjectIndex(
                "src/main/java/lib/Shared.java", """
                        package lib;

                        class Shared {
                        }
                        """
        );

        String source = """
                package app;
                import lib.*;

                class UsesShared {
                    Shared value;
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source, index);
        SyntaxNode sharedTypeRef = findTypeReference(model.syntaxTree().root(), "Shared");
        assertEquals("lib.Shared", model.inferredType(sharedTypeRef).orElseThrow().displayName());
    }

    @Test
    void resolvesStaticImportsFromProjectIndex() throws IOException {
        JavaProjectSemanticIndex index = buildProjectIndex(
                "src/main/java/lib/Util.java", """
                        package lib;

                        class Util {
                            static int VALUE = 1;

                            static int twice(int value) {
                                return value * 2;
                            }
                        }
                        """
        );

        String source = """
                package app;
                import static lib.Util.VALUE;
                import static lib.Util.twice;

                class UsesUtil {
                    int use() {
                        return twice(VALUE);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source, index);
        List<SyntaxNode> nameExpressions = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.NAME_EXPRESSION.id());
        SyntaxNode valueRef = findNameExpression(nameExpressions, "VALUE");
        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(valueRef).orElseThrow().kind());

        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());
        SyntaxNode twiceInvocation = invocations.stream()
                .filter(node -> syntaxText(node).contains("twice(VALUE)"))
                .findFirst()
                .orElse(null);
        assertNotNull(twiceInvocation);
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(twiceInvocation).orElseThrow().kind());
    }

    @Test
    void resolvesDirectProjectMemberAccessAndConstructors() throws IOException {
        JavaProjectSemanticIndex index = buildProjectIndex(
                "src/main/java/lib/Util.java", """
                        package lib;

                        class Util {
                            static int VALUE = 1;

                            static int twice(int value) {
                                return value * 2;
                            }
                        }
                        """,
                "src/main/java/lib/Box.java", """
                        package lib;

                        class Box {
                            Box(int value) {
                            }
                        }
                        """
        );

        String source = """
                package app;
                import lib.Util;
                import lib.Box;

                class UsesMembers {
                    int use() {
                        new Box(1);
                        return Util.twice(Util.VALUE);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source, index);

        List<SyntaxNode> fieldAccesses = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id());
        SyntaxNode utilValue = fieldAccesses.stream()
                .filter(node -> syntaxText(node).contains("Util.VALUE"))
                .findFirst()
                .orElse(null);
        assertNotNull(utilValue);
        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(utilValue).orElseThrow().kind());

        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());
        SyntaxNode utilTwice = invocations.stream()
                .filter(node -> syntaxText(node).contains("Util.twice(Util.VALUE)"))
                .findFirst()
                .orElse(null);
        assertNotNull(utilTwice);
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(utilTwice).orElseThrow().kind());

        List<SyntaxNode> creations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id());
        SyntaxNode boxCreation = creations.stream()
                .filter(node -> syntaxText(node).contains("new Box(1)"))
                .findFirst()
                .orElse(null);
        assertNotNull(boxCreation);
        assertEquals(SymbolKind.CONSTRUCTOR, model.resolvedSymbol(boxCreation).orElseThrow().kind());
    }

    @Test
    void resolvesExplicitMemberAccessAndMethodCalls() {
        String source = """
                class Members {
                    void run(String text) {
                        int first = text.length();
                        int second = Math.max(1, 2);
                        System.out.println(first + second);
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> fieldAccesses = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id());
        List<SyntaxNode> invocations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id());

        SyntaxNode systemOut = fieldAccesses.stream()
                .filter(node -> syntaxText(node).contains("System.out"))
                .findFirst()
                .orElse(null);
        SyntaxNode lengthCall = invocations.stream()
                .filter(node -> syntaxText(node).contains("text.length()"))
                .findFirst()
                .orElse(null);
        SyntaxNode maxCall = invocations.stream()
                .filter(node -> syntaxText(node).contains("Math.max(1, 2)"))
                .findFirst()
                .orElse(null);
        SyntaxNode printlnCall = invocations.stream()
                .filter(node -> syntaxText(node).contains("println(first + second)"))
                .findFirst()
                .orElse(null);

        assertNotNull(systemOut);
        assertNotNull(lengthCall);
        assertNotNull(maxCall);
        assertNotNull(printlnCall);

        assertEquals(SymbolKind.FIELD, model.resolvedSymbol(systemOut).orElseThrow().kind());
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(lengthCall).orElseThrow().kind());
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(maxCall).orElseThrow().kind());
        assertEquals(SymbolKind.METHOD, model.resolvedSymbol(printlnCall).orElseThrow().kind());
    }

    @Test
    void resolvesConstructorsAndReportsUnresolvedCallsAndMembers() {
        String source = """
                class Calls {
                    static class Box {
                        Box(int value) {
                        }
                    }

                    void run(String text) {
                        new Box(1);
                        new Box("bad");
                        text.missing();
                        System.missingField;
                    }
                }
                """;

        SemanticModel model = JavaSemanticAnalyzer.analyze(source);
        List<SyntaxNode> creations = nodesOfKind(model.syntaxTree().root(), JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id());
        SyntaxNode okCreation = creations.stream()
                .filter(node -> syntaxText(node).contains("new Box(1)"))
                .findFirst()
                .orElse(null);
        SyntaxNode badCreation = creations.stream()
                .filter(node -> syntaxText(node).contains("new Box(\"bad\")"))
                .findFirst()
                .orElse(null);

        assertNotNull(okCreation);
        assertNotNull(badCreation);
        assertEquals(SymbolKind.CONSTRUCTOR, model.resolvedSymbol(okCreation).orElseThrow().kind());
        assertTrue(model.resolvedSymbol(badCreation).isEmpty());

        long unresolvedCallDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_CALL".equals(diagnostic.code()))
                .count();
        long unresolvedMemberDiagnostics = model.diagnostics().stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_MEMBER".equals(diagnostic.code()))
                .count();

        assertTrue(unresolvedCallDiagnostics >= 2);
        assertTrue(unresolvedMemberDiagnostics >= 1);
    }

    private static void assertSymbol(List<Symbol> symbols, SymbolKind expectedKind) {
        Symbol symbol = symbols.stream().filter(candidate -> candidate.kind() == expectedKind).findFirst().orElse(null);
        assertNotNull(symbol);
    }

    private static List<SyntaxNode> nodesOfKind(SyntaxNode root, String kindId) {
        Objects.requireNonNull(root, "root");
        List<SyntaxNode> result = new ArrayList<>();
        collectNodesOfKind(root, kindId, result);
        return List.copyOf(result);
    }

    private static void collectNodesOfKind(SyntaxNode node, String kindId, List<SyntaxNode> out) {
        if (kindId.equals(node.kind().id()))
            out.add(node);
        for (SyntaxNode child : node.children())
            collectNodesOfKind(child, kindId, out);
    }

    private static SyntaxNode findNameExpression(List<SyntaxNode> nameExpressions, String targetName) {
        SyntaxNode match = nameExpressions.stream()
                .filter(node -> targetName.equals(syntaxText(node).trim()))
                .findFirst()
                .orElse(null);
        assertNotNull(match);
        return match;
    }

    private static SyntaxNode findLiteralExpression(SyntaxNode root, String literalText) {
        List<SyntaxNode> literals = nodesOfKind(root, JavaSyntaxKinds.LITERAL_EXPRESSION.id());
        SyntaxNode match = literals.stream()
                .filter(node -> literalText.equals(syntaxText(node).trim()))
                .findFirst()
                .orElse(null);
        assertNotNull(match);
        return match;
    }

    private static SyntaxNode findTypeReference(SyntaxNode root, String typeText) {
        List<SyntaxNode> typeReferences = nodesOfKind(root, JavaSyntaxKinds.TYPE_REFERENCE.id());
        SyntaxNode match = typeReferences.stream()
                .filter(node -> typeText.equals(syntaxText(node).trim()))
                .findFirst()
                .orElse(null);
        assertNotNull(match);
        return match;
    }

    private static String syntaxText(SyntaxNode node) {
        if (node instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken token)
            return token.text();

        StringBuilder builder = new StringBuilder();
        for (SyntaxNode child : node.children())
            builder.append(syntaxText(child));
        return builder.toString();
    }

    private JavaProjectSemanticIndex buildProjectIndex(String relativePath, String source, String... additionalPathAndSourcePairs) throws IOException {
        writeProjectSource(relativePath, source);
        for (int index = 0; index < additionalPathAndSourcePairs.length; index += 2) {
            writeProjectSource(additionalPathAndSourcePairs[index], additionalPathAndSourcePairs[index + 1]);
        }
        return new JavaProjectSemanticIndexer().build(tempDir);
    }

    private void writeProjectSource(String relativePath, String source) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
