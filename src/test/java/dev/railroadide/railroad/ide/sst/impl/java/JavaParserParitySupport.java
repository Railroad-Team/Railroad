package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;

final class JavaParserParitySupport {
    static final Set<String> TOP_LEVEL_TYPE_KIND_IDS = Set.of(
            JavaSyntaxKinds.CLASS_DECLARATION.id(),
            JavaSyntaxKinds.INTERFACE_DECLARATION.id(),
            JavaSyntaxKinds.ENUM_DECLARATION.id(),
            JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id(),
            JavaSyntaxKinds.RECORD_DECLARATION.id(),
            JavaSyntaxKinds.EMPTY_TYPE_DECLARATION.id()
    );

    private static final String MISSING_TOKEN_PREFIX = "JAVA_MISSING_";
    private static final String MODULE_DIRECTIVE_SUFFIX = "_DIRECTIVE";

    private JavaParserParitySupport() {
    }

    static List<Path> collectProjectJavaSources() throws IOException {
        List<Path> roots = List.of(Path.of("src", "main", "java"), Path.of("src", "test", "java"));
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root))
                continue;

            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(files::add);
            }
        }

        files.sort(Comparator.naturalOrder());
        return List.copyOf(files);
    }

    static ParityResult analyzeSyntaxOnly(String source) {
        SyntaxTree syntaxTree = JavaSyntaxParser.parse(source);
        String syntaxText = JavaParserTestSupport.syntaxText(syntaxTree);
        TopLevelShape syntaxTopLevelShape = topLevelShapeFromSyntax(syntaxTree);
        SyntaxDiagnostics syntaxDiagSummary = summarizeSyntaxDiagnostics(syntaxTree);
        return new ParityResult(
                source,
                syntaxText,
                syntaxTopLevelShape,
                syntaxDiagSummary
        );
    }

    static List<String> syntaxOnlyIssues(ParityResult result) {
        List<String> issues = new ArrayList<>();
        if (!result.source().equals(result.syntaxText())) {
            issues.add("syntax roundtrip mismatch (source and syntax text differ)");
        }

        if (result.syntaxDiagnostics().errors() != 0) {
            issues.add("syntax parser produced recovery markers (errors=" + result.syntaxDiagnostics().errors() +
                    ", missingTokens=" + result.syntaxDiagnostics().missingTokens() +
                    ", errorNodes=" + result.syntaxDiagnostics().errorNodes() + ")");
        }

        return List.copyOf(issues);
    }

    static String formatIssues(Path sourcePath, List<String> issues, ParityResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(sourcePath).append('\n');
        builder.append("syntaxTopLevel=").append(result.syntaxTopLevelShape()).append('\n');
        builder.append("syntaxDiagnostics=").append(result.syntaxDiagnostics()).append('\n');
        for (String issue : issues) {
            builder.append("- ").append(issue).append('\n');
        }
        return builder.toString().trim();
    }

    static String formatIssues(String label, List<String> issues, ParityResult result) {
        return formatIssues(Path.of(label), issues, result);
    }

    private static SyntaxDiagnostics summarizeSyntaxDiagnostics(SyntaxTree syntaxTree) {
        long missingTokens = JavaParserTestSupport.collectSyntaxTokens(syntaxTree).stream()
                .map(token -> token.kind().id())
                .filter(kindId -> kindId.equals(SyntaxKind.MISSING_TOKEN.id()) || kindId.startsWith(MISSING_TOKEN_PREFIX))
                .count();

        long errorNodes = countNodesOfKind(syntaxTree.root(), JavaSyntaxKinds.ERROR.id());
        long errors = missingTokens + errorNodes;
        return new SyntaxDiagnostics(errors, 0, missingTokens, errorNodes);
    }

    private static long countNodesOfKind(SyntaxNode root, String kindId) {
        long count = 0;
        Deque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            SyntaxNode node = stack.pop();
            if (node.kind().id().equals(kindId))
                count++;
            for (SyntaxNode child : node.children()) {
                stack.push(child);
            }
        }
        return count;
    }

    private static TopLevelShape topLevelShapeFromSyntax(SyntaxTree syntaxTree) {
        int packageCount = 0;
        int importCount = 0;
        int typeCount = 0;
        int moduleCount = 0;
        int moduleDirectiveCount = 0;

        for (SyntaxNode child : syntaxTree.root().children()) {
            String kindId = child.kind().id();
            if (kindId.equals(JavaSyntaxKinds.PACKAGE_DECLARATION.id())) {
                packageCount++;
            } else if (kindId.equals(JavaSyntaxKinds.IMPORT_DECLARATION.id())) {
                importCount++;
            } else if (TOP_LEVEL_TYPE_KIND_IDS.contains(kindId)) {
                typeCount++;
            } else if (kindId.equals(JavaSyntaxKinds.MODULE_DECLARATION.id())) {
                moduleCount++;
                moduleDirectiveCount += countModuleDirectives(child);
            }
        }

        return new TopLevelShape(packageCount, importCount, typeCount, moduleCount, moduleDirectiveCount);
    }

    private static int countModuleDirectives(SyntaxNode moduleNode) {
        int count = 0;
        Deque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(moduleNode);
        while (!stack.isEmpty()) {
            SyntaxNode node = stack.pop();
            String kindId = node.kind().id();
            if (kindId.startsWith("JAVA_MODULE_") && kindId.endsWith(MODULE_DIRECTIVE_SUFFIX))
                count++;
            for (SyntaxNode child : node.children()) {
                stack.push(child);
            }
        }
        return count;
    }

    static String readSource(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    record TopLevelShape(
            int packageDeclarations,
            int importDeclarations,
            int typeDeclarations,
            int moduleDeclarations,
            int moduleDirectives
    ) {
    }

    record SyntaxDiagnostics(long errors, long warnings, long missingTokens, long errorNodes) {
    }

    record ParityResult(
            String source,
            String syntaxText,
            TopLevelShape syntaxTopLevelShape,
            SyntaxDiagnostics syntaxDiagnostics
    ) {
    }
}
