package dev.railroadide.railroad.ide.sst.javac;

import com.sun.source.util.JavacTask;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.impl.java.JavaParserTestSupport;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSstServices;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class OpenJdkJavacChecks implements AutoCloseable {
    private static final Duration CASE_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern REFERENCE_DIAGNOSTIC = Pattern.compile(
        "(.+?):(\\d+):(\\d+):\\s+(compiler\\.err\\.[\\w.]+)(?::.*)?");
    private static final Pattern REFERENCE_SUMMARY = Pattern.compile("\\d+ (?:error|warning)s?");
    private static final Set<String> SYNTAX_CODES = Set.of("JAVA_ERROR_NODE", "JAVA_MISSING_TOKEN");
    private static final Set<String> SELECTOR_CODES = Set.of("SEM_UNRESOLVED_MEMBER", "SEM_UNRESOLVED_CALL",
        "SEM_INACCESSIBLE_MEMBER", "SEM_INACCESSIBLE_CALL");
    private static final Map<String, Set<String>> SEMANTIC_CODES = Map.ofEntries(
        Map.entry("compiler.err.already.defined", Set.of("SEM_DUPLICATE_DECLARATION")),
        Map.entry("compiler.err.duplicate.class", Set.of("SEM_DUPLICATE_DECLARATION")),
        Map.entry("compiler.err.cant.resolve", Set.of("SEM_UNRESOLVED_NAME", "SEM_UNRESOLVED_TYPE")),
        Map.entry("compiler.err.cant.resolve.location",
            Set.of("SEM_UNRESOLVED_NAME", "SEM_UNRESOLVED_TYPE", "SEM_UNRESOLVED_MEMBER")),
        Map.entry("compiler.err.cant.resolve.location.args", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.cant.resolve.args", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.ref.ambiguous", Set.of("SEM_AMBIGUOUS_NAME", "SEM_AMBIGUOUS_IMPORT")),
        Map.entry("compiler.err.report.access", Set.of("SEM_INACCESSIBLE_TYPE", "SEM_INACCESSIBLE_MEMBER",
            "SEM_INACCESSIBLE_CALL")),
        Map.entry("compiler.err.class.public.should.be.in.file", Set.of("SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE")),
        Map.entry("compiler.err.cyclic.inheritance", Set.of("SEM_INVALID_INHERITANCE")),
        Map.entry("compiler.err.does.not.override.abstract", Set.of("SEM_MISSING_IMPLEMENTATION")),
        Map.entry("compiler.err.missing.ret.stmt", Set.of("SEM_MISSING_RETURN")),
        Map.entry("compiler.err.var.might.not.have.been.initialized",
            Set.of("SEM_UNASSIGNED_VARIABLE", "SEM_UNINITIALIZED_FINAL_FIELD")),
        Map.entry("compiler.err.unreported.exception.need.to.catch.or.throw", Set.of("SEM_UNCAUGHT_CHECKED_EXCEPTION")),
        Map.entry("compiler.err.unreachable.stmt", Set.of("SEM_UNREACHABLE_CODE")),
        Map.entry("compiler.err.mod.not.allowed.here", Set.of("SEM_ILLEGAL_MODIFIER")));

    private final WorkspaceConfigurationSnapshot configuration;
    private final JavaCompiler compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(),
        "A JDK is required for the javac syntax oracle");
    private final JavaSstServices services = new JavaSstServices();

    public OpenJdkJavacChecks(Path checkout) {
        configuration = new WorkspaceConfigurationSnapshot(0, Set.of("javac-test"),
            Map.of("module.javac-test.sources", checkout.resolve("test").toString()));
    }

    public Result check(Path checkout, OpenJdkJavacSuite.TestCase test) throws IOException {
        OpenJdkCompilePlan plan = OpenJdkCompilePlan.read(checkout, test);
        var mismatches = new ArrayList<String>();
        var unsupported = new ArrayList<String>();
        if (plan.unsupported() != null) {
            unsupported.add(plan.unsupported());
        }

        Deadline deadline = Deadline.after(CASE_TIMEOUT);
        var previous = new LinkedHashMap<Path, TextDocumentSnapshot>();
        int syntaxFiles = 0;
        int actions = 0;
        for (var action : plan.actions()) {
            String label = "Action " + (actions + 1) + " (@" + action.name() + ")";
            var sources = new LinkedHashMap<Path, TextDocumentSnapshot>();
            try {
                for (Path file : action.sources()) {
                    sources.put(file, readSource(file, action.encoding()));
                }
            } catch (CharacterCodingException exception) {
                unsupported.add(label + " has source bytes that cannot be decoded as " + action.encoding());
                break;
            }
            actions++;
            var visible = new LinkedHashMap<>(previous);
            visible.putAll(sources);
            WorkspaceSnapshot workspace = workspace(test, visible, actions);
            Map<Path, ParsedSource> parsed = checkSyntax(sources, workspace, action.preview(), deadline, label,
                mismatches);
            syntaxFiles += parsed.size();
            List<ReportedError> errors = collectErrors(parsed);
            boolean syntaxRejected = parsed.values().stream().anyMatch(source -> !source.javacErrors().isEmpty());
            if (!syntaxRejected && errors.isEmpty()) {
                checkSemantics(workspace, parsed, deadline, errors);
            }
            if (!action.fails() && !errors.isEmpty()) {
                mismatches.add(label + " should succeed. SST reported " + errors);
            } else if (action.fails() && action.reference() == null && errors.isEmpty()) {
                mismatches.add(label + " should fail. SST reported no errors");
            }
            if (action.reference() != null) {
                checkReference(action, parsed, errors, label, mismatches, unsupported);
            }
            if (!action.fails()) {
                // Later actions get the sources of earlier successful javac actions, even when SST found a mismatch.
                previous.putAll(sources);
            }
        }
        if (plan.actions().isEmpty() && test.javaSource()) {
            try {
                Map<Path, TextDocumentSnapshot> source = Map.of(test.file(), readSource(test.file(), UTF_8));
                boolean preview = test.tags().stream().anyMatch(tag -> tag.name().equals("enablePreview"));
                checkSyntax(source, workspace(test, source, 0), preview, deadline, "On-disk source", mismatches);
                syntaxFiles++;
            } catch (CharacterCodingException exception) {
                unsupported.add("On-disk source cannot be decoded as UTF-8");
            }
        }

        Status status = !mismatches.isEmpty()
            ? Status.MISMATCH
            : unsupported.isEmpty() ? Status.PASS : Status.UNSUPPORTED;
        return new Result(test.id(), status, syntaxFiles, actions, List.copyOf(mismatches), List.copyOf(unsupported));
    }

    private Map<Path, ParsedSource> checkSyntax(
        Map<Path, TextDocumentSnapshot> sources,
        WorkspaceSnapshot workspace,
        boolean preview,
        Deadline deadline,
        String label,
        List<String> mismatches
    ) throws IOException {
        var parsed = new LinkedHashMap<Path, ParsedSource>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var inputs = sources.values().stream().map(SourceFile::new).toList();
        List<String> options = preview
            ? List.of("-proc:none", "--enable-preview", "--source",
                Integer.toString(Runtime.version().feature()))
            : List.of("-proc:none");
        try (var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, options, null, inputs);
            task.parse();
        }

        for (var entry : sources.entrySet()) {
            var request = new FeatureRequestContext(workspace, entry.getValue(), CancellationToken.none(), deadline);
            var result = JavaSyntaxParser.parseWithDiagnostics(request);
            var javacErrors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR && diagnostic.getSource() != null
                    && diagnostic.getSource().toUri().equals(entry.getKey().toUri()))
                .toList();
            var syntaxErrors = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == SyntaxDiagnostic.Severity.ERROR).toList();
            if (javacErrors.isEmpty() != syntaxErrors.isEmpty()) {
                mismatches.add(label + " " + entry.getKey().getFileName() + " syntax differs. javac: "
                    + javacErrors.stream().map(Diagnostic::getCode).toList() + ", SST: " + syntaxErrors);
            }
            if (!entry.getValue().text().equals(JavaParserTestSupport.syntaxText(result.tree()))) {
                mismatches.add(label + " " + entry.getKey().getFileName() + " failed the exact source round trip");
            }
            parsed.put(entry.getKey(), new ParsedSource(result, javacErrors));
        }
        if (diagnostics.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR && diagnostic.getSource() == null))
            throw new IOException("javac could not initialize its parser: " + diagnostics.getDiagnostics());
        return parsed;
    }

    private void checkSemantics(
        WorkspaceSnapshot workspace,
        Map<Path, ParsedSource> parsed,
        Deadline deadline,
        List<ReportedError> errors
    ) {
        for (var entry : parsed.entrySet()) {
            Path source = entry.getKey();
            var tree = entry.getValue().sst().tree();
            var request = new FeatureRequestContext(workspace, tree.documentSnapshot(), CancellationToken.none(),
                deadline);
            var symbols = services.capture(request);
            var model = JavaSemanticAnalyzer.analyzeFacts(tree, symbols, request::checkActive);
            var snapshot = tree.documentSnapshot();
            var ruleContext = new JavaRuleContext(snapshot.uri(), snapshot.text(), model, symbols,
                request::checkActive);
            var diagnostics = new ArrayList<>(model.diagnostics());
            for (var provider : JavaInspectionRegistries.coreRuleProviders()) {
                diagnostics.addAll(JavaInspectionRuleEngine.collectDiagnostics(provider, ruleContext));
            }
            request.checkActive();
            for (var diagnostic : diagnostics) {
                if (diagnostic.severity() == SemanticDiagnostic.Severity.ERROR) {
                    errors.add(new ReportedError(source, diagnostic.code(), diagnostic.startOffset(),
                        diagnostic.endOffset()));
                }
            }
        }
    }

    private static List<ReportedError> collectErrors(Map<Path, ParsedSource> parsed) {
        List<ReportedError> errors = new ArrayList<>();
        for (var entry : parsed.entrySet()) {
            for (var diagnostic : entry.getValue().sst().diagnostics()) {
                if (diagnostic.severity() == SyntaxDiagnostic.Severity.ERROR) {
                    errors.add(new ReportedError(entry.getKey(), diagnostic.code(), diagnostic.startOffset(),
                        diagnostic.endOffset()));
                }
            }
        }
        return errors;
    }

    private static void checkReference(
        OpenJdkCompilePlan.Action action,
        Map<Path, ParsedSource> parsed,
        List<ReportedError> errors,
        String label,
        List<String> mismatches,
        List<String> unsupported
    ) throws IOException {
        var unmatched = new ArrayList<>(errors);
        boolean hasOutput = false;
        for (String line : Files.readAllLines(action.reference(), UTF_8)) {
            String text = line.strip();
            if (text.isEmpty() || REFERENCE_SUMMARY.matcher(text).matches())
                continue;

            hasOutput = true;
            var diagnostic = REFERENCE_DIAGNOSTIC.matcher(text);
            if (!diagnostic.matches()) {
                unsupported.add(label + " has an unsupported reference line: " + text);
                continue;
            }
            String file = diagnostic.group(1).replace('\\', '/');
            List<Path> candidates = parsed.keySet().stream().filter(path -> path.endsWith(file)).toList();
            if (candidates.size() != 1) {
                unsupported.add(label + " cannot identify the reference source: " + text);
                continue;
            }
            Path source = candidates.getFirst();
            ParsedSource result = parsed.get(source);
            int row = Integer.parseInt(diagnostic.group(2));
            int column = Integer.parseInt(diagnostic.group(3));
            String code = diagnostic.group(4);
            var javacSyntaxError = result.javacErrors().stream().filter(error -> error.getCode().equals(code)
                && error.getLineNumber() == row && error.getColumnNumber() == column).findFirst();
            Set<String> expected = javacSyntaxError.isPresent() ? SYNTAX_CODES : semanticCodes(code, text);
            if (expected == null) {
                unsupported.add(label + " has no SST diagnostic mapping for " + text);
                continue;
            }

            var snapshot = result.sst().tree().documentSnapshot();
            int offset = javacSyntaxError.isPresent()
                ? Math.toIntExact(javacSyntaxError.get().getPosition())
                : referenceOffset(snapshot, row, column);
            var matching = unmatched.stream().filter(error -> error.source().equals(source)
                && expected.contains(error.code()) && coversReference(error, snapshot.text(), offset)).findFirst();
            if (matching.isPresent()) {
                unmatched.remove(matching.get());
            } else {
                mismatches
                    .add(label + " expected " + text + " (" + String.join(" or ", expected.stream().sorted().toList())
                        + "). SST reported " + errors);
            }
        }
        if (action.fails() && !hasOutput) {
            unsupported.add(label + " has no diagnostic details in " + action.reference().getFileName());
        }
    }

    private static Set<String> semanticCodes(String code, String referenceLine) {
        if ("compiler.err.cant.infer.local.var.type".equals(code)
            && referenceLine.endsWith("(compiler.misc.local.self.ref)"))
            return Set.of("SEM_SELF_REFERENCING_VAR");
        return SEMANTIC_CODES.get(code);
    }

    private static boolean coversReference(ReportedError error, String source, int offset) {
        if (error.covers(offset))
            return true;
        if (offset < 0 || offset >= source.length())
            return false;

        // javac points at the selector dot or call parenthesis; SST reports on the identifier.
        if (source.charAt(offset) == '.' && SELECTOR_CODES.contains(error.code())) {
            int selector = offset + 1;
            while (selector < source.length() && Character.isWhitespace(source.charAt(selector))) {
                selector++;
            }
            return error.covers(selector);
        }
        if (source.charAt(offset) == '(' && error.code().equals("SEM_UNCAUGHT_CHECKED_EXCEPTION")) {
            int name = offset - 1;
            while (name >= 0 && Character.isWhitespace(source.charAt(name))) {
                name--;
            }
            return error.covers(name);
        }
        return false;
    }

    private static int referenceOffset(TextDocumentSnapshot source, int row, int column) throws IOException {
        var lines = source.lineMap();
        if (row > 0 && row <= lines.lineCount() && column > 0) {
            int end = lines.lineEnd(row - 1);
            int currentColumn = 1;
            for (int offset = lines.lineStart(row - 1); offset <= end; offset++) {
                if (currentColumn == column)
                    return offset;
                if (offset < end) {
                    currentColumn += source.text().charAt(offset) == '\t' ? 8 - (currentColumn - 1) % 8 : 1;
                }
            }
        }
        throw new IOException("Reference location outside " + source.uri() + ":" + row + ":" + column);
    }

    private WorkspaceSnapshot workspace(
        OpenJdkJavacSuite.TestCase test,
        Map<Path, TextDocumentSnapshot> visible,
        int revision
    ) {
        Map<DocumentId, DocumentSnapshot> documents = visible.values().stream()
            .collect(Collectors.toMap(TextDocumentSnapshot::id, snapshot -> snapshot));
        return new WorkspaceSnapshot(test.id(), documents, configuration, revision);
    }

    private static TextDocumentSnapshot readSource(Path path, Charset charset) throws IOException {
        return new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(path), DocumentVersion.initial(),
            "java", Files.readString(path, charset), charset);
    }

    @Override
    public void close() {
        services.close();
    }

    public enum Status {
        PASS,
        MISMATCH,
        UNSUPPORTED,
        ERROR
    }

    public record Result(
        String id,
        Status status,
        int syntaxFiles,
        int compileActions,
        List<String> findings,
        List<String> unsupported
    ) {
    }

    private record ParsedSource(
        JavaSyntaxParser.ParseResult sst,
        List<Diagnostic<? extends JavaFileObject>> javacErrors
    ) {
    }

    private record ReportedError(Path source, String code, int start, int end) {
        public boolean covers(int position) {
            return start == end ? position == start : position >= start && position < end;
        }

        @Override
        public String toString() {
            return source.getFileName() + "@" + start + ".." + end + " " + code;
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final TextDocumentSnapshot snapshot;

        private SourceFile(TextDocumentSnapshot snapshot) {
            super(snapshot.uri().filePath().orElseThrow().toUri(), Kind.SOURCE);
            this.snapshot = snapshot;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return snapshot.text();
        }
    }
}
