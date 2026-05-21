package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenElement;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenNode;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenToken;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

public final class JavaSyntaxMemoryProfileRunner {
    private static final int DEFAULT_MAX_FILES = Integer.MAX_VALUE;

    private JavaSyntaxMemoryProfileRunner() {
    }

    public static void main(String[] args) throws IOException {
        ProfileOptions options = parseOptions(args);
        List<SourceUnit> units = loadSources(options.inputs(), options.maxFiles());
        if (units.isEmpty()) {
            System.out.println("No Java sources found for memory profile input.");
            return;
        }

        long totalBytes = units.stream().mapToLong(SourceUnit::bytes).sum();
        long totalLines = units.stream().mapToLong(SourceUnit::lineCount).sum();
        System.out.printf(
                Locale.ROOT,
                "Java syntax memory profile: files=%d, size=%.2f MiB, lines=%d%n",
                units.size(),
                bytesToMiB(totalBytes),
                totalLines
        );

        forceGc();
        long heapBeforeParse = usedHeapBytes();
        List<SyntaxTree> trees = new ArrayList<>(units.size());
        for (SourceUnit unit : units) {
            trees.add(JavaSyntaxParser.parse(unit.source()));
        }

        forceGc();
        long heapAfterParse = usedHeapBytes();
        AggregateStats greenStats = collectGreenStats(trees);
        long heapAfterGreenWalk = heapAfterParse;
        long heapAfterRedWalk = heapAfterParse;
        AggregateStats redStats = new AggregateStats();
        if (options.materializeRed()) {
            redStats = collectRedStats(trees);
            forceGc();
            heapAfterRedWalk = usedHeapBytes();
            heapAfterGreenWalk = heapAfterParse;
        }

        printGreenStats(greenStats, totalBytes);
        if (options.materializeRed())
            printRedStats(redStats, totalBytes);

        if (options.measureHeap()) {
            System.out.println();
            System.out.println("Heap deltas (approximate, GC-sensitive):");
            System.out.printf(
                    Locale.ROOT,
                    "  Parse tree retained: %s%n",
                    formatBytes(heapAfterParse - heapBeforeParse)
            );
            if (options.materializeRed()) {
                System.out.printf(
                        Locale.ROOT,
                        "  Additional red wrappers (after full traversal): %s%n",
                        formatBytes(heapAfterRedWalk - heapAfterGreenWalk)
                );
            }
        }
    }

    private static void printGreenStats(AggregateStats stats, long totalBytes) {
        System.out.println();
        System.out.println("Green tree profile:");
        System.out.printf(Locale.ROOT, "  Nodes: %d%n", stats.nodeCount());
        System.out.printf(Locale.ROOT, "  Tokens: %d%n", stats.tokenCount());
        System.out.printf(Locale.ROOT, "  Token text chars: %d%n", stats.tokenChars());
        System.out.printf(Locale.ROOT, "  Max depth: %d%n", stats.maxDepth());
        System.out.printf(
                Locale.ROOT,
                "  Density: %.2f elements / KiB%n",
                densityPerKiB(stats.totalElements(), totalBytes)
        );
    }

    private static void printRedStats(AggregateStats stats, long totalBytes) {
        System.out.println();
        System.out.println("Red wrapper profile (fully materialized):");
        System.out.printf(Locale.ROOT, "  Nodes: %d%n", stats.nodeCount());
        System.out.printf(Locale.ROOT, "  Tokens: %d%n", stats.tokenCount());
        System.out.printf(Locale.ROOT, "  Max depth: %d%n", stats.maxDepth());
        System.out.printf(
                Locale.ROOT,
                "  Density: %.2f wrappers / KiB%n",
                densityPerKiB(stats.totalElements(), totalBytes)
        );
    }

    private static double densityPerKiB(long count, long bytes) {
        if (bytes <= 0)
            return 0.0;

        return (double) count / ((double) bytes / 1024.0);
    }

    private static AggregateStats collectGreenStats(List<SyntaxTree> trees) {
        AggregateStats stats = new AggregateStats();
        for (SyntaxTree tree : trees) {
            GreenNode root = SyntaxInternalFactory.greenRoot(tree);
            Deque<GreenFrame> stack = new ArrayDeque<>();
            stack.push(new GreenFrame(root, 1));
            while (!stack.isEmpty()) {
                GreenFrame frame = stack.pop();
                GreenElement element = frame.element();
                int depth = frame.depth();
                stats.recordDepth(depth);
                if (element instanceof GreenToken token) {
                    stats.recordToken(token.text().length());
                    continue;
                }

                GreenNode node = (GreenNode) element;
                stats.recordNode();
                List<GreenElement> children = node.children();
                for (int childIndex = children.size() - 1; childIndex >= 0; childIndex--) {
                    stack.push(new GreenFrame(children.get(childIndex), depth + 1));
                }
            }
        }

        return stats;
    }

