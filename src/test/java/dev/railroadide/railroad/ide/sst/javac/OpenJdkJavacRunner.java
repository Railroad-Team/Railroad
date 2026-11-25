package dev.railroadide.railroad.ide.sst.javac;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class OpenJdkJavacRunner {
    private static final int CONSOLE_FINDING_LIMIT = 240;

    private OpenJdkJavacRunner() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args[0].equals("--worker")) {
            serve(Path.of(args[1]));
            return;
        }

        var suite = new OpenJdkJavacSuite(Path.of(args[0]));
        Path reportPath = Path.of(args[1]);
        String selection = args.length == 3 ? args[2] : "";
        String revision = revision(suite.root());
        List<OpenJdkJavacSuite.TestCase> cases = suite.discover(selection);
        System.out.println("Checking " + cases.size() + " OpenJDK javac cases at " + revision
            + " with JDK " + Runtime.version());

        List<OpenJdkJavacChecks.Result> results = new ArrayList<OpenJdkJavacChecks.Result>();
        Map<OpenJdkJavacChecks.Status, Integer> counts = new EnumMap<OpenJdkJavacChecks.Status, Integer>(
            OpenJdkJavacChecks.Status.class);
        for (OpenJdkJavacChecks.Status status : OpenJdkJavacChecks.Status.values()) {
            counts.put(status, 0);
        }

        try (var worker = new OpenJdkJavacWorker(suite.root())) {
            for (OpenJdkJavacSuite.TestCase test : cases) {
                OpenJdkJavacChecks.Result result = worker.run(test.id());
                results.add(result);
                counts.merge(result.status(), 1, Integer::sum);
                if (result.status() == OpenJdkJavacChecks.Status.MISMATCH
                    || result.status() == OpenJdkJavacChecks.Status.ERROR) {
                    String finding = result.findings().getFirst().lines().findFirst().orElseThrow();
                    if (finding.length() > CONSOLE_FINDING_LIMIT) {
                        finding = finding.substring(0, CONSOLE_FINDING_LIMIT) + "...";
                    }

                    System.out.println(result.status() + " " + result.id() + " " + finding);
                }
            }
        }

        var report = new Report(suite.root().toString(), revision, Runtime.version().toString(), selection, counts,
            results);
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(reportPath, UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report, writer);
            writer.newLine();
        }

        System.out.println(counts + "  Report: " + reportPath);
        if (counts.get(OpenJdkJavacChecks.Status.MISMATCH) > 0 || counts.get(OpenJdkJavacChecks.Status.ERROR) > 0) {
            System.exit(1);
        }
    }

    private static void serve(Path root) throws IOException {
        var protocol = new BufferedWriter(new OutputStreamWriter(System.out, UTF_8));
        System.setOut(new PrintStream(OutputStream.nullOutputStream(), false, UTF_8));
        var gson = new Gson();
        var suite = new OpenJdkJavacSuite(root);
        try (var requests = new BufferedReader(new InputStreamReader(System.in, UTF_8));
            var checks = new OpenJdkJavacChecks(suite.root())) {
            protocol.write("READY");
            protocol.newLine();
            protocol.flush();
            String request;
            while ((request = requests.readLine()) != null) {
                String id = gson.fromJson(request, String.class);
                OpenJdkJavacSuite.TestCase test = suite.discover(id).getFirst();
                OpenJdkJavacChecks.Result result = runCase(checks, suite.root(), test);
                protocol.write(gson.toJson(result));
                protocol.newLine();
                protocol.flush();
            }
        }
    }

    public static OpenJdkJavacChecks.Result runCase(
        OpenJdkJavacChecks checks,
        Path root,
        OpenJdkJavacSuite.TestCase test
    ) {
        try {
            return checks.check(root, test);
        } catch (IOException | RuntimeException | StackOverflowError | AssertionError exception) {
            var trace = new StringWriter();
            exception.printStackTrace(new PrintWriter(trace));
            return new OpenJdkJavacChecks.Result(test.id(), OpenJdkJavacChecks.Status.ERROR, 0, 0, 0,
                List.of(trace.toString()), List.of());
        }
    }

    private static String revision(Path root) throws IOException, InterruptedException {
        Process git = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "--verify", "HEAD")
            .redirectErrorStream(true).start();
        String output;
        try (InputStream input = git.getInputStream()) {
            output = new String(input.readAllBytes(), UTF_8).strip();
        }

        if (git.waitFor() != 0 || !output.matches("[a-f0-9]{40,64}"))
            throw new IOException("Could not identify the OpenJDK checkout revision: " + output);

        return output;
    }

    private record Report(
        String checkout,
        String revision,
        String jdk,
        String selection,
        Map<OpenJdkJavacChecks.Status, Integer> counts,
        List<OpenJdkJavacChecks.Result> cases
    ) {
    }
}
