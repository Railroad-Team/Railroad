package dev.railroadide.railroad.ide.sst;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SstArchitectureTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/railroadide/railroad/ide/sst");
    private static final List<String> CORE = List.of("document/api", "request/api", "provider/api", "syntax/api",
        "semantic/api", "lexer");
    private static final List<String> FORBIDDEN = List.of("dev.railroadide.railroad.ide.sst.java.",
        "dev.railroadide.railroad.ide.sst.impl.java.", "dev.railroadide.railroad.ide.sst.ast.",
        "dev.railroadide.railroad.ide.sst.parser.",
        "dev.railroadide.railroad.ide.sst.project.", "dev.railroadide.railroad.ide.classparser.",
        "dev.railroadide.railroad.ide.ui.", "dev.railroadide.railroad.ide.language.",
        "dev.railroadide.railroad.plugin.spi.dto.",
        "dev.railroadide.railroad.plugin.spi.inspection.Java", "javax.tools.", "com.github.javaparser.",
        "org.eclipse.jdt.", "javafx.");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);

    @Test
    public void neutralCoreCannotDependOnJavaSupportOrEditorControls() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String directory : CORE) {
            inspect(SOURCE.resolve(directory), FORBIDDEN, violations);
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    public void independentProvidersUseOnlyPublicLanguageNeutralContracts() throws IOException {
        Path samples = SOURCE.resolve("examples");
        assertTrue(Files.isDirectory(samples), "Independent sample providers must remain present");
        List<String> forbidden = new ArrayList<>(FORBIDDEN);
        forbidden.add(".internal.");
        List<String> violations = new ArrayList<>();
        inspect(samples, forbidden, violations);
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    public void featureContractsCannotLeakJavaOrEditorDependencies() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String file : List.of("diagnostics/EditorDiagnostic.java", "diagnostics/DiagnosticsProvider.java",
            "diagnostics/DiagnosticSeverity.java", "diagnostics/DiagnosticOrigin.java",
            "diagnostics/DiagnosticTag.java",
            "completion/CompletionProvider.java", "completion/CompletionResult.java", "completion/CompletionItem.java",
            "signature/SignatureHelpProvider.java", "signature/SignatureHelp.java")) {
            inspect(SOURCE.getParent().resolve(file), FORBIDDEN, violations);
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static void inspect(Path directory, List<String> forbidden, List<String> violations) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = COMMENTS.matcher(Files.readString(path)).replaceAll("");
                for (String dependency : forbidden) {
                    if (source.contains(dependency)) {
                        violations.add(path + " depends on " + dependency);
                    }
                }
            }
        }
    }
}
