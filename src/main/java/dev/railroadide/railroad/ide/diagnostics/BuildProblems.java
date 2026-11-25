package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.utility.DecodingOutputStream;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Source locations and messages from each build target's latest execution in one project.
 */
public final class BuildProblems {
    /**
     * One build error.
     *
     * @param file source or build script containing the error, or {@code null} when unknown
     * @param line one-based line of the error, or 0 when unknown
     * @param column one-based column of the error
     * @param message error message
     */
    public record Problem(@Nullable Path file, int line, int column, String message) {
    }

    private final List<Consumer<List<Problem>>> listeners = new ArrayList<>();
    private List<Problem> problems = List.of();
    private final Map<String, Report> running = new HashMap<>();
    private final Map<String, List<Problem>> failures = new LinkedHashMap<>();

    /**
     * Starts collecting problems for a new execution of a build target, clearing its previous problems.
     *
     * @param target identifier of the built target, such as a task path
     * @param directory build directory that relative file names are resolved against
     * @return report receiving the execution's output and outcome
     */
    public synchronized Report begin(String target, Path directory) {
        var report = new Report(directory, target);
        running.put(target, report);
        if (failures.remove(target) != null) {
            publish();
        }

        return report;
    }

    /**
     * Returns the problems of every target whose latest execution failed.
     *
     * @return current problems
     */
    public synchronized List<Problem> problems() {
        return problems;
    }

    /**
     * Replays the current list and delivers replacements, in order, on the publishing thread.
     *
     * @param listener receives the current problems and every later replacement
     * @return action that stops delivery
     */
    public synchronized Runnable subscribe(Consumer<List<Problem>> listener) {
        listeners.add(listener);
        listener.accept(problems);
        return () -> {
            synchronized (this) {
                listeners.remove(listener);
            }
        };
    }

    private void publish() {
        List<Problem> next = failures.values().stream().flatMap(List::stream).distinct().toList();
        if (problems.equals(next))
            return;

        problems = next;
        listeners.forEach(listener -> listener.accept(next));
    }

    /**
     * Collects the problems of one execution of a build target from compiler events and console output.
     */
    public final class Report {
        private static final Pattern JAVAC = Pattern.compile("^(.*?\\.java):(\\d+):(?:(\\d+):)?\\s*error:\\s*(.+)$");
        private static final Pattern SCRIPT = Pattern.compile("^(?:Build|Settings) file '(.+)' line: (\\d+)$");
        private static final Pattern COLOR = Pattern.compile("\\x1b\\[[0-9;]*m");
        private final Path directory;
        private final String target;
        private @Nullable Path realDirectory;
        private final Set<Problem> located = new LinkedHashSet<>();
        private final Set<Problem> parsed = new LinkedHashSet<>();
        private final List<Lines> channels = new ArrayList<>();
        private @Nullable Path script;
        private int scriptLine;
        private boolean finished;

        private Report(Path directory, String target) {
            this.directory = directory.toAbsolutePath().normalize();
            this.target = target;
        }

        /**
         * Records a problem whose location the build tool reported directly.
         *
         * @param file file path, absolute or relative to the build directory
         * @param line one-based line
         * @param column one-based column, or a smaller value when unknown
         * @param message problem message
         */
        public synchronized void location(String file, int line, int column, String message) {
            Path path = resolve(file);
            if (!finished && path != null && line > 0) {
                located.add(new Problem(path, line, Math.max(column, 1), message));
            }
        }

        /**
         * Forwards decoded console text unchanged while reading compiler lines from this output channel.
         *
         * @param charset encoding of the build output
         * @param sink receives the decoded text
         * @return stream to pass to the build as one output channel
         */
        public synchronized OutputStream output(Charset charset, Consumer<String> sink) {
            var lines = new Lines();
            channels.add(lines);
            return new DecodingOutputStream(charset, text -> {
                sink.accept(text);
                synchronized (this) {
                    if (!finished) {
                        lines.accept(text);
                    }
                }
            });
        }

        /**
         * Ends the execution and publishes its problems if it failed. Cancelled executions report nothing.
         *
         * @param failure failure of the execution, or {@code null} when it succeeded
         */
        public synchronized void finish(@Nullable Throwable failure) {
            if (finished)
                return;

            finished = true;
            synchronized (BuildProblems.this) {
                if (!running.remove(target, this) || failure == null)
                    return;

                String message = failure.toString();
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause instanceof CancellationException)
                        return;

                    if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                        message = cause.getMessage().strip();
                        message.lines().forEach(this::parse);
                    }
                }

                channels.forEach(Lines::end);
                List<Problem> result = new ArrayList<>(located);
                for (Problem problem : parsed) {
                    boolean reported = located.stream().anyMatch(other -> other.file().equals(problem.file())
                        && other.line() == problem.line() && other.message().contains(problem.message()));
                    if (!reported) {
                        result.add(problem);
                    }
                }

                if (result.isEmpty()) {
                    result.add(new Problem(script, scriptLine, 1, message));
                }

                failures.put(target, List.copyOf(result));
                publish();
            }
        }

        private @Nullable Path resolve(String path) {
            try {
                Path source = directory.resolve(path).normalize();
                if (source.startsWith(directory))
                    return source;

                if (realDirectory == null) {
                    realDirectory = ProjectPathIdentityUtils.normalize(directory);
                }

                return source.startsWith(realDirectory) ? directory.resolve(realDirectory.relativize(source)) : source;
            } catch (InvalidPathException _) {
                return null;
            }
        }

        private void parse(String text) {
            if (!text.contains(".java:") && !text.contains(" file '"))
                return;

            String line = COLOR.matcher(text).replaceAll("").strip();
            if (line.startsWith("> ")) {
                line = line.substring(2).stripLeading();
            }

            Matcher compiler = JAVAC.matcher(line);
            Matcher buildScript = SCRIPT.matcher(line);
            try {
                if (compiler.matches()) {
                    Path file = resolve(compiler.group(1));
                    int number = Integer.parseInt(compiler.group(2));
                    int column = compiler.group(3) == null ? 1 : Integer.parseInt(compiler.group(3));
                    if (file != null && number > 0) {
                        parsed.add(new Problem(file, number, Math.max(1, column), compiler.group(4)));
                    }
                } else if (buildScript.matches()) {
                    script = resolve(buildScript.group(1));
                    scriptLine = Integer.parseInt(buildScript.group(2));
                }
            } catch (NumberFormatException _) {
                // Output can include arbitrary application text resembling compiler locations.
            }
        }

        private final class Lines {
            private final StringBuilder pending = new StringBuilder();
            private boolean oversized;

            private void accept(String text) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\r' || c == '\n') {
                        end();
                    } else if (pending.length() < 32_768) {
                        pending.append(c);
                    } else {
                        oversized = true;
                    }
                }
            }

            private void end() {
                if (!oversized && !pending.isEmpty()) {
                    parse(pending.toString());
                }

                pending.setLength(0);
                oversized = false;
            }
        }
    }
}
