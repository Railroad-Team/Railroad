package dev.railroadide.railroad.ide.sst.javac;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
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
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        "(.+?):(\\d+):(\\d+):\\s+(compiler\\.err\\.[\\w.-]+)(?::.*)?");
    private static final Pattern REFERENCE_SUMMARY = Pattern.compile("\\d+ (?:error|warning)s?");
    private static final Pattern REFERENCE_WARNING = Pattern
        .compile("(?:.+?:\\d+:\\d+:|-)\\s+compiler\\.(?:warn|note)\\..*");
    private static final Pattern PROBLEM_DETAIL = Pattern
        .compile("compiler\\.err\\.prob\\.found\\.req: \\(compiler\\.misc\\.([\\w.-]+).*");
    /**
     * javac's default {@code -Xmaxerrs}. A reference with this many errors may omit later ones.
     */
    private static final int JAVAC_ERROR_LIMIT = 100;
    private static final Set<String> SYNTAX_CODES = Set.of("JAVA_ERROR_NODE", "JAVA_MISSING_TOKEN",
        "JAVA_LEXICAL_ERROR");
    private static final Set<String> SELECTOR_CODES = Set.of("SEM_UNRESOLVED_MEMBER", "SEM_UNRESOLVED_CALL",
        "SEM_INACCESSIBLE_MEMBER", "SEM_INACCESSIBLE_CALL");
    private static final Set<String> FINAL_ASSIGNMENT = Set.of("SEM_ILLEGAL_FINAL_ASSIGNMENT");
    private static final Set<String> INACCESSIBLE = Set.of("SEM_INACCESSIBLE_TYPE", "SEM_INACCESSIBLE_MEMBER",
        "SEM_INACCESSIBLE_CALL");
    private static final Map<String, Set<String>> SEMANTIC_CODES = Map.ofEntries(
        Map.entry("compiler.err.already.defined", Set.of("SEM_DUPLICATE_DECLARATION")),
        Map.entry("compiler.err.already.defined.in.clinit", Set.of("SEM_DUPLICATE_DECLARATION")),
        Map.entry("compiler.err.duplicate.class", Set.of("SEM_DUPLICATE_DECLARATION")),
        Map.entry("compiler.err.cant.resolve", Set.of("SEM_UNRESOLVED_NAME", "SEM_UNRESOLVED_TYPE")),
        Map.entry("compiler.err.cant.resolve.location",
            Set.of("SEM_UNRESOLVED_NAME", "SEM_UNRESOLVED_TYPE", "SEM_UNRESOLVED_MEMBER")),
        Map.entry("compiler.err.cant.resolve.location.args", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.cant.resolve.args", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.cant.apply.symbol", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.cant.apply.symbols", Set.of("SEM_UNRESOLVED_CALL")),
        Map.entry("compiler.err.doesnt.exist", Set.of("SEM_UNRESOLVED_IMPORT", "SEM_UNRESOLVED_TYPE")),
        Map.entry("compiler.err.ref.ambiguous", Set.of("SEM_AMBIGUOUS_NAME", "SEM_AMBIGUOUS_IMPORT")),
        Map.entry("compiler.err.report.access", INACCESSIBLE),
        Map.entry("compiler.err.not.def.public.cant.access", INACCESSIBLE),
        Map.entry("compiler.err.not.def.access.class.intf.cant.access", INACCESSIBLE),
        Map.entry("compiler.err.class.public.should.be.in.file", Set.of("SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE")),
        Map.entry("compiler.err.cyclic.inheritance", Set.of("SEM_INVALID_INHERITANCE")),
        Map.entry("compiler.err.does.not.override.abstract", Set.of("SEM_MISSING_IMPLEMENTATION")),
        Map.entry("compiler.err.override.meth", Set.of("SEM_INVALID_OVERRIDE")),
        Map.entry("compiler.err.override.static", Set.of("SEM_INVALID_OVERRIDE")),
        Map.entry("compiler.err.override.weaker.access", Set.of("SEM_INVALID_OVERRIDE")),
        Map.entry("compiler.err.override.incompatible.ret", Set.of("SEM_INVALID_OVERRIDE")),
        Map.entry("compiler.err.override.meth.doesnt.throw", Set.of("SEM_INVALID_OVERRIDE")),
        Map.entry("compiler.err.missing.ret.stmt", Set.of("SEM_MISSING_RETURN")),
        Map.entry("compiler.err.var.might.not.have.been.initialized",
            Set.of("SEM_UNASSIGNED_VARIABLE", "SEM_UNINITIALIZED_FINAL_FIELD")),
        Map.entry("compiler.err.cant.assign.val.to.var", FINAL_ASSIGNMENT),
        Map.entry("compiler.err.var.might.already.be.assigned", FINAL_ASSIGNMENT),
        Map.entry("compiler.err.var.might.be.assigned.in.loop", FINAL_ASSIGNMENT),
        Map.entry("compiler.err.final.parameter.may.not.be.assigned", FINAL_ASSIGNMENT),
        Map.entry("compiler.err.try.resource.may.not.be.assigned", FINAL_ASSIGNMENT),
        Map.entry("compiler.err.unreported.exception.need.to.catch.or.throw", Set.of("SEM_UNCAUGHT_CHECKED_EXCEPTION")),
        Map.entry("compiler.err.except.never.thrown.in.try", Set.of("SEM_UNREACHABLE_CATCH")),
        Map.entry("compiler.err.except.already.caught", Set.of("SEM_UNREACHABLE_CATCH")),
        Map.entry("compiler.err.unreachable.stmt", Set.of("SEM_UNREACHABLE_CODE")),
        Map.entry("compiler.err.break.outside.switch.loop", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.break.outside.switch.expression", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.cont.outside.loop", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.not.loop.label", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.undef.label", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.ret.outside.meth", Set.of("SEM_INVALID_CONTROL_FLOW")),
        Map.entry("compiler.err.mod.not.allowed.here", Set.of("SEM_ILLEGAL_MODIFIER")),
        Map.entry("compiler.err.illegal.combination.of.modifiers", Set.of("SEM_ILLEGAL_MODIFIER")));
    /**
     * The {@code compiler.misc} details of {@code compiler.err.prob.found.req} that SST reports. The others concern
     * inference and functional interfaces, which SST does not check yet.
     */
    private static final Map<String, Set<String>> PROBLEM_CODES = Map.of(
        "inconvertible.types", Set.of("SEM_INCOMPATIBLE_ASSIGNMENT"),
        "possible.loss.of.precision", Set.of("SEM_INCOMPATIBLE_ASSIGNMENT"),
        "unexpected.ret.val", Set.of("SEM_INVALID_CONTROL_FLOW"),
        "missing.ret.val", Set.of("SEM_INVALID_CONTROL_FLOW"));
    /**
     * javac errors from its flow analysis, which it skips for every class it reaches after any error.
     */
    private static final Set<String> JAVAC_FLOW_CODES = Set.of("compiler.err.var.might.not.have.been.initialized",
        "compiler.err.var.might.already.be.assigned", "compiler.err.var.might.be.assigned.in.loop",
        "compiler.err.var.not.initialized.in.default.constructor", "compiler.err.final.parameter.may.not.be.assigned",
        "compiler.err.try.resource.may.not.be.assigned", "compiler.err.unreported.exception.need.to.catch.or.throw",
        "compiler.err.unreported.exception.default.constructor", "compiler.err.unreported.exception.implicit.close",
        "compiler.err.unreachable.stmt", "compiler.err.missing.ret.stmt", "compiler.err.except.never.thrown.in.try",
        "compiler.err.except.already.caught", "compiler.err.initializer.must.be.able.to.complete.normally",
        "compiler.err.not.exhaustive", "compiler.err.not.exhaustive.statement");
    /**
     * SST errors that correspond to javac's flow analysis.
     */
    private static final Set<String> SST_FLOW_CODES = Set.of("SEM_MISSING_RETURN", "SEM_UNASSIGNED_VARIABLE",
        "SEM_UNINITIALIZED_FINAL_FIELD", "SEM_UNREACHABLE_CODE", "SEM_UNCAUGHT_CHECKED_EXCEPTION",
        "SEM_UNREACHABLE_CATCH", "SEM_ILLEGAL_FINAL_ASSIGNMENT");

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
        int skippedWarnings = 0;
        for (var action : plan.actions()) {
            String label = "Action " + (actions + 1) + " (@" + action.name() + ")";
            var sources = new LinkedHashMap<Path, TextDocumentSnapshot>();
            var implicit = new LinkedHashMap<Path, TextDocumentSnapshot>();
            try {
                for (Path file : action.sources()) {
                    sources.put(file, readSource(file, action.encoding()));
                }
                for (Path file : implicitSources(checkout, plan.sourcePath(), action, sources, previous.keySet())) {
                    implicit.put(file, readSource(file, action.encoding()));
                }
            } catch (CharacterCodingException exception) {
                unsupported.add(label + " has source bytes that cannot be decoded as " + action.encoding());
                break;
            }
            actions++;
            var visible = new LinkedHashMap<>(previous);
            visible.putAll(implicit);
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
                skippedWarnings += checkReference(action, parsed, errors, label, mismatches, unsupported);
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
        return new Result(test.id(), status, syntaxFiles, actions, skippedWarnings, List.copyOf(mismatches),
            List.copyOf(unsupported));
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

    /**
     * Finds the sources javac compiles implicitly for an action. jtreg passes the test directory and its libraries
     * as {@code -sourcepath}, so javac loads any type it cannot otherwise find from there. SST needs those files as
     * workspace documents to resolve the same names, but they are not checked themselves.
     */
    private List<Path> implicitSources(
        Path checkout,
        List<Path> sourcePath,
        OpenJdkCompilePlan.Action action,
        Map<Path, TextDocumentSnapshot> sources,
        Set<Path> known
    ) throws IOException {
        if (sourcePath.isEmpty())
            return List.of();

        var loaded = new LinkedHashSet<Path>();
        var options = new ArrayList<>(List.of("-proc:none"));
        if (action.preview()) {
            options.addAll(List.of("--enable-preview", "--source", Integer.toString(Runtime.version().feature())));
        }
        try (var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, action.encoding())) {
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourcePath);
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of());
            var inputs = sources.values().stream().map(SourceFile::new).toList();
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var task = (JavacTask) compiler.getTask(Writer.nullWriter(), fileManager, diagnostics, options, null,
                inputs);
            task.addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                    if (event.getKind() == TaskEvent.Kind.PARSE) {
                        loaded.add(Path.of(event.getSourceFile().toUri()).toAbsolutePath().normalize());
                    }
                }
            });
            task.parse();
            // Semantics are never checked after a syntax error, and javac can fail assertions attributing such trees.
            if (diagnostics.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR))
                return List.of();
            task.analyze();
        }

        Path testRoot = checkout.resolve("test").toAbsolutePath().normalize();
        return loaded.stream()
            .filter(path -> path.startsWith(testRoot) && !sources.containsKey(path) && !known.contains(path))
            .toList();
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

    /**
     * Matches the errors in javac's reference output with the errors SST reported, and returns how many warning and
     * note lines it skipped. SST only mirrors javac errors.
     */
    private static int checkReference(
        OpenJdkCompilePlan.Action action,
        Map<Path, ParsedSource> parsed,
        List<ReportedError> errors,
        String label,
        List<String> mismatches,
        List<String> unsupported
    ) throws IOException {
        var unmatched = new ArrayList<>(errors);
        var javacErrors = new ArrayList<JavacError>();
        boolean complete = true;
        boolean hasOutput = false;
        int warnings = 0;
        for (String line : Files.readAllLines(action.reference(), UTF_8)) {
            String text = line.strip();
            if (text.isEmpty() || REFERENCE_SUMMARY.matcher(text).matches())
                continue;
            if (REFERENCE_WARNING.matcher(text).matches()) {
                warnings++;
                continue;
            }

            hasOutput = true;
            var diagnostic = REFERENCE_DIAGNOSTIC.matcher(text);
            if (!diagnostic.matches()) {
                unsupported.add(label + " has an unsupported reference line: " + text);
                complete = false;
                continue;
            }
            String file = diagnostic.group(1).replace('\\', '/');
            List<Path> candidates = parsed.keySet().stream().filter(path -> path.endsWith(file)).toList();
            if (candidates.size() != 1) {
                unsupported.add(label + " cannot identify the reference source: " + text);
                complete = false;
                continue;
            }
            Path source = candidates.getFirst();
            ParsedSource result = parsed.get(source);
            var snapshot = result.sst().tree().documentSnapshot();
            int row = Integer.parseInt(diagnostic.group(2));
            int column = Integer.parseInt(diagnostic.group(3));
            String code = diagnostic.group(4);
            var javacSyntaxError = result.javacErrors().stream().filter(error -> error.getCode().equals(code)
                && error.getLineNumber() == row && error.getColumnNumber() == column).findFirst();
            int offset = javacSyntaxError.isPresent()
                ? Math.toIntExact(javacSyntaxError.get().getPosition())
                : referenceOffset(snapshot, row, column);
            javacErrors.add(new JavacError(source, offset, code));
            Set<String> expected = javacSyntaxError.isPresent() ? SYNTAX_CODES : semanticCodes(code, text);
            if (expected == null) {
                unsupported.add(label + " has no SST diagnostic mapping for " + text);
                complete = false;
                continue;
            }

            String expectation = text + " (" + String.join(" or ", expected.stream().sorted().toList()) + ")";
            var matching = unmatched.stream().filter(error -> error.source().equals(source)
                && expected.contains(error.code()) && coversReference(error, snapshot.text(), offset)).findFirst();
            var misplaced = matching.isPresent()
                ? matching
                : unmatched.stream()
                    .filter(error -> error.source().equals(source) && expected.contains(error.code())
                        && snapshot.lineMap().positionAt(error.start()).line() == row - 1)
                    .findFirst();
            misplaced.ifPresent(unmatched::remove);
            if (matching.isEmpty() && misplaced.isPresent()) {
                mismatches.add(label + " misplaced " + expectation + ". SST reported " + misplaced.get());
            } else if (matching.isEmpty()) {
                mismatches.add(label + " expected " + expectation + ". SST reported " + errors);
            }
        }
        if (action.fails() && !hasOutput) {
            unsupported.add(label + " has no diagnostic details in " + action.reference().getFileName());
        }
        if (action.fails() && complete && hasOutput && javacErrors.size() < JAVAC_ERROR_LIMIT) {
            var surplus = unmatched.stream().filter(error -> javacErrors.stream()
                .noneMatch(javac -> javac.source().equals(error.source())
                    && coversReference(error, parsed.get(error.source()).text(), javac.offset())))
                .filter(error -> !SST_FLOW_CODES.contains(error.code())
                    || javacCheckedFlow(error, javacErrors, parsed))
                .toList();
            if (!surplus.isEmpty()) {
                mismatches.add(label + " reported errors javac does not: " + surplus);
            }
        }
        return warnings;
    }

    /**
     * Reports whether javac ran flow analysis where SST reported a flow error. javac skips the flow analysis of every
     * class once it has reported any error, and it reports declaration errors before it analyzes any class. When all
     * its errors come from flow analysis, it analyzed the top-level classes in source order up to and including the
     * first class with an error.
     */
    private static boolean javacCheckedFlow(
        ReportedError error,
        List<JavacError> javacErrors,
        Map<Path, ParsedSource> parsed
    ) {
        if (!javacErrors.stream().allMatch(javac -> JAVAC_FLOW_CODES.contains(javac.code())))
            return false;

        var order = List.copyOf(parsed.keySet());
        var first = javacErrors.stream()
            .min(Comparator.comparingInt((JavacError javac) -> order.indexOf(javac.source()))
                .thenComparingInt(JavacError::offset))
            .orElseThrow();
        int errorFile = order.indexOf(error.source());
        int firstFile = order.indexOf(first.source());
        if (errorFile != firstFile)
            return errorFile < firstFile;

        return error.start() < parsed.get(first.source()).topLevelDeclarationAt(first.offset()).end();
    }

    private static Set<String> semanticCodes(String code, String referenceLine) {
        if ("compiler.err.cant.infer.local.var.type".equals(code)
            && referenceLine.endsWith("(compiler.misc.local.self.ref)"))
            return Set.of("SEM_SELF_REFERENCING_VAR");
        var problem = PROBLEM_DETAIL.matcher(referenceLine);
        if (problem.find())
            return PROBLEM_CODES.get(problem.group(1));
        return SEMANTIC_CODES.get(code);
    }

    private static boolean coversReference(ReportedError error, String source, int offset) {
        if (error.covers(offset))
            return true;
        if (offset < 0 || offset >= source.length())
            return false;

        // javac points at 'new' for a constructor that no overload accepts; SST reports on the created type.
        if (source.startsWith("new", offset) && error.code().equals("SEM_UNRESOLVED_CALL")) {
            int type = offset + "new".length();
            while (type < source.length() && Character.isWhitespace(source.charAt(type))) {
                type++;
            }
            return type > offset + "new".length() && error.covers(type);
        }
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

    /**
     * The outcome of one test.
     *
     * @param skippedWarnings the warning and note lines in reference output, which SST does not check
     */
    public record Result(
        String id,
        Status status,
        int syntaxFiles,
        int compileActions,
        int skippedWarnings,
        List<String> findings,
        List<String> unsupported
    ) {
    }

    private record ParsedSource(
        JavaSyntaxParser.ParseResult sst,
        List<Diagnostic<? extends JavaFileObject>> javacErrors
    ) {
        private String text() {
            return sst.tree().documentSnapshot().text();
        }

        private SyntaxNode topLevelDeclarationAt(int offset) {
            SyntaxNode root = sst.tree().root();
            return root.children().stream().filter(child -> child.start() <= offset && offset < child.end())
                .findFirst().orElse(root);
        }
    }

    private record JavacError(Path source, int offset, String code) {
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
