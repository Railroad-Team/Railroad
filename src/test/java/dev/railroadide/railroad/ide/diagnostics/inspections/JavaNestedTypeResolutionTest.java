package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Nested types are resolved through their qualifier, enclosing classes and supertypes rather than by simple name.
 */
public class JavaNestedTypeResolutionTest {
    @TempDir
    public static Path directory;
    private static JavaSymbolIndex index;

    @BeforeAll
    public static void compileLibrary() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("lib"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-proc:none", "-d",
            Files.createDirectory(directory.resolve("classes")).toString()));
        arguments.add(write(sources, "Payload.java", """
            package lib;
            public interface Payload {
                Type<?> type();
                record Type<T>(String id) {}
            }
            """));
        arguments.add(write(sources, "Holder.java", """
            package lib;
            public class Holder {
                public record Entry(int value) {}
            }
            """));
        arguments.add(write(sources, "Renderer.java", """
            package lib;
            public abstract class Renderer<S> {
                protected abstract void onRender(S state, int light);
            }
            """));
        arguments.add(write(sources, "Transactions.java", """
            package lib;
            interface Context { interface Close { void close(); } }
            interface Inner extends Context {}
            interface Outer extends Context {}
            public interface Transactions extends Inner, Outer {}
            """));
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output,
            arguments.toArray(String[]::new)), output::toString);
        index = new CompositeJavaSymbolIndex(List.of(
            JavaLibrarySymbolIndex.build(List.of(directory.resolve("classes"))),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
    }

    @Test
    public void inheritedNestedTypeResolvesBySimpleName() {
        assertClean("""
            package app;
            import lib.Payload;
            record Message(String text) implements Payload {
                static final Type<Message> TYPE = new Type<>("message");
                @Override
                public Type<Message> type() { return TYPE; }
            }
            """);
    }

    @Test
    public void qualifiedInheritedCallbackResolvesToItsDeclaringInterface() {
        assertClean("""
            package app;
            import lib.Transactions;
            class Participant implements Transactions.Close {
                static class Close {}
                @Override public void close() {}
                void notifyClosed(Transactions.Close listener) { listener.close(); }
            }
            """);
    }

    @Test
    public void qualifiedMemberDeclaredOnChildHidesInheritedInterface() {
        String source = """
            package app;
            import lib.Transactions;
            interface Child extends Transactions { class Close {} }
            class Invalid implements Child.Close {}
            """;
        List<SemanticDiagnostic> errors = JavaInspectionTestSupport.runProvider(new CoreInheritanceInspection(),
            Path.of("app/Invalid.java"), source, index);
        assertEquals(List.of("Invalid inheritance: type cannot implement non-interface 'Close'"),
            errors.stream().filter(d -> d.code().equals("SEM_INVALID_INHERITANCE"))
                .map(SemanticDiagnostic::message).toList());
    }

    @Test
    public void inheritedNestedTypeShadowsAnEnclosingTypeOfTheSameName() {
        assertClean("""
            package app;
            import lib.Payload;
            class Recipe {
                static class Type {}
                record Display(String text) implements Payload {
                    static final Type<Display> TYPE = new Type<>("display");
                    @Override
                    public Type<Display> type() { return TYPE; }
                }
            }
            """);
    }

    @Test
    public void qualifiedNestedTypeArgumentMatchesASourceDeclaredBase() throws Exception {
        assertCleanInCompiledProject("renderer", "MillRenderer.java", """
            package app;
            class MillRenderer extends BaseRenderer<String, MillRenderer.MillState> {
                @Override
                protected void onRender(MillState state, int light) {}
                static class MillState extends State {}
            }
            """, Map.of("State.java", "package app; public class State {}", "BaseRenderer.java", """
            package app;
            public abstract class BaseRenderer<T, S extends State> {
                protected abstract void onRender(S state, int light);
            }
            """));
    }

    @Test
    public void inheritedImplementationWithQualifiedNestedParameterSatisfiesTheInterface() throws Exception {
        assertCleanInCompiledProject("conveyor", "FeederRenderer.java", """
            package app;
            class FeederRenderer extends FlapRenderer {}
            """, Map.of("Registry.java", """
            package app;
            public final class Registry {
                public record Context(int light) {}
                public interface Renderer { void render(Context context); }
            }
            """, "FlapRenderer.java", """
            package app;
            public abstract class FlapRenderer implements Registry.Renderer {
                @Override
                public void render(Registry.Context context) {}
            }
            """));
    }

    @Test
    public void lambdaTargetingANestedFunctionalInterfaceKeepsItsParameterType() throws Exception {
        assertCleanInCompiledProject("fields", "Base.java", """
            package app;
            public abstract class Base<T extends Base<T>> {
                protected int dirty;
                void register(Fields<T> fields) { fields.add(entity -> entity.dirty); }
            }
            """, Map.of("Field.java", """
            package app;
            public record Field(int value) {
                public interface Getter<B extends Base<B>> { int get(B entity); }
            }
            """, "Fields.java", """
            package app;
            public class Fields<B extends Base<B>> {
                public void add(Field.Getter<B> getter) {}
            }
            """));
    }

    @Test
    public void importedNestedRecordResolves() {
        assertClean("""
            package app;
            import lib.Holder.Entry;
            class Reader { int read(Entry entry) { return entry.value(); } }
            """);
    }

    @Test
    public void qualifiedNestedTypeArgumentMatchesTheImplementation() {
        assertClean("""
            package app;
            import lib.Renderer;
            class WindRenderer extends Renderer<WindRenderer.WindState> {
                @Override
                protected void onRender(WindState state, int light) {}
                static class WindState {}
            }
            """);
    }

    @Test
    public void qualifiedNestedTypeIsNotConfusedWithASiblingOfTheSameName() {
        assertClean("""
            package app;
            class Models {
                static final Settings.Builder DEFAULT = new Settings.Builder().scale(1).translation(2);
                static Builder write() { return new Builder("id").label("x"); }
                static class Builder {
                    Builder(String id) {}
                    Builder label(String label) { return this; }
                }
                static class Settings {
                    static class Builder {
                        Builder scale(int scale) { return this; }
                        Builder translation(int translation) { return this; }
                    }
                }
            }
            """);
    }

    /**
     * Mirrors a Gradle project whose compiled output is also on the classpath, so nested project types are known
     * under both their source and binary names.
     */
    private static void assertCleanInCompiledProject(
        String project,
        String fileName,
        String source,
        Map<String, String> siblings
    ) throws Exception {
        Path root = Files.createDirectories(directory.resolve(project).resolve("src"));
        Path app = Files.createDirectories(root.resolve("app"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-proc:none", "-d",
            Files.createDirectories(directory.resolve(project).resolve("classes")).toString()));
        for (Map.Entry<String, String> sibling : siblings.entrySet()) {
            arguments.add(write(app, sibling.getKey(), sibling.getValue()));
        }

        Path file = Path.of(write(app, fileName, source));
        arguments.add(file.toString());
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output,
            arguments.toArray(String[]::new)), output::toString);
        JavaSymbolIndex projectIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(root),
            JavaLibrarySymbolIndex.build(List.of(directory.resolve(project).resolve("classes"))), index));
        assertClean(source, file, projectIndex);
    }

    private static void assertClean(String source) {
        assertClean(source, Path.of("app/Source.java"), index);
    }

    private static void assertClean(String source, Path file, JavaSymbolIndex symbolIndex) {
        List<SemanticDiagnostic> diagnostics = JavaInspectionTestSupport.runProviders(List.of(
            new CoreCallResolutionInspection(), new CoreImportInspection(), new CoreInheritanceInspection(),
            new CoreTypeResolutionInspection(), new CoreMemberResolutionInspection()), file, source, symbolIndex);
        assertEquals(List.of(), diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR)
            .map(SemanticDiagnostic::message).toList());
    }

    private static String write(Path directory, String name, String content) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file.toString();
    }
}
