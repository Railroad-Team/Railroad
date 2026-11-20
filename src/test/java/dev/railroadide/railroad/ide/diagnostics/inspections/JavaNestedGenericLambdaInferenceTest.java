package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lambda parameters typed only through nested generic calls, as in DataFixerUpper's {@code RecordCodecBuilder}, must
 * receive the type the compiler infers so that member accesses on them resolve.
 */
public class JavaNestedGenericLambdaInferenceTest {
    private static final String API = """
        package dfu;

        import java.util.function.BiFunction;
        import java.util.function.Function;

        public final class Api {
            public interface K1 {}
            public interface App<F extends K1, A> {}
            public interface Applicative<F extends K1, Mu extends Applicative.Mu> {
                interface Mu extends K1 {}
            }

            public interface Codec<A> {
                MapCodec<A> fieldOf(String name);
            }

            public abstract static class MapCodec<A> {
                public <O> RecordCodecBuilder<O, A> forGetter(Function<O, A> getter) {
                    return null;
                }
            }

            public static final class P2<F extends K1, T1, T2> {
                public <R> App<F, R> apply(Applicative<F, ?> instance, BiFunction<T1, T2, R> function) {
                    return null;
                }
            }

            public static final class RecordCodecBuilder<O, F> implements App<RecordCodecBuilder.Mu<O>, F> {
                public static final class Mu<O> implements K1 {}

                public static final class Instance<O>
                    implements Applicative<RecordCodecBuilder.Mu<O>, Instance.Mu<O>> {
                    public static final class Mu<O> implements Applicative.Mu {}

                    public <T1, T2> P2<RecordCodecBuilder.Mu<O>, T1, T2> group(
                        App<RecordCodecBuilder.Mu<O>, T1> t1,
                        App<RecordCodecBuilder.Mu<O>, T2> t2) {
                        return null;
                    }
                }

                public static <O> Codec<O> create(Function<Instance<O>, ? extends App<Mu<O>, O>> builder) {
                    return null;
                }
            }
        }
        """;

    @TempDir
    public static Path directory;
    private static Path classes;
    private static JavaSymbolIndex index;

    @BeforeAll
    public static void compileCodecApi() throws Exception {
        Path source = Files.createDirectories(directory.resolve("dfu")).resolve("Api.java");
        Files.writeString(source, API);
        classes = Files.createDirectory(directory.resolve("classes"));
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "25",
            "-proc:none", "-d", classes.toString(), source.toString()), output::toString);
        // Only the API's class files are indexed: caller output cannot hide an inference failure.
        index = new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(List.of(classes)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
    }

    @Test
    public void recordCodecBuilderGetterParametersResolveToTheBuiltType() throws Exception {
        check("climateSettings", "specialEffects", List.of());
    }

    @Test
    public void recordCodecBuilderGetterParametersStillRejectMissingMembers() throws Exception {
        check("climateSettings", "missing", List.of("Cannot resolve member 'missing'"));
    }

    private static void check(String firstGetter, String secondGetter, List<String> expected) throws Exception {
        String source = """
            import dfu.Api.Codec;
            import dfu.Api.MapCodec;
            import dfu.Api.RecordCodecBuilder;

            class Biome {
                public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create(
                    i -> i.group(
                        Biome.ClimateSettings.CODEC.forGetter(b -> b.%s),
                        Effects.CODEC.fieldOf("effects").forGetter(b -> b.%s))
                    .apply(i, Biome::new));

                private final ClimateSettings climateSettings;
                private final Effects specialEffects;

                Biome(ClimateSettings climateSettings, Effects specialEffects) {
                    this.climateSettings = climateSettings;
                    this.specialEffects = specialEffects;
                }

                record ClimateSettings(boolean hasPrecipitation, float temperature) {
                    static MapCodec<ClimateSettings> CODEC;
                }

                record Effects(int fogColor) {
                    static Codec<Effects> CODEC;
                }
            }
            """.formatted(firstGetter, secondGetter);
        Path caller = directory.resolve("Biome.java");
        Files.writeString(caller, source);
        var output = new ByteArrayOutputStream();
        int exit = ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "25", "-proc:none",
            "-classpath", classes.toString(), "-d", directory.resolve("caller").toString(), caller.toString());
        assertEquals(expected.isEmpty(), exit == 0, () -> "javac: " + output);

        List<SemanticDiagnostic> diagnostics = JavaInspectionTestSupport.runProviders(
            List.of(new CoreMemberResolutionInspection()), caller, source, index);
        assertEquals(expected, diagnostics.stream().map(SemanticDiagnostic::message).toList());
    }
}
