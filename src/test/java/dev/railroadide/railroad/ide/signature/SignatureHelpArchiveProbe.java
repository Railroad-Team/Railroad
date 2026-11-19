package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Isolates JDT's retained archive handles so temporary JAR fixtures can be removed on Windows. */
public final class SignatureHelpArchiveProbe {
    private SignatureHelpArchiveProbe() {
    }

    public static void verify(
        Path directory,
        Path jdk,
        List<Path> classpath,
        String source,
        int caret,
        String owner,
        String method,
        List<String> parameters
    ) throws Exception {
        Path arguments = Files.createTempFile(directory, "signature-", ".args");
        Path output = Files.createTempFile(directory, "signature-", ".log");
        List<String> command = new ArrayList<>(List.of("-Xmx512m", "-cp", System.getProperty("java.class.path"),
            SignatureHelpArchiveProbe.class.getName(), jdk.toString(),
            String.join("\n", classpath.stream().map(Path::toString).toList()), source,
            Integer.toString(caret), owner, method, parameters.toString()));
        Files.writeString(arguments, command.stream().map(value -> "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"")
            .collect(Collectors.joining("\n")));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "@" + arguments).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Signature probe timed out");
            assertEquals(0, process.exitValue(), () -> {
                try {
                    return Files.readString(output);
                } catch (Exception exception) {
                    return exception.toString();
                }
            });
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    public static void main(String[] arguments) {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
        var store = new DefaultDocumentStore("signature-probe", new WorkspaceConfigurationSnapshot(0, Set.of(":"),
            Map.of("module.:.jdkHome", arguments[0], "module.:.classpath", arguments[1])));
        var request = new TextDocumentSnapshot(DocumentId.create(), DocumentUri.virtual("test", "/Example.java"),
            DocumentVersion.initial(), "java", arguments[2], StandardCharsets.UTF_8);
        store.publishBacking(request);
        SignatureHelp help = new JdtJavaSignatureHelpProvider(new String[0]).compute(store.capture(), request.id(),
            Integer.parseInt(arguments[3]));
        assertNotNull(help);
        assertEquals(arguments[4], help.ownerQualified());
        assertEquals(arguments[5], help.methodName());
        assertEquals(arguments[6],
            help.parameters().stream().map(SignatureHelp.ParameterInfo::type).toList().toString());
    }
}
