package dev.railroadide.railroad.formatter;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Applies Railroad's structural Java style rules before the whitespace formatter runs.
 *
 * <p>
 * The JDK compiler tree API is used deliberately: style rules operate on Java statements rather than text and
 * automatically understand the same language level as the project toolchain. Rules that need symbols can extend this
 * tool to run javac attribution and inspect elements through {@link Trees}.
 * </p>
 */
public final class RailroadJavaStyle {
    private static final List<String> JAVAC_OPTIONS = List.of("-proc:none", "--release", "25");

    private RailroadJavaStyle() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) {
            printUsageAndExit();
        }

        int pathStart;
        Path repository = null;
        String ratchetFrom = null;
        if ("--all".equals(args[1])) {
            pathStart = 2;
        } else if (args.length >= 5 && "--ratchet-from".equals(args[1])) {
            ratchetFrom = args[2];
            repository = Path.of(args[3]).toAbsolutePath().normalize();
            pathStart = 4;
        } else {
            printUsageAndExit();
            return;
        }

        if (pathStart >= args.length) {
            printUsageAndExit();
            return;
        }

        boolean checkOnly = "--check".equals(args[0]);
        List<Path> files = collectJavaFiles(args, pathStart);
        if (ratchetFrom != null) {
            files = retainChangedFiles(files, repository, ratchetFrom);
        }

        int changedFiles = 0;

        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            RewriteResult result = rewrite(source, file.toUri());
            if (result.source().equals(source))
                continue;

            changedFiles++;
            if (checkOnly) {
                for (int line : result.changedLines()) {
                    System.err.printf("%s:%d: Java source violates semantic formatting rules%n", file, line);
                }
            } else {
                Files.writeString(file, result.source(), StandardCharsets.UTF_8);
                System.out.println("Formatted " + file);
            }
        }

        if (checkOnly && changedFiles > 0) {
            System.err.printf(Locale.ROOT, "%d Java file%s require structural formatting. Run './gradlew format'.%n",
                changedFiles, changedFiles == 1 ? "" : "s");
            System.exit(1);
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: RailroadJavaStyle (--apply|--check) "
            + "(--all|--ratchet-from <revision> <repository>) <file-or-directory>...");
        System.exit(2);
    }

    public static String rewrite(String source) {
        return rewrite(source, URI.create("string:///RailroadStyleInput.java")).source();
    }

    private static RewriteResult rewrite(String source, URI sourceUri) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("A full JDK is required to run Railroad's Java formatter");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject input = new SourceFile(sourceUri, source);
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, JAVAC_OPTIONS, null, List.of(input));

        try {
            CompilationUnitTree unit = task.parse().iterator().next();
            failOnParseErrors(diagnostics);

            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            List<TextEdit> edits = new ArrayList<>();
            List<Integer> changedLines = new ArrayList<>();

            new SemanticStyleScanner(unit, positions, source, edits, changedLines).scan(unit, null);
            edits.sort(Comparator.<TextEdit>comparingInt(TextEdit::start).reversed()
                .thenComparing(Comparator.comparingInt(TextEdit::end).reversed()));

            var rewritten = new StringBuilder(source);
            for (TextEdit edit : edits) {
                rewritten.replace(edit.start(), edit.end(), edit.replacement());
            }

            changedLines.sort(Integer::compareTo);
            return new RewriteResult(rewritten.toString(), List.copyOf(changedLines));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse " + sourceUri, exception);
        }
    }

    private static List<Path> collectJavaFiles(String[] args, int pathStart) throws IOException {
        List<Path> files = new ArrayList<>();
        for (int index = pathStart; index < args.length; index++) {
            Path path = Path.of(args[index]);
            if (Files.isDirectory(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    paths.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.getFileName().toString().endsWith(".java"))
                        .forEach(files::add);
                }
            } else if (path.getFileName().toString().endsWith(".java")) {
                files.add(path);
            }
        }

        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static List<Path> retainChangedFiles(List<Path> candidates, Path repository, String revision)
        throws IOException {
        Set<Path> changed = new HashSet<>();
        changed.addAll(gitPaths(repository, "diff", "--name-only", "--diff-filter=ACMRTUXB", revision, "--"));
        changed.addAll(gitPaths(repository, "ls-files", "--others", "--exclude-standard", "--"));

        return candidates.stream()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .filter(changed::contains)
            .toList();
    }

    private static List<Path> gitPaths(Path repository, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(Arrays.asList(arguments));

        Process process = new ProcessBuilder(command).start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                var error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Git command failed (" + String.join(" ", command) + "):\n" + error);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while determining changed Java files", exception);
        }

        return output.lines()
            .filter(line -> !line.isBlank())
            .map(repository::resolve)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .toList();
    }

    private static void failOnParseErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> errors = diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getSource().toUri() + ":" + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT))
            .toList();
        if (!errors.isEmpty())
            throw new IllegalArgumentException("Java formatter could not parse source:\n" + String.join("\n", errors));
    }

    private record RewriteResult(String source, List<Integer> changedLines) {
    }

    private record TextEdit(int start, int end, String replacement) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(URI uri, String source) {
            super(uri, Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class SemanticStyleScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String source;
        private final List<TextEdit> edits;
        private final List<Integer> changedLines;

        private SemanticStyleScanner(CompilationUnitTree unit, SourcePositions positions, String source,
            List<TextEdit> edits, List<Integer> changedLines) {
            this.unit = unit;
            this.positions = positions;
            this.source = source;
            this.edits = edits;
            this.changedLines = changedLines;
        }

        @Override
        public Void visitIf(IfTree tree, Void unused) {
            inspect(tree.getThenStatement(), false);
            inspect(tree.getElseStatement(), true);
            return super.visitIf(tree, unused);
        }

        @Override
        public Void visitForLoop(ForLoopTree tree, Void unused) {
            inspect(tree.getStatement(), false);
            return super.visitForLoop(tree, unused);
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
            inspect(tree.getStatement(), false);
            return super.visitEnhancedForLoop(tree, unused);
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
            inspect(tree.getStatement(), false);
            return super.visitWhileLoop(tree, unused);
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
            inspect(tree.getStatement(), false);
            return super.visitDoWhileLoop(tree, unused);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            replaceExactConstructedLocalType(tree);
            replaceConventionalUnusedName(tree);
            return super.visitVariable(tree, unused);
        }

        private void replaceConventionalUnusedName(VariableTree variable) {
            String name = variable.getName().toString();
            if (!isConventionalUnusedName(name))
                return;

            List<? extends Tree> scope = unnamedVariableScope(variable);
            if (scope == null || containsIdentifier(scope, name))
                return;

            TextRange nameRange = findVariableName(variable, name);
            if (nameRange == null)
                return;

            edits.add(new TextEdit(nameRange.start(), nameRange.end(), "_"));
            changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(nameRange.start())));
        }

        private List<? extends Tree> unnamedVariableScope(VariableTree variable) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (parent instanceof BlockTree block) {
                if (variable.getInitializer() == null || !isLocalVariableStatement(variable, block))
                    return null;

                List<? extends StatementTree> statements = block.getStatements();
                for (int index = 0; index < statements.size(); index++) {
                    if (statements.get(index) == variable)
                        return statements.subList(index + 1, statements.size());
                }
                return null;
            }

            if (parent instanceof ForLoopTree loop) {
                if (loop.getInitializer().size() != 1 || loop.getInitializer().getFirst() != variable)
                    return null;
                List<Tree> scope = new ArrayList<>();
                if (loop.getCondition() != null) {
                    scope.add(loop.getCondition());
                }
                scope.addAll(loop.getUpdate());
                scope.add(loop.getStatement());
                return scope;
            }

            if (parent instanceof EnhancedForLoopTree loop && loop.getVariable() == variable)
                return List.of(loop.getStatement());

            if (parent instanceof CatchTree catchTree && catchTree.getParameter() == variable)
                return List.of(catchTree.getBlock());

            if (parent instanceof LambdaExpressionTree lambda && lambda.getParameters().stream()
                .anyMatch(parameter -> parameter == variable))
                return List.of(lambda.getBody());

            if (parent instanceof TryTree tryTree && tryTree.getResources().stream()
                .anyMatch(resource -> resource == variable))
                return List.of(tryTree);

            return null;
        }

        private TextRange findVariableName(VariableTree variable, String name) {
            int searchStart = variable.getType() == null ? startOf(variable) : endOf(variable.getType());
            int searchEnd = variable.getInitializer() == null
                ? endOf(variable)
                : startOf(variable.getInitializer());
            if (searchStart < 0 || searchEnd <= searchStart)
                return null;

            for (int offset = searchStart; offset < searchEnd;) {
                int codePoint = source.codePointAt(offset);
                if (!Character.isJavaIdentifierStart(codePoint)) {
                    offset += Character.charCount(codePoint);
                    continue;
                }

                int identifierEnd = offset + Character.charCount(codePoint);
                while (identifierEnd < searchEnd) {
                    int next = source.codePointAt(identifierEnd);
                    if (!Character.isJavaIdentifierPart(next))
                        break;
                    identifierEnd += Character.charCount(next);
                }

                if (source.regionMatches(offset, name, 0, name.length()) && identifierEnd - offset == name.length())
                    return new TextRange(offset, identifierEnd);
                offset = identifierEnd;
            }
            return null;
        }

        private static boolean containsIdentifier(List<? extends Tree> trees, String name) {
            var scanner = new IdentifierUsageScanner(name);
            for (Tree tree : trees) {
                if (tree != null) {
                    scanner.scan(tree, null);
                }
            }
            return scanner.found;
        }

        private static boolean isConventionalUnusedName(String name) {
            if (name.equals("ignored"))
                return true;
            if (name.isEmpty() || name.charAt(0) != '$')
                return false;
            return name.substring(1).chars().allMatch(Character::isDigit);
        }

        private void replaceExactConstructedLocalType(VariableTree variable) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (!isLocalVariableStatement(variable, parent)
                || variable.getType() == null
                || !variable.getModifiers().getAnnotations().isEmpty()
                || !(variable.getInitializer() instanceof NewClassTree construction)
                || construction.getClassBody() != null
                || construction.getEnclosingExpression() != null
                || !construction.getTypeArguments().isEmpty())
                return;

            int variableStart = startOf(variable);
            int variableEnd = endOf(variable);
            int declaredTypeStart = startOf(variable.getType());
            int declaredTypeEnd = endOf(variable.getType());
            int constructedTypeStart = startOf(construction.getIdentifier());
            int constructedTypeEnd = endOf(construction.getIdentifier());
            if (variableStart < 0 || variableEnd <= variableStart
                || declaredTypeStart < variableStart || declaredTypeEnd <= declaredTypeStart
                || constructedTypeStart < 0 || constructedTypeEnd <= constructedTypeStart
                || followedByComma(variableEnd))
                return;

            String declaredType = withoutWhitespace(source.substring(declaredTypeStart, declaredTypeEnd));
            String constructedType = withoutWhitespace(source.substring(constructedTypeStart, constructedTypeEnd));
            if (declaredType.equals("var") || !declaredType.equals(constructedType))
                return;

            edits.add(new TextEdit(declaredTypeStart, declaredTypeEnd, "var"));
            changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(declaredTypeStart)));
        }

        private boolean isLocalVariableStatement(VariableTree variable, Tree parent) {
            if (parent instanceof BlockTree block) {
                long declaredTypeStart = positions.getStartPosition(unit, variable.getType());
                long variablesWithSameTypeStart = block.getStatements().stream()
                    .filter(VariableTree.class::isInstance)
                    .map(VariableTree.class::cast)
                    .filter(candidate -> positions.getStartPosition(unit, candidate.getType()) == declaredTypeStart)
                    .count();
                return variablesWithSameTypeStart == 1;
            }

            if (parent instanceof ForLoopTree loop)
                return loop.getInitializer().size() == 1 && loop.getInitializer().getFirst() == variable;

            return false;
        }

        private boolean followedByComma(int offset) {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
            return offset < source.length() && source.charAt(offset) == ',';
        }

        private int startOf(Tree tree) {
            return Math.toIntExact(positions.getStartPosition(unit, tree));
        }

        private int endOf(Tree tree) {
            return Math.toIntExact(positions.getEndPosition(unit, tree));
        }

        private static String withoutWhitespace(String text) {
            var result = new StringBuilder(text.length());
            text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(result::appendCodePoint);
            return result.toString();
        }

        private void inspect(StatementTree statement, boolean allowElseIf) {
            if (statement == null)
                return;

            if (statement instanceof BlockTree block) {
                if (block.getStatements().size() == 1 && isTerminal(block.getStatements().getFirst().getKind())) {
                    removeOptionalBraces(block);
                }
                return;
            }

            if (isTerminal(statement.getKind()) || (allowElseIf && statement.getKind() == Tree.Kind.IF))
                return;

            addRequiredBraces(statement);
        }

        private void removeOptionalBraces(BlockTree block) {
            int start = Math.toIntExact(positions.getStartPosition(unit, block));
            int end = Math.toIntExact(positions.getEndPosition(unit, block));
            if (start < 0 || end <= start || source.charAt(start) != '{' || source.charAt(end - 1) != '}')
                throw new IllegalStateException("javac returned unexpected source positions for a block");

            edits.add(closingBraceEdit(end - 1));
            edits.add(openingBraceEdit(start));
            changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(start)));
        }

        private void addRequiredBraces(StatementTree statement) {
            int start = Math.toIntExact(positions.getStartPosition(unit, statement));
            int end = Math.toIntExact(positions.getEndPosition(unit, statement));
            if (start < 0 || end <= start)
                throw new IllegalStateException("javac returned unexpected source positions for a statement");

            edits.add(new TextEdit(end, end, "\n}"));
            edits.add(new TextEdit(start, start, "{\n"));
            changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(start)));
        }

        private TextEdit openingBraceEdit(int brace) {
            int editStart = brace;
            while (editStart > 0 && isHorizontalWhitespace(source.charAt(editStart - 1))) {
                editStart--;
            }
            return new TextEdit(editStart, brace + 1, "");
        }

        private TextEdit closingBraceEdit(int brace) {
            int lineStart = brace;
            while (lineStart > 0 && isHorizontalWhitespace(source.charAt(lineStart - 1))) {
                lineStart--;
            }

            boolean braceIsFirstTokenOnLine = lineStart == 0
                || source.charAt(lineStart - 1) == '\n'
                || source.charAt(lineStart - 1) == '\r';
            if (!braceIsFirstTokenOnLine) {
                int editStart = brace;
                while (editStart > 0 && isHorizontalWhitespace(source.charAt(editStart - 1))) {
                    editStart--;
                }
                return new TextEdit(editStart, brace + 1, "");
            }

            int editEnd = brace + 1;
            while (editEnd < source.length() && isHorizontalWhitespace(source.charAt(editEnd))) {
                editEnd++;
            }

            if (editEnd >= source.length()
                || (source.charAt(editEnd) != '\r' && source.charAt(editEnd) != '\n'))
                return new TextEdit(brace, editEnd, "");

            if (editEnd < source.length() && source.charAt(editEnd) == '\r') {
                editEnd++;
            }
            if (editEnd < source.length() && source.charAt(editEnd) == '\n') {
                editEnd++;
            }

            return new TextEdit(lineStart, editEnd, "");
        }

        private static boolean isHorizontalWhitespace(char character) {
            return character == ' ' || character == '\t';
        }

        private static boolean isTerminal(Tree.Kind kind) {
            return kind == Tree.Kind.RETURN
                || kind == Tree.Kind.THROW
                || kind == Tree.Kind.BREAK
                || kind == Tree.Kind.CONTINUE
                || kind == Tree.Kind.YIELD;
        }

        private static final class IdentifierUsageScanner extends TreeScanner<Void, Void> {
            private final String name;
            private boolean found;

            private IdentifierUsageScanner(String name) {
                this.name = name;
            }

            @Override
            public Void visitIdentifier(IdentifierTree tree, Void unused) {
                if (tree.getName().contentEquals(name)) {
                    found = true;
                }
                return null;
            }
        }
    }

    private record TextRange(int start, int end) {
    }
}
