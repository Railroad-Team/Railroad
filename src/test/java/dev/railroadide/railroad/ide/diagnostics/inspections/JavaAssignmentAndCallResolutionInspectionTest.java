package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.*;

class JavaAssignmentAndCallResolutionInspectionTest {
    @Test
    void assignmentRuleInfersGenericMethodReturnsFromArgumentsAndClassLiterals() {
        String source = """
            class Example {
                static final class Jdk {}
                static class Node {}
                static final class Symbol {}
                interface Observable<T> { T getValue(); }

                static <T> T lookup(Class<T> type) {
                    return null;
                }

                java.util.Optional<Symbol> localSymbol(String ignored) {
                    return java.util.Optional.empty();
                }

                <U extends Node> void consumeBounded(Observable<U> observable) {
                    Node node = observable.getValue();
                }

                void run() {
                    String text = java.util.Objects.requireNonNull("value");
                    Jdk jdk = lookup(Jdk.class);
                    Symbol symbol = java.util.Optional.of("value")
                        .flatMap(this::localSymbol)
                        .orElse(null);
                    String[] values = null;
                    String first = values[0];
                    int[] bounds = null;
                    int open = bounds[0];
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> inferredTypes = new ArrayList<>();
        context.traverse(node -> {
            if (Set.of("JAVA_CLASS_LITERAL_EXPRESSION", "JAVA_METHOD_INVOCATION_EXPRESSION").contains(node.kind().id())) {
                inferredTypes.add(node.kind().id() + "="
                    + context.inferredType(node).map(type -> type.displayName() + type).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", inferredTypes));
    }

    @Test
    void assignmentRuleUsesTargetTypeForStaticImportedFunctionalOverloads() {
        String source = """
            package sample;

            import static sample.Assertions.call;

            class Assertions {
                interface Executable { void run(); }
                interface ValueSupplier<T> { T get(); }

                static void call(Executable executable) {}
                static <T> T call(ValueSupplier<T> supplier) { return supplier.get(); }
            }

            class Example {
                static final class Result {}
                static Result create() { return new Result(); }

                Result result = call(() -> create());
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleSupportsJavaBoxingAndUnboxingConversions() {
        String source = """
            class Example {
                void run() {
                    Integer boxed = 1;
                    int unboxed = boxed;
                    long widened = boxed;
                    Object object = 2;
                    boolean flag = Boolean.TRUE;
                    Long boxedLong = 5000L;
                    float singlePrecision = 1.0f;
                    int shifted = (1 << 1) >>> 1;
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> binaryTypes = new ArrayList<>();
        context.traverse(node -> {
            if ("JAVA_BINARY_EXPRESSION".equals(node.kind().id())) {
                binaryTypes.add(source.substring(node.start(), node.end()) + "="
                    + context.inferredType(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", binaryTypes));
    }

    @Test
    void assignmentRuleTreatsJvmAndSourceNestedTypeNamesAsEquivalent() {
        String source = "class Example {}";
        JavaRuleContext context = new JavaRuleContext(
            Path.of("Example.java"), source, JavaSemanticAnalyzer.analyzeFacts(source));

        assertTrue(context.isAssignable(
            new dev.railroadide.railroad.ide.sst.semantic.api.Type.DeclaredType("example.Outer.Inner", List.of()),
            new dev.railroadide.railroad.ide.sst.semantic.api.Type.DeclaredType("example.Outer$Inner", List.of())));
    }

    @Test
    void assignmentRuleSpecializesGenericMethodsInheritedThroughBinarySupertypes() throws Exception {
        String source = """
            import javafx.beans.property.SimpleStringProperty;
            import javafx.beans.property.StringProperty;

            class Example {
                String fromDeclaredProperty(StringProperty property) {
                    return property.getValue();
                }

                String fromConcreteProperty() {
                    SimpleStringProperty property = new SimpleStringProperty("value");
                    return property.getValue();
                }
            }
            """;
        Path javafxJar = Path.of(
            javafx.beans.property.StringProperty.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            JavaLibrarySymbolIndex.build(List.of(javafxJar)),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), Path.of("Example.java"), source, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleAcceptsLambdasAndMethodReferencesForFunctionalInterfaces() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Objects;
            import java.util.function.Function;
            import java.util.function.Predicate;
            import java.util.function.Supplier;

            enum Kind { VALUE }
            class Example {
                Supplier<List<String>> supplier = ArrayList::new;
                Predicate<String> predicate = Objects::nonNull;
                Function<Kind, String> function = Enum::name;
                Function<String, String> lambda = value -> value.trim();
                String invalid = String::trim;
            }
            """);

        List<SemanticDiagnostic> incompatible = diagnostics.stream()
            .filter(diagnostic -> "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code()))
            .toList();
        assertEquals(1, incompatible.size(),
            () -> incompatible.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
        assertTrue(incompatible.getFirst().message().contains("java.lang.String"));
    }

    @Test
    void assignmentRuleInfersArrayCreationTypesFromTheCreatedTypeAndDimensions() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), """
            class Example {
                void run() {
                    String[] strings = new String[0];
                    byte[] bytes = new byte[4];
                    int[][] matrix = new int[2][3];
                    String[][] initialized = new String[][]{{"value"}};
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleInfersGenericReturnsFromFunctionalArguments() {
        String source = """
            import java.util.Arrays;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.stream.Collectors;

            class Example {
                static class Outer {
                    static class Builder {}
                    static Builder builder() { return new Builder(); }
                }

                void run(String[] source) {
                    String mapped = Optional.of(" value ").map(String::trim).orElse(null);
                    String lambdaMapped = Optional.of("value").map(value -> value.trim()).orElse(null);
                    String[] copied = Arrays.stream(source).map(String::trim).toArray(String[]::new);
                    List<String> collected = Arrays.stream(source).collect(Collectors.toList());
                    Map<String, Integer> values = Map.of("value", 1);
                    String key = values.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                    Outer.Builder builder = Outer.builder();
                }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        JavaRuleContext context = new JavaRuleContext(Path.of("Example.java"), source, model);
        List<String> inferredTypes = new ArrayList<>();
        context.traverse(node -> {
            if (Set.of("JAVA_METHOD_INVOCATION_EXPRESSION", "JAVA_METHOD_REFERENCE_EXPRESSION").contains(node.kind().id())) {
                inferredTypes.add(source.substring(node.start(), node.end()) + "="
                    + context.inferredType(node).map(Object::toString).orElse("<none>"));
            }
        });
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", inferredTypes));
    }

    @Test
    void callResolutionUsesFunctionalTargetTypesForPolyExpressionOverloads() {
        String source = """
            import java.util.Collection;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.Comparator;
            import java.util.List;
            import java.util.Map;
            import java.nio.file.Path;
            import java.util.function.BiConsumer;
            import java.util.function.Consumer;
            import java.util.function.Function;
            import java.util.function.Supplier;
            import java.util.function.UnaryOperator;

            class LambdaBase {
                static String namedThreadFactory(String name) { return name; }
            }

            class Example extends LambdaBase {
                static final class Row {
                    RuleDescriptor rule;
                    Box<String> severityOverride;
                }

                static final class Rule {
                    String id() { return "id"; }
                }

                record RuleDescriptor(String providerId, Rule rule) {}

                static final class Box<T> {
                    Box() {}
                    Box(Function<T, String> formatter) {}
                    T getValue() { return null; }
                }

                record Widget(String id) {}

                static class BaseWidget {}
                interface MutableWidget { void setValue(Object value); }
                static class GenericWidget<T extends BaseWidget & MutableWidget> {
                    T getComponent() { return null; }
                }
                interface Listener<T> {}
                static class Property<T> {
                    void removeListener(Listener<? super T> listener) {}
                }
                static class Reference<T> { T get() { return null; } }

                record FileResult(Path path) {}
                record ValidationEntry(Popup popup) {}
                static final class Popup { void hide() {} }
                enum Mode { DEFAULT }
                interface TextLength { int length(String text); }
                static class CommonPane { void common() {} }
                static final class HorizontalPane extends CommonPane {}
                static final class VerticalPane extends CommonPane {}
                static class ConfigurationData { String getName() { return "name"; } }
                static class Configuration<D extends ConfigurationData> {
                    D data() { return null; }
                }
                interface Module { List<String> getConfigurations(); }

                void items(Collection<String> values) {}
                void items(Supplier<Collection<String>> values) {}
                void listen(Consumer<String> listener) {}
                void listen(BiConsumer<String, String> listener) {}
                void transform(UnaryOperator<String> operator) {}
                void log(String format, Object... arguments) {}

                void flatten(List<? extends String> values) {
                    values.stream()
                        .flatMap(value -> java.util.stream.Stream.of(value.trim()))
                        .toList();
                }

                void nestedSourceFields(List<Row> rows) {
                    for (Row row : rows) {
                        row.rule.rule().id();
                        row.severityOverride.getValue().trim();
                    }
                }

                void nestedRecordBackingField(ValidationEntry entry) {
                    entry.popup.hide();
                }

                void comparatorFactory(ArrayList<FileResult> results) {
                    results.sort(Comparator.comparing(file -> file.path().toString()));
                    ArrayList<Map.Entry<Path, FileResult>> entries = new ArrayList<>();
                    entries.sort(Comparator.comparingLong(
                        (Map.Entry<Path, FileResult> entry) -> entry.getValue().path().toString().length()));
                    ArrayList<RuleDescriptor> descriptors = new ArrayList<>();
                    descriptors.sort(Comparator
                        .comparing(RuleDescriptor::providerId)
                        .thenComparing(rule -> rule.rule().id()));
                    Mode[] modes = Mode.values();
                }

                void contextualGenericLambdas(
                        List<Consumer<Row>> consumers,
                        Map<Widget, Row> rows,
                        Row row,
                        GenericWidget<?> genericWidget,
                        Property<Widget[]> property,
                        Reference<Listener<Widget[]>> listenerReference
                ) {
                    consumers.forEach(consumer -> consumer.accept(row));
                    for (Map.Entry<Widget, Row> entry : rows.entrySet())
                        entry.getKey().id().trim();
                    Box<Widget> box = new Box<>(widget -> widget.id().trim());
                    String[] versions = Arrays.stream(new int[] { 17, 21 })
                        .mapToObj(Integer::toString)
                        .toArray(String[]::new);
                    Comparator.<Widget, String>comparing(widget -> widget.id().trim());
                    genericWidget.getComponent().setValue("value");
                    property.removeListener(listenerReference.get());
                }

                Function<Widget, String> returnedLambda() {
                    return widget -> widget.id().trim();
                }

                void conditionalTargets(boolean horizontal) {
                    var pane = horizontal ? new HorizontalPane() : new VerticalPane();
                    pane.common();
                    TextLength length = horizontal ? null : text -> text.length();
                    namedThreadFactory("worker").trim();
                }

                void wildcardReceiverTypes(
                        Configuration<?> configuration,
                        Collection<? extends Module> modules
                ) {
                    configuration.data().getName().trim();
                    modules.stream()
                        .flatMap(module -> module.getConfigurations().stream())
                        .toList();
                }

                void laterDeclaredFields(List<Mode> modes, List<LaterRow> rows) {
                    for (Mode row : modes)
                        row.toString();
                    for (LaterRow row : rows) {
                        row.rule.rule().id();
                        row.severityOverride.getValue().trim();
                    }
                }

                void run() {
                    int clamped = Math.clamp(5, 0, 10);
                    String last = List.of("value").getLast();
                    items(List::of);
                    items(() -> List.of("value"));
                    items(List.of("value"));
                    listen((oldValue, newValue) -> newValue.trim());
                    transform(value -> value.trim());
                    List.of(" value ").stream()
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
                    List.of(Map.entry((CharSequence) "aa", 1), Map.entry((CharSequence) "b", 2))
                        .stream()
                        .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                        .toList();
                    log("value={}", 1);
                }

                static final class LaterRow {
                    RuleDescriptor rule;
                    Box<String> severityOverride;
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream()
                .map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionContextuallyTypesAssignedLambdasAndPrefersFieldsOverMethods() {
        String source = """
            import java.util.function.Consumer;

            class Example {
                Consumer<String> handler;

                void handler() {}

                void run() {
                    handler = event -> event.trim();
                    handler.accept("value");
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionSpecializesUnqualifiedInheritedGenericMethods() {
        String source = """
            import java.util.List;
            import java.util.function.BiConsumer;

            class Base<T> {
                T get() { return null; }
                void listen(BiConsumer<T, T> listener) {}
                <U> String describe(U value) {
                    return value.toString() + value.getClass().getName();
                }
            }

            class Child extends Base<String> {
                void run() {
                    get().trim();
                    listen((oldValue, newValue) -> newValue.trim());
                }
            }

            interface Named {
                String name();
            }

            class NamedValue<T extends Named> {
                String lower(T value) {
                    return value.name().toLowerCase();
                }
            }

            interface Left {
                void left();
            }

            interface Right {
                void right();
            }

            class Intersection<T extends Left & Right> {
                void use(T value) {
                    value.left();
                    value.right();
                }
            }

            enum Mode {
                VALUE;

                String lower() {
                    return name().toLowerCase();
                }
            }

            class Iteration {
                void run(List<String> values, String[] array) {
                    for (var value : values) {
                        value.trim();
                    }
                    for (var value : array) {
                        value.trim();
                    }
                    Mode.VALUE.name().toLowerCase();
                }
            }

            class Holder {
                enum NestedMode {
                    VALUE;

                    NestedMode() {
                        name().toLowerCase();
                    }
                }
            }

            class GenericBuilder<T, N> {
                static <T, N> GenericBuilder<T, N> builder() {
                    return new GenericBuilder<>();
                }

                GenericBuilder<T, N> use(BiConsumer<T, N> consumer) {
                    return this;
                }
            }

            class ValueNode {
                void setValue(String value) {}
            }

            class ExplicitTypeArguments {
                void run() {
                    GenericBuilder.<String, ValueNode>builder()
                        .use((value, node) -> node.setValue(value));
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreCallResolutionInspection(), Path.of("Example.java"), source,
            JavaJdkSymbolIndex.fromCurrentRuntime());

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void callResolutionResolvesExplicitConstructorInvocations() {
        String source = """
            import java.util.ArrayList;
            import java.util.function.Consumer;
            import java.util.function.Function;
            import java.util.function.Supplier;

            class Base {
                Base(String name, Function<String, Integer> length) {}
            }

            class Box<T> {
                Box(Consumer<T> consumer, Supplier<T> supplier) {}
            }

            class Child extends Base {
                Child() {
                    super("value", value -> value.length());
                }

                <U> void box(Supplier<U> supplier) {
                    new Box<>(value -> value.toString(), supplier);
                }
            }

            class Outer {
                static class Inner extends ArrayList<String> {
                    boolean addValue() {
                        return super.add("value");
                    }
                }
            }

            interface LexerContract {
                record LexError(String message, int offset, int line, int column) {
                    LexError(String message, int offset) {
                        this(message, offset, 0, 0);
                    }
                }
            }

            class LexerImplementation implements LexerContract {
                LexError error() {
                    LexError shortError = new LexError("message", 1);
                    return new LexError(shortError.message(), shortError.offset(), 2, 3);
                }
            }
            """;

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), source);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
                "SEM_UNRESOLVED_CALL".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void assignmentRuleFindsSupertypesOfNestedTypesDeclaredInIndexedSources(@TempDir Path sourceRoot) throws Exception {
        Path contractFile = sourceRoot.resolve("api/Contract.java");
        Path keyFile = sourceRoot.resolve("api/Key.java");
        Path contextFile = sourceRoot.resolve("api/Context.java");
        Path keysFile = sourceRoot.resolve("api/Keys.java");
        Path containerFile = sourceRoot.resolve("impl/Container.java");
        Path useFile = sourceRoot.resolve("usage/Use.java");
        Files.createDirectories(contractFile.getParent());
        Files.createDirectories(containerFile.getParent());
        Files.createDirectories(useFile.getParent());
        Files.writeString(contractFile, """
            package api;
            public interface Contract {}
            """);
        Files.writeString(keyFile, """
            package api;
            public final class Key<T> {}
            """);
        Files.writeString(contextFile, """
            package api;
            public interface Context {
                <T> T get(Key<T> key);
            }
            """);
        Files.writeString(keysFile, """
            package api;
            public final class Keys {
                public static final Key<String> NAME = new Key<>();
            }
            """);
        Files.writeString(containerFile, """
            package impl;
            import api.Contract;
            public class Container {
                public record Value() implements Contract {}
            }
            """);
        String useSource = """
            package usage;
            import api.Contract;
            import api.Context;
            import api.Keys;
            import impl.Container;
            class Use {
                Contract value = new Container.Value();
                String read(Context context) {
                    return context.get(Keys.NAME);
                }
            }
            """;
        Files.writeString(useFile, useSource);
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAssignmentInspection(), useFile, useSource, symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_INCOMPATIBLE_ASSIGNMENT".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

}
