package dev.railroadide.railroad.ide.sst.javac;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class OpenJdkJavacWorker implements AutoCloseable {
    private static final long WATCHDOG_SECONDS = 45;

    private final Gson gson = new Gson();
    private final Path root;
    private final Path classpathJar;
    private final ExecutorService responses = Executors.newVirtualThreadPerTaskExecutor();
    private Process process;
    private BufferedWriter requests;
    private BufferedReader replies;

    public OpenJdkJavacWorker(Path root) throws IOException {
        this.root = root;
        classpathJar = writeClasspathJar();
    }

    public OpenJdkJavacChecks.Result run(String id) throws IOException, InterruptedException {
        if (process == null) {
            start();
        }
        String failure;
        try {
            requests.write(gson.toJson(id));
            requests.newLine();
            requests.flush();
            String line = readReply();
            if (line != null && line.startsWith("{")) {
                var result = gson.fromJson(line, OpenJdkJavacChecks.Result.class);
                if (id.equals(result.id()))
                    return result;
            }
            failure = line == null ? "SST worker exited without a result" : "SST worker stopped: " + line;
        } catch (TimeoutException exception) {
            failure = "SST worker exceeded the " + WATCHDOG_SECONDS + "-second watchdog";
        } catch (ExecutionException | IOException | JsonParseException exception) {
            failure = "SST worker communication failed: " + exception;
        }
        stop();
        return new OpenJdkJavacChecks.Result(id, OpenJdkJavacChecks.Status.ERROR, 0, 0, 0, List.of(failure),
            List.of());
    }

    private void start() throws IOException, InterruptedException {
        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String heap = "-Xmx" + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + "m";
        process = new ProcessBuilder(java, heap, "-XX:+ExitOnOutOfMemoryError", "-cp", classpathJar.toString(),
            OpenJdkJavacRunner.class.getName(), "--worker", root.toString())
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        requests = process.outputWriter(UTF_8);
        replies = process.inputReader(UTF_8);
        try {
            String reply = readReply();
            if (!"READY".equals(reply))
                throw new IOException("SST worker did not start: " + reply);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("SST worker did not start", exception);
        }
    }

    private String readReply() throws ExecutionException, InterruptedException, TimeoutException {
        return responses.submit(replies::readLine).get(WATCHDOG_SECONDS, TimeUnit.SECONDS);
    }

    private void stop() throws InterruptedException {
        if (process != null) {
            process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            process = null;
        }
    }

    private static Path writeClasspathJar() throws IOException {
        // A manifest classpath keeps the child command short on Windows.
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
            .map(entry -> Path.of(entry).toAbsolutePath().toUri().toASCIIString())
            .collect(Collectors.joining(" "));
        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.CLASS_PATH, classpath);
        Path jar = Files.createTempFile("sst-javac-classpath-", ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.flush();
        } catch (IOException exception) {
            Files.deleteIfExists(jar);
            throw exception;
        }
        return jar;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        try {
            stop();
        } finally {
            responses.shutdownNow();
            Files.deleteIfExists(classpathJar);
        }
    }
}
