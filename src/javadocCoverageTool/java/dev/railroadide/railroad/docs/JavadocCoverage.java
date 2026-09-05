package dev.railroadide.railroad.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Generates a source-only coverage report using the project's JDK parser. */
public final class JavadocCoverage {
    private JavadocCoverage() {
    }

    /**
     * Generates an HTML report and a machine-readable violation count.
     *
     * @param args project root, UTF-8 source-file manifest, and output directory
     * @throws IOException if a source or report cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3)
            throw new IllegalArgumentException("Expected: <project-root> <source-manifest> <report-directory>");
        var root = Path.of(args[0]).toAbsolutePath().normalize();
        var sources = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8).stream()
            .filter(line -> !line.isBlank()).map(Path::of).toList();
        var output = Path.of(args[2]);
        // Never leave a previous successful report behind if parsing fails.
        Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("index.html"));
        Files.deleteIfExists(output.resolve("violations.txt"));
        var report = analyze(root, sources);
        Files.writeString(output.resolve("index.html"), render(report), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("violations.txt"), Long.toString(report.incomplete()), StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT,
            "Javadoc coverage: %d/%d complete (%s). %d incomplete declarations.%nReport: %s%n",
            report.complete(), report.total(), percentage(report.complete(), report.total()), report.incomplete(),
            output.resolve("index.html").toUri());
    }

    public static Report analyze(Path root, List<Path> sources) throws IOException {
        var classes = new ArrayList<TypeCoverage>();
        if (sources.isEmpty())
            return new Report(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("Javadoc coverage requires a JDK, not a JRE.");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                List.of("-proc:none", "--release", "25"), null,
                manager.getJavaFileObjectsFromPaths(sources.stream().distinct().sorted().toList()));
            var docs = DocTrees.instance(task);
            // Deliberately do not attribute or compile: no dependencies or generated members are needed.
            for (var unit : task.parse()) {
                for (var declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type) {
                        collectType(new TreePath(new TreePath(unit), type), "", false, root, docs, classes);
                    }
                }
            }
        }
        var errors = diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(Object::toString).collect(Collectors.joining(System.lineSeparator()));
        if (!errors.isEmpty())
            throw new IOException(
                "Cannot produce reliable Javadoc coverage because Java sources could not be parsed:\n" + errors);
        classes.sort(Comparator.comparing(TypeCoverage::packageName).thenComparing(TypeCoverage::name));
        return new Report(List.copyOf(classes));
    }

    private static void collectType(
        TreePath path,
        String enclosingName,
        boolean implicitPublic,
        Path root,
        DocTrees docs,
        List<TypeCoverage> classes
    ) {
        var type = (ClassTree) path.getLeaf();
        if (!isPublic(type.getModifiers().getFlags(), implicitPublic))
            return;
        var unit = path.getCompilationUnit();
        String packageName = unit.getPackageName() == null ? "(default package)" : unit.getPackageName().toString();
        String name = enclosingName.isEmpty()
            ? type.getSimpleName().toString()
            : enclosingName + "." + type.getSimpleName();
        var entries = new ArrayList<Entry>();
        var typeParameters = new ArrayList<String>();
        type.getTypeParameters().forEach(parameter -> typeParameters.add("<" + parameter.getName() + ">"));
        // Record components are represented by non-static private fields in the parse tree.
        if (type.getKind() == Tree.Kind.RECORD) {
            type.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
                .filter(field -> !field.getModifiers().getFlags().contains(Modifier.STATIC))
                .forEach(field -> typeParameters.add(field.getName().toString()));
        }
        entries.add(entry(path, "Type", name, typeParameters, false, root, docs));
        boolean isInterface = type.getKind() == Tree.Kind.INTERFACE || type.getKind() == Tree.Kind.ANNOTATION_TYPE;
        for (var member : type.getMembers()) {
            var memberPath = new TreePath(path, member);
            switch (member) {
                case ClassTree classTree -> collectType(memberPath, name, isInterface, root, docs, classes);
                case MethodTree method when isPublic(method.getModifiers().getFlags(), isInterface) -> {
                    var parameters = new ArrayList<String>();
                    method.getTypeParameters().forEach(parameter -> parameters.add("<" + parameter.getName() + ">"));
                    method.getParameters().forEach(parameter -> parameters.add(parameter.getName().toString()));
                    boolean constructor = method.getReturnType() == null;
                    String methodName = constructor ? type.getSimpleName().toString() : method.getName().toString();
                    String signature = methodName + "(" + method.getParameters().stream()
                        .map(parameter -> parameter.getType() + " " + parameter.getName())
                        .collect(Collectors.joining(", "))
                        + ")";
                    if (!constructor) {
                        signature += " : " + method.getReturnType();
                    }
                    entries.add(entry(memberPath, constructor ? "Constructor" : "Method", signature, parameters,
                        !constructor && !method.getReturnType().toString().equals("void"), root, docs));
                }
                case VariableTree field -> {
                    var flags = field.getModifiers().getFlags();
                    if (isPublic(flags, isInterface)
                        && (isInterface || (flags.contains(Modifier.STATIC) && flags.contains(Modifier.FINAL)))) {
                        entries
                            .add(entry(memberPath, "Constant", field.getName().toString(), List.of(), false, root,
                                docs));
                    }
                }
                default -> {
                }
            }
        }
        // Type summary first, then constants, constructors, and methods, with overloads sorted by signature.
        entries.subList(1, entries.size()).sort(Comparator.comparing(Entry::kind).thenComparing(Entry::name));
        classes.add(new TypeCoverage(packageName, name, List.copyOf(entries)));
    }

    private static boolean isPublic(Set<Modifier> flags, boolean implicitPublic) {
        return flags.contains(Modifier.PUBLIC)
            || (implicitPublic && !flags.contains(Modifier.PRIVATE) && !flags.contains(Modifier.PROTECTED));
    }

    private static Entry entry(
        TreePath path,
        String kind,
        String name,
        List<String> parameters,
        boolean needsReturn,
        Path root,
        DocTrees docs
    ) {
        var unit = path.getCompilationUnit();
        var file = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
        String source = file.startsWith(root) ? root.relativize(file).toString().replace('\\', '/') : file.toString();
        long position = docs.getSourcePositions().getStartPosition(unit, path.getLeaf());
        long line = position < 0 ? 1 : unit.getLineMap().getLineNumber(position);
        var comment = docs.getDocCommentTree(path);
        return new Entry(kind, name, source, line, issues(comment, parameters, needsReturn));
    }

    private static List<String> issues(DocCommentTree comment, List<String> parameters, boolean needsReturn) {
        var issues = new ArrayList<String>();
        var documentedParameters = new HashSet<String>();
        boolean[] documentedReturn = {false};
        if (comment == null) {
            issues.add("Missing Javadoc");
        } else {
            if (!hasDescription(comment.getFullBody())) {
                issues.add("Missing Javadoc description");
            }
            new DocTreeScanner<Void, Void>() {
                @Override
                public Void visitParam(ParamTree tag, Void unused) {
                    if (hasDescription(tag.getDescription())) {
                        documentedParameters
                            .add(tag.isTypeParameter() ? "<" + tag.getName() + ">" : tag.getName().toString());
                    }
                    return super.visitParam(tag, unused);
                }

                @Override
                public Void visitReturn(ReturnTree tag, Void unused) {
                    documentedReturn[0] |= hasDescription(tag.getDescription());
                    return super.visitReturn(tag, unused);
                }

                @Override
                public Void visitErroneous(ErroneousTree tag, Void unused) {
                    issues.add("Malformed Javadoc: " + tag.getDiagnostic().getMessage(Locale.ROOT));
                    return null;
                }

                @Override
                public Void visitInheritDoc(InheritDocTree tag, Void unused) {
                    issues.add("Inherited documentation is not verified; document this declaration explicitly");
                    return null;
                }
            }.scan(comment, null);
        }
        for (String parameter : parameters) {
            if (!documentedParameters.contains(parameter)) {
                issues.add("Missing or empty @param " + parameter);
            }
        }
        if (needsReturn && !documentedReturn[0]) {
            issues.add("Missing or empty @return");
        }
        return issues.stream().distinct().toList();
    }

    private static boolean hasDescription(List<? extends DocTree> trees) {
        return trees.stream().anyMatch(tree -> switch (tree.getKind()) {
            case START_ELEMENT, END_ELEMENT, COMMENT, INHERIT_DOC, ERRONEOUS -> false;
            case CODE, LITERAL -> !((LiteralTree) tree).getBody().getBody().isBlank();
            case RETURN -> hasDescription(((ReturnTree) tree).getDescription());
            default -> !tree.toString().isBlank();
        });
    }

    public static String render(Report report) throws IOException {
        var configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setClassForTemplateLoading(JavadocCoverage.class, "");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setLocale(Locale.ROOT);
        configuration.setNumberFormat("computer");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setAutoEscapingPolicy(Configuration.FORCE_AUTO_ESCAPING_POLICY);
        var template = configuration.getTemplate("report.ftlh");
        var output = new StringWriter();
        try {
            template.process(reportModel(report), output);
        } catch (TemplateException exception) {
            throw new IOException("Could not render the Javadoc coverage report template", exception);
        }
        return output.toString();
    }

    private static Map<String, Object> reportModel(Report report) {
        var packages = new TreeMap<String, List<TypeCoverage>>();
        report.classes()
            .forEach(type -> packages.computeIfAbsent(type.packageName(), unused -> new ArrayList<>()).add(type));
        var packageModels = new ArrayList<Map<String, Object>>();
        for (var pkg : packages.entrySet()) {
            var types = new ArrayList<Map<String, Object>>();
            for (var type : pkg.getValue()) {
                String qualifiedName = type.packageName() + "." + type.name();
                var entries = type.entries().stream().map(entry -> Map.<String, Object>of(
                    "id", id(qualifiedName + "#" + entry.kind() + ":" + entry.name()),
                    "search", (qualifiedName + " " + entry.name()).toLowerCase(Locale.ROOT),
                    "kind", entry.kind(),
                    "name", entry.name(),
                    "complete", entry.complete(),
                    "issues", entry.issues(),
                    "source", entry.source(),
                    "line", entry.line())).toList();
                types.add(Map.of("id", id(qualifiedName), "name", type.name(),
                    "coverage", coverageModel(type.entries()), "entries", entries));
            }
            var entries = pkg.getValue().stream().flatMap(type -> type.entries().stream()).toList();
            packageModels.add(Map.of("id", id(pkg.getKey()), "name", pkg.getKey(),
                "coverage", coverageModel(entries), "types", types));
        }
        return Map.of("percentage", percentage(report.complete(), report.total()),
            "complete", report.complete(), "total", report.total(), "incomplete", report.incomplete(),
            "typeCount", report.classes().size(), "packages", packageModels);
    }

    private static Map<String, Object> coverageModel(List<Entry> entries) {
        long complete = entries.stream().filter(Entry::complete).count();
        return Map.of("complete", complete, "total", entries.size(), "percentage",
            percentage(complete, entries.size()));
    }

    private static String percentage(long complete, long total) {
        return total == 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", 100.0 * complete / total);
    }

    private static String id(String value) {
        return "api-" + Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public record Entry(String kind, String name, String source, long line, List<String> issues) {
        public boolean complete() {
            return issues.isEmpty();
        }
    }

    public record TypeCoverage(String packageName, String name, List<Entry> entries) {
    }

    public record Report(List<TypeCoverage> classes) {
        public long total() {
            return classes.stream().mapToLong(type -> type.entries().size()).sum();
        }

        public long complete() {
            return classes.stream().flatMap(type -> type.entries().stream()).filter(Entry::complete).count();
        }

        public long incomplete() {
            return total() - complete();
        }
    }
}
