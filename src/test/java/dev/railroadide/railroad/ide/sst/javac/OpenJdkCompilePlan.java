package dev.railroadide.railroad.ide.sst.javac;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public record OpenJdkCompilePlan(List<Action> actions, String unsupported) {
    private static final String JDK_VERSION = Integer.toString(Runtime.version().feature());
    private static final Pattern CLASS_NAME = Pattern.compile("[\\w$]+(?:\\.[\\w$]+)*");
    private static final Set<String> INFORMATION_TAGS = Set.of("test", "bug", "summary", "author", "key", "comment",
        "library", "enablePreview");

    public static OpenJdkCompilePlan read(Path checkout, OpenJdkJavacSuite.TestCase test) throws IOException {
        boolean hasCompile = test.tags().stream().map(OpenJdkCompilePlan::actionTag)
            .anyMatch(tag -> tag.name().equals("compile") || tag.name().startsWith("compile/"));
        if (!hasCompile)
            return new OpenJdkCompilePlan(List.of(),
                "No declarative compile action. The jtreg driver requires an adapter.");

        boolean preview = test.tags().stream().anyMatch(tag -> tag.name().equals("enablePreview"));
        var actions = new ArrayList<Action>();
        try {
            for (var tag : test.tags()) {
                if (!INFORMATION_TAGS.contains(tag.name()) && !isAction(tag.name()))
                    throw new Unsupported("Test metadata @" + tag.name());
            }
            Path testRoot = checkout.resolve("test");
            Path testDir = test.file().getParent();
            List<Path> libraries = libraryRoots(test, testRoot, testDir);
            for (var original : test.tags()) {
                var tag = actionTag(original);
                if (INFORMATION_TAGS.contains(tag.name()) || tag.name().equals("clean") && actions.isEmpty())
                    continue;
                switch (tag.name().split("/", 2)[0]) {
                    case "compile" -> actions.add(compile(tag, testRoot, testDir, preview));
                    case "build" -> actions.add(build(tag, libraries, testRoot, preview));
                    default -> throw new Unsupported("jtreg action @" + tag.name());
                }
            }
        } catch (Unsupported exception) {
            return new OpenJdkCompilePlan(List.copyOf(actions), exception.getMessage());
        }
        return new OpenJdkCompilePlan(List.copyOf(actions), null);
    }

    private static boolean isAction(String name) {
        return Set.of("run", "compile", "build", "clean").contains(name.split("/", 2)[0]);
    }

    private static OpenJdkJavacSuite.Tag actionTag(OpenJdkJavacSuite.Tag tag) {
        if (!tag.name().equals("run") || tag.value().isBlank())
            return tag;

        String[] action = tag.value().split("\\s+", 2);
        return new OpenJdkJavacSuite.Tag(action[0], action.length == 2 ? action[1] : "");
    }

    private static List<Path> libraryRoots(OpenJdkJavacSuite.TestCase test, Path testRoot, Path testDir)
        throws IOException, Unsupported {
        var roots = new LinkedHashSet<Path>();
        roots.add(testDir);
        for (var tag : test.tags()) {
            if (tag.name().equals("library")) {
                for (String path : words(tag.value())) {
                    Path candidate = path.startsWith("/")
                        ? testRoot.resolve("langtools").resolve(path.substring(1))
                        : testDir.resolve(path);
                    if (path.startsWith("/") && Files.notExists(candidate)) {
                        candidate = testRoot.getParent().resolve(path.substring(1));
                    }
                    Path directory = withinTestTree(candidate, testRoot);
                    if (!Files.isDirectory(directory))
                        throw new Unsupported("Source library is not a directory: " + path);
                    roots.add(directory);
                }
            }
        }
        return List.copyOf(roots);
    }

    private static Action build(OpenJdkJavacSuite.Tag tag, List<Path> libraries, Path testRoot, boolean preview)
        throws IOException, Unsupported {
        if (!tag.name().equals("build"))
            throw new Unsupported("jtreg action @" + tag.name());

        var sources = new LinkedHashSet<Path>();
        for (String name : words(tag.value())) {
            if (!CLASS_NAME.matcher(name).matches())
                throw new Unsupported("@build class selector " + name);

            String relative = name.replace('.', '/') + ".java";
            Path path = libraries.stream().map(root -> root.resolve(relative)).filter(Files::isRegularFile)
                .findFirst().orElseThrow(() -> new Unsupported("@build has no Java source for " + name));
            sources.add(withinTestTree(path, testRoot));
        }
        if (sources.isEmpty())
            throw new IOException("@build requires at least one class");
        return new Action("build", List.copyOf(sources), UTF_8, preview, false, null);
    }

    private static Action compile(OpenJdkJavacSuite.Tag tag, Path testRoot, Path testDir, boolean preview)
        throws IOException, Unsupported {
        boolean fails = false;
        Path reference = null;
        String[] modifiers = tag.name().split("/");
        for (int index = 1; index < modifiers.length; index++) {
            String modifier = modifiers[index];
            if (modifier.equals("fail")) {
                fails = true;
            } else if (modifier.startsWith("ref=")) {
                reference = requiredFile(testDir.resolve(modifier.substring(4)), testRoot);
            } else
                throw new Unsupported("jtreg compile modifier /" + modifier);
        }

        var sources = new LinkedHashSet<Path>();
        Charset encoding = UTF_8;
        List<String> arguments = words(tag.value());
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index).replace("${jdk.version}", JDK_VERSION);
            switch (argument) {
                case "--enable-preview" -> preview = true;
                case "-source", "--source", "--release", "-target", "--target" -> {
                    String version = operand(arguments, ++index, argument).replace("${jdk.version}", JDK_VERSION);
                    if (!JDK_VERSION.equals(version))
                        throw new Unsupported(argument + " " + version + " requires a different language profile");
                }
                case "-encoding" -> encoding = Charset.forName(operand(arguments, ++index, argument));
                case "-XDrawDiagnostics", "-proc:none", "-nowarn", "-g", "-g:none", "-parameters" -> {
                }
                default -> {
                    if (argument.equals("-Xlint") || argument.startsWith("-Xlint:"))
                        continue;
                    if (argument.startsWith("-") || !argument.endsWith(".java") || argument.contains("${")
                        || argument.equals("module-info.java") || argument.endsWith("/module-info.java"))
                        throw new Unsupported("javac argument " + argument);
                    sources.add(requiredFile(testDir.resolve(argument), testRoot));
                }
            }
        }
        if (sources.isEmpty())
            throw new IOException("@" + tag.name() + " requires at least one Java source");
        return new Action(tag.name(), List.copyOf(sources), encoding, preview, fails, reference);
    }

    private static String operand(List<String> arguments, int index, String option) throws IOException {
        if (index >= arguments.size())
            throw new IOException("Missing argument for " + option);
        return arguments.get(index);
    }

    private static List<String> words(String value) {
        return value.isBlank() ? List.of() : List.of(value.strip().split("\\s+"));
    }

    private static Path requiredFile(Path path, Path testRoot) throws IOException, Unsupported {
        Path resolved = withinTestTree(path, testRoot);
        if (!Files.isRegularFile(resolved))
            throw new IOException("Missing test file: " + path);
        return resolved;
    }

    private static Path withinTestTree(Path path, Path testRoot) throws IOException, Unsupported {
        Path normalized = path.normalize();
        if (!normalized.startsWith(testRoot))
            throw new Unsupported("Path outside the OpenJDK test tree: " + path);
        if (Files.exists(normalized) && !normalized.toRealPath().startsWith(testRoot))
            throw new Unsupported("Path outside the OpenJDK test tree: " + path);
        return normalized;
    }

    public record Action(
        String name,
        List<Path> sources,
        Charset encoding,
        boolean preview,
        boolean fails,
        Path reference
    ) {
    }

    private static final class Unsupported extends Exception {
        private Unsupported(String reason) {
            super(reason);
        }
    }
}
