import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.signature.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Standalone observation of signature-help costs on Railroad's real captured Java sources. */
public final class SignatureSnapshotProfile {
    private SignatureSnapshotProfile() {
    }

    public static void main(String[] arguments) throws Exception {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
        Path root = Path.of(arguments[0]);
        String classpath = String.join("\n", System.getProperty("java.class.path").split(File.pathSeparator));
        Map<String, String> settings = new HashMap<>();
        settings.put("module.:.classpath", classpath);
        settings.put("module.:.output", root.resolve("build/classes/java/main").toString());
        settings.put("module.:.testOutput", root.resolve("build/classes/java/test").toString());
        settings.put("module.:.jdkHome", System.getProperty("java.home"));
        DocumentStore store = new DefaultDocumentStore("profile",
            new WorkspaceConfigurationSnapshot(0, Set.of(":"), settings));
        int sourceCount = 0;
        long characters = 0;
        try (var paths = Files.walk(root.resolve("src/main/java"))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String text = Files.readString(file);
                characters += text.length();
                sourceCount++;
                store.publishBacking(new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(file),
                    DocumentVersion.initial(), "java", text, StandardCharsets.UTF_8));
            }
        }
        String text = "package profile; import dev.railroadide.railroad.ide.classparser.ClassStubParser; "
            + "import java.nio.file.Path; class SnapshotProbe { void run() { "
            + "ClassStubParser.parse(Path.of(\"Example.class\")); } }";
        TextDocumentSnapshot probe = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(root.resolve("profile/SnapshotProbe.java")), DocumentVersion.initial(),
            "java", text, StandardCharsets.UTF_8);
        store.publishBacking(probe);
        WorkspaceSnapshot workspace = store.capture();
        long start = System.nanoTime();
        var context = JavaLanguageSupport.analysisContextProvider().index(workspace);
        System.out.println("PROFILE sources=" + sourceCount + " chars=" + characters
            + " context_ms=" + elapsedMillis(start));
        for (int iteration = 0; iteration < 3; iteration++) {
            start = System.nanoTime();
            try (JavaSignatureSources staged = JavaSignatureSources.create(context, null)) {
                System.out.println("PROFILE staging=" + iteration + " create_ms=" + elapsedMillis(start));
            }
            System.out.println("PROFILE staging=" + iteration + " with_cleanup_ms=" + elapsedMillis(start));
        }
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(new String[0]);
        int caret = text.indexOf("parse(") + "parse(".length();
        for (int iteration = 0; iteration < 6; iteration++) {
            if (iteration > 2) {
                text += " ";
                store.publishOverlay(new TextDocumentSnapshot(probe.id(), probe.uri(), new DocumentVersion(iteration),
                    "java", text, StandardCharsets.UTF_8));
                workspace = store.capture();
            }
            start = System.nanoTime();
            SignatureHelp help = provider.compute(workspace, probe.id(), caret);
            long duration = elapsedMillis(start);
            if (help == null || !help.methodName().equals("parse"))
                throw new AssertionError("No captured dependency signature");
            System.out.println("PROFILE request=" + iteration + " changed=" + (iteration > 2)
                + " total_ms=" + duration + " owner=" + help.ownerQualified());
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