    private static AggregateStats collectRedStats(List<SyntaxTree> trees) {
        AggregateStats stats = new AggregateStats();
        for (SyntaxTree tree : trees) {
            Deque<RedFrame> stack = new ArrayDeque<>();
            stack.push(new RedFrame(tree.root(), 1));
            while (!stack.isEmpty()) {
                RedFrame frame = stack.pop();
                SyntaxNode node = frame.node();
                int depth = frame.depth();
                stats.recordDepth(depth);
                if (node instanceof SyntaxToken) {
                    stats.recordToken(0);
                    continue;
                }

                stats.recordNode();
                List<SyntaxNode> children = node.children();
                for (int childIndex = children.size() - 1; childIndex >= 0; childIndex--) {
                    stack.push(new RedFrame(children.get(childIndex), depth + 1));
                }
            }
        }

        return stats;
    }

    private static ProfileOptions parseOptions(String[] args) {
        Objects.requireNonNull(args, "args");
        int maxFiles = DEFAULT_MAX_FILES;
        boolean materializeRed = true;
        boolean measureHeap = true;
        List<Path> inputs = new ArrayList<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank())
                continue;

            if (arg.startsWith("--max-files=")) {
                String value = arg.substring("--max-files=".length());
                maxFiles = Math.max(1, Integer.parseInt(value));
                continue;
            }

            if ("--no-red".equals(arg)) {
                materializeRed = false;
                continue;
            }

            if ("--no-heap".equals(arg)) {
                measureHeap = false;
                continue;
            }

            inputs.add(Path.of(arg));
        }

        if (inputs.isEmpty())
            inputs = List.of(Path.of("src/main/java"));

        return new ProfileOptions(List.copyOf(inputs), maxFiles, materializeRed, measureHeap);
    }

    private static List<SourceUnit> loadSources(List<Path> inputs, int maxFiles) throws IOException {
        TreeSet<Path> javaFiles = new TreeSet<>();
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            if (!Files.exists(normalized))
                continue;

            if (Files.isRegularFile(normalized)) {
                if (normalized.toString().endsWith(".java"))
                    javaFiles.add(normalized);
                continue;
            }

            try (var stream = Files.walk(normalized)) {
                stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }
        }

        List<SourceUnit> units = new ArrayList<>(Math.min(javaFiles.size(), maxFiles));
        int loaded = 0;
        for (Path path : javaFiles) {
            if (loaded >= maxFiles)
                break;

            String source = Files.readString(path, StandardCharsets.UTF_8);
            long lines = source.lines().count();
            long bytes = source.getBytes(StandardCharsets.UTF_8).length;
            units.add(new SourceUnit(path, source, lines, bytes));
            loaded++;
        }

        units.sort(Comparator.comparing(SourceUnit::path));
        return units;
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static String formatBytes(long bytes) {
        long abs = Math.abs(bytes);
        if (abs < 1024L)
            return bytes + " B";
        if (abs < 1024L * 1024L)
            return String.format(Locale.ROOT, "%.2f KiB", bytes / 1024.0);

        return String.format(Locale.ROOT, "%.2f MiB", bytes / (1024.0 * 1024.0));
    }

    private static double bytesToMiB(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private record SourceUnit(Path path, String source, long lineCount, long bytes) {
    }

    private record ProfileOptions(
            List<Path> inputs,
            int maxFiles,
            boolean materializeRed,
            boolean measureHeap
    ) {
    }

    private record GreenFrame(GreenElement element, int depth) {
    }

    private record RedFrame(SyntaxNode node, int depth) {
    }

    private static final class AggregateStats {
        private long nodeCount;
        private long tokenCount;
        private long tokenChars;
        private int maxDepth;

        void recordNode() {
            nodeCount++;
        }

        void recordToken(int chars) {
            tokenCount++;
            tokenChars += chars;
        }

        void recordDepth(int depth) {
            maxDepth = Math.max(maxDepth, depth);
        }

        long nodeCount() {
            return nodeCount;
        }

        long tokenCount() {
            return tokenCount;
        }

        long tokenChars() {
            return tokenChars;
        }

        int maxDepth() {
            return maxDepth;
        }

        long totalElements() {
            return nodeCount + tokenCount;
        }
    }
}
