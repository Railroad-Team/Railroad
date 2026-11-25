package dev.railroadide.railroad.ide.sst.javac;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class OpenJdkJavacSuite {
    private static final Pattern JAVA_COMMENT = Pattern.compile("\\s*(?:/\\*(.*?)\\*/|//[^\\r\\n]*)", Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
    private static final Pattern SHELL_COMMENT = Pattern.compile("(?m)(?:^\\h*#[^\\r\\n]*\\R?)+");
    private static final Pattern TAG = Pattern.compile("@([\\w/=.\\-]+)(?:\\s+(.*))?");
    private static final Pattern ID = Pattern.compile("(?:^|\\s)id=([\\w-]+)(?:\\s|$)");

    private final Path root;
    private final Path javac;

    public OpenJdkJavacSuite(Path checkout) throws IOException {
        root = checkout.toRealPath();
        javac = root.resolve("test/langtools/tools/javac");
        if (!Files.isRegularFile(root.resolve("test/langtools/TEST.ROOT")) || !Files.isDirectory(javac))
            throw new IOException("Expected an OpenJDK checkout with test/langtools/tools/javac: " + root);
    }

    public Path root() {
        return root;
    }

    public List<TestCase> discover(String selection) throws IOException {
        int hash = selection.indexOf('#');
        String fileSelection = hash < 0 ? selection : selection.substring(0, hash);
        String idSelection = hash < 0 ? null : selection.substring(hash + 1);
        Path selected = javac.resolve(fileSelection).normalize();
        if (!selected.startsWith(javac) || !Files.exists(selected) || !selected.toRealPath().startsWith(javac))
            throw new IOException("Invalid javac test selection: " + selection);

        if (idSelection != null && (!Files.isRegularFile(selected) || idSelection.isEmpty()))
            throw new IOException("A test ID requires a file and a nonempty suffix: " + selection);

        List<TestCase> tests = new ArrayList<>();
        try (var files = Files.walk(selected)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (isDescriptor(file) && file.toRealPath().startsWith(javac)) {
                    tests.addAll(readCases(file));
                }
            }
        }

        if (idSelection != null) {
            tests.removeIf(test -> !test.id().equals(pathId(selected) + "#" + idSelection));
        }

        if (tests.isEmpty())
            throw new IOException("No tagged javac tests match " + (selection.isEmpty() ? javac : selection));

        return tests.stream().sorted(Comparator.comparing(TestCase::id)).toList();
    }

    private List<TestCase> readCases(Path file) throws IOException {
        // The tags are ASCII even when a compile action declares a different encoding for its sources.
        String source = Files.readString(file, ISO_8859_1);
        List<List<Tag>> descriptions = new ArrayList<List<Tag>>();
        String filename = file.getFileName().toString();
        if (filename.endsWith(".sh") || filename.endsWith(".html")) {
            boolean shell = filename.endsWith(".sh");
            Matcher comments = (shell ? SHELL_COMMENT : HTML_COMMENT).matcher(source);
            while (comments.find()) {
                addDescription(descriptions, shell ? comments.group() : comments.group(1));
            }
        } else {
            Matcher comments = JAVA_COMMENT.matcher(source);
            while (comments.lookingAt()) {
                String block = comments.group(1);
                if (block != null) {
                    addDescription(descriptions, block);
                }

                comments.region(comments.end(), source.length());
            }
        }

        List<TestCase> cases = new ArrayList<>();
        for (int index = 0; index < descriptions.size(); index++) {
            List<Tag> tags = descriptions.get(index);
            Matcher id = ID.matcher(tags.getFirst().value());
            String suffix = id.find() ? "#" + id.group(1) : descriptions.size() > 1 ? "#id" + index : "";
            cases.add(new TestCase(pathId(file) + suffix, file, tags));
        }

        return cases;
    }

    private static void addDescription(List<List<Tag>> descriptions, String comment) {
        List<Tag> tags = new ArrayList<>();
        for (String line : comment.lines().toList()) {
            String content = line.stripLeading().replaceFirst("^[*#]\\s*", "").stripTrailing();
            Matcher match = TAG.matcher(content);
            if (match.matches()) {
                tags.add(new Tag(match.group(1), match.group(2) == null ? "" : match.group(2)));
            } else if (!tags.isEmpty() && !content.isEmpty()) {
                Tag previous = tags.removeLast();
                tags.add(new Tag(previous.name(), (previous.value() + " " + content).strip()));
            }
        }

        if (!tags.isEmpty() && tags.getFirst().name().equals("test")) {
            descriptions.add(List.copyOf(tags));
        }
    }

    private String pathId(Path file) {
        return javac.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isDescriptor(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".sh") || name.endsWith(".html")
            || name.endsWith(".jasm") || name.endsWith(".jcod");
    }

    public record TestCase(String id, Path file, List<Tag> tags) {
        public boolean javaSource() {
            return file.getFileName().toString().endsWith(".java");
        }
    }

    public record Tag(String name, String value) {
    }
}
