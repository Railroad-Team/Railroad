package dev.railroadide.railroad.ide.sst.impl.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class JavaParserBenchmarkRunner {
    private static final int DEFAULT_WARMUP_ITERATIONS = 5;
    private static final int DEFAULT_MEASURE_ITERATIONS = 20;
    private static final int DEFAULT_REPEAT_PER_ITERATION = 1;
    private static final int DEFAULT_SLOWEST_FILES = 5;

    private static final long SMALL_FILE_LIMIT_BYTES = 16L * 1024L;
    private static final long MEDIUM_FILE_LIMIT_BYTES = 64L * 1024L;

    private JavaParserBenchmarkRunner() {
    }

    public static void main(String[] args) throws IOException {
        BenchmarkOptions options = parseOptions(args);
        List<SourceUnit> corpus = loadCorpus(options.inputs());
        if (corpus.isEmpty()) {
            System.out.println("No Java sources found for benchmark input.");
            return;
        }

        List<BenchmarkDataset> datasets = buildDatasets(corpus);
        Path cwd = Path.of("").toAbsolutePath().normalize();

        printRunHeader(options, corpus, datasets);
        for (BenchmarkDataset dataset : datasets) {
            runDataset(options, dataset, cwd);
        }
    }

    private static void runDataset(BenchmarkOptions options, BenchmarkDataset dataset, Path cwd) {
        System.out.println();
        System.out.printf(
                Locale.ROOT,
                "Dataset %s: files=%d, size=%.2f MiB%n",
                dataset.name(),
                dataset.units().size(),
                bytesToMiB(dataset.totalBytes())
        );

        List<BenchmarkResult> results = new ArrayList<>();
        for (ParseMode mode : options.modes()) {
            BenchmarkResult result = runBenchmark(mode, dataset, options);
            results.add(result);
        }

        printResultTable(results);

        if ("full".equals(dataset.name())) {
            for (BenchmarkResult result : results) {
                printSlowestFiles(result, options.slowestFiles(), cwd);
            }
        }
    }

    private static BenchmarkResult runBenchmark(ParseMode mode, BenchmarkDataset dataset, BenchmarkOptions options) {
        ParseStrategy strategy = strategyFor(mode);
        Map<Path, MutableFileTiming> perFile = new java.util.HashMap<>();

        for (int i = 0; i < options.warmupIterations(); i++) {
            runIteration(dataset.units(), options.repeatPerIteration(), strategy, null, mode);
        }

        List<Long> iterationNanos = new ArrayList<>(options.measureIterations());
        for (int i = 0; i < options.measureIterations(); i++) {
            long start = System.nanoTime();
            runIteration(dataset.units(), options.repeatPerIteration(), strategy, perFile, mode);
            iterationNanos.add(System.nanoTime() - start);
        }

        Map<Path, FileTiming> fileTiming = new java.util.HashMap<>(perFile.size());
        for (Map.Entry<Path, MutableFileTiming> entry : perFile.entrySet()) {
            fileTiming.put(entry.getKey(), entry.getValue().freeze());
        }

        long bytesPerIteration = dataset.totalBytes() * options.repeatPerIteration();
        int parsesPerIteration = dataset.units().size() * options.repeatPerIteration();
        return new BenchmarkResult(mode, dataset.name(), bytesPerIteration, parsesPerIteration, iterationNanos, Map.copyOf(fileTiming));
    }

    private static void runIteration(
            List<SourceUnit> units,
            int repeats,
            ParseStrategy strategy,
            Map<Path, MutableFileTiming> perFile,
            ParseMode mode
    ) {
        for (int repeat = 0; repeat < repeats; repeat++) {
            for (SourceUnit unit : units) {
                long parseStart = perFile == null ? 0L : System.nanoTime();
                try {
                    strategy.parse(unit);
                } catch (RuntimeException exception) {
                    String message = "Benchmark parse failed in mode " + mode.id + " for file " + unit.path();
                    throw new IllegalStateException(message, exception);
                }

                if (perFile != null) {
                    long elapsed = System.nanoTime() - parseStart;
                    perFile.computeIfAbsent(unit.path(), ignored -> new MutableFileTiming()).record(elapsed);
                }
            }
        }
    }

    private static ParseStrategy strategyFor(ParseMode mode) {
        if (mode != ParseMode.SYNTAX)
            throw new IllegalStateException("Unsupported parse mode: " + mode);
        return JavaParserBenchmarkRunner::parseSyntaxUnit;
    }

    private static void parseSyntaxUnit(SourceUnit unit) {
        JavaSyntaxParser.parse(unit.source());
    }

    private static void printResultTable(List<BenchmarkResult> results) {
        System.out.printf(
                Locale.ROOT,
                "%-8s %10s %10s %10s %10s %12s%n",
                "Mode",
                "p50(ms)",
                "p95(ms)",
                "mean(ms)",
                "ops/s",
                "MiB/s"
        );
        for (BenchmarkResult result : results) {
            System.out.printf(
                    Locale.ROOT,
                    "%-8s %10.3f %10.3f %10.3f %10.1f %12.2f%n",
                    result.mode().id,
                    nanosToMillis(percentileNanos(result.iterationNanos(), 0.50)),
                    nanosToMillis(percentileNanos(result.iterationNanos(), 0.95)),
                    nanosToMillis(meanNanos(result.iterationNanos())),
                    result.operationsPerSecond(),
                    result.mebibytesPerSecond()
            );
        }
    }

    private static void printSlowestFiles(BenchmarkResult result, int limit, Path cwd) {
        if (limit <= 0 || result.perFileTiming().isEmpty())
            return;

        List<Map.Entry<Path, FileTiming>> entries = new ArrayList<>(result.perFileTiming().entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<Path, FileTiming> entry) -> entry.getValue().averageNanos()).reversed());

        System.out.printf("Slowest files (%s, avg parse):%n", result.mode().id);
        int printed = 0;
        for (Map.Entry<Path, FileTiming> entry : entries) {
            if (printed >= limit)
                break;

            String displayPath = relativize(cwd, entry.getKey());
            FileTiming timing = entry.getValue();
            System.out.printf(
                    Locale.ROOT,
                    "  %s -> avg %.3f ms, max %.3f ms (%d samples)%n",
                    displayPath,
                    nanosToMillis(timing.averageNanos()),
                    nanosToMillis(timing.maxNanos()),
                    timing.samples()
            );
            printed++;
        }
    }

    private static void printRunHeader(BenchmarkOptions options, List<SourceUnit> corpus, List<BenchmarkDataset> datasets) {
        long bytes = 0L;
        long lines = 0L;
        for (SourceUnit unit : corpus) {
            bytes += unit.bytes();
            lines += unit.lines();
        }

        Map<SizeBucket, Integer> bucketCounts = new EnumMap<>(SizeBucket.class);
        for (SizeBucket bucket : SizeBucket.values()) {
            bucketCounts.put(bucket, 0);
        }
        for (SourceUnit unit : corpus) {
            bucketCounts.put(unit.bucket(), bucketCounts.get(unit.bucket()) + 1);
        }

        System.out.printf(
                Locale.ROOT,
                "Java parser benchmark: files=%d, size=%.2f MiB, lines=%d%n",
                corpus.size(),
                bytesToMiB(bytes),
                lines
        );
        System.out.printf(
                Locale.ROOT,
                "Warmup=%d, iterations=%d, repeats=%d, modes=%s%n",
                options.warmupIterations(),
                options.measureIterations(),
                options.repeatPerIteration(),
                options.modes()
        );
        System.out.printf(
                Locale.ROOT,
                "Buckets: small=%d, medium=%d, large=%d%n",
                bucketCounts.get(SizeBucket.SMALL),
                bucketCounts.get(SizeBucket.MEDIUM),
                bucketCounts.get(SizeBucket.LARGE)
        );
        System.out.print("Datasets: ");
        for (int i = 0; i < datasets.size(); i++) {
            if (i > 0)
                System.out.print(", ");
            System.out.print(datasets.get(i).name());
        }
        System.out.println();
    }

    private static List<BenchmarkDataset> buildDatasets(List<SourceUnit> corpus) {
        List<BenchmarkDataset> datasets = new ArrayList<>();
        datasets.add(new BenchmarkDataset("full", List.copyOf(corpus)));

        for (SizeBucket bucket : SizeBucket.values()) {
            List<SourceUnit> bucketUnits = new ArrayList<>();
            for (SourceUnit unit : corpus) {
                if (unit.bucket() == bucket)
                    bucketUnits.add(unit);
            }

            if (!bucketUnits.isEmpty()) {
                datasets.add(new BenchmarkDataset(bucket.id, List.copyOf(bucketUnits)));
            }
        }

        return datasets;
    }

    private static List<SourceUnit> loadCorpus(List<Path> inputs) throws IOException {
        Set<Path> files = new TreeSet<>();
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                try (var stream = Files.walk(normalized)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .forEach(files::add);
                }
            } else if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".java")) {
                files.add(normalized);
            }
        }

        List<SourceUnit> corpus = new ArrayList<>(files.size());
        for (Path file : files) {
            String source = Files.readString(file);
            long bytes = Files.size(file);
            int lines = countLines(source);
            SizeBucket bucket = SizeBucket.fromBytes(bytes);
            corpus.add(new SourceUnit(file, source, bytes, lines, bucket));
        }

        return corpus;
    }

    private static BenchmarkOptions parseOptions(String[] args) {
        int warmupIterations = DEFAULT_WARMUP_ITERATIONS;
        int measureIterations = DEFAULT_MEASURE_ITERATIONS;
        int repeatPerIteration = DEFAULT_REPEAT_PER_ITERATION;
        int slowestFiles = DEFAULT_SLOWEST_FILES;
        Set<ParseMode> modes = EnumSet.of(ParseMode.SYNTAX);
        List<Path> inputs = new ArrayList<>();

        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                System.exit(0);
            } else if (arg.startsWith("--warmup=")) {
                warmupIterations = parsePositiveInt(arg, "--warmup=");
            } else if (arg.startsWith("--iterations=")) {
                measureIterations = parsePositiveInt(arg, "--iterations=");
            } else if (arg.startsWith("--repeat=")) {
                repeatPerIteration = parsePositiveInt(arg, "--repeat=");
            } else if (arg.startsWith("--slowest=")) {
                slowestFiles = parseNonNegativeInt(arg, "--slowest=");
            } else if (arg.startsWith("--mode=")) {
                modes = parseModes(arg.substring("--mode=".length()));
            } else {
                inputs.add(Path.of(arg));
            }
        }

        if (inputs.isEmpty()) {
            inputs.add(Path.of("src/main/java"));
        }

        return new BenchmarkOptions(warmupIterations, measureIterations, repeatPerIteration, slowestFiles, Set.copyOf(modes), List.copyOf(inputs));
    }

    private static Set<ParseMode> parseModes(String rawMode) {
        String mode = rawMode.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "syntax" -> EnumSet.of(ParseMode.SYNTAX);
            default -> throw new IllegalArgumentException("Unsupported mode: " + rawMode + " (expected syntax)");
        };
    }

    private static int parsePositiveInt(String argument, String prefix) {
        int value = parseInt(argument, prefix);
        if (value <= 0)
            throw new IllegalArgumentException("Expected value > 0 for " + prefix + " in " + argument);
        return value;
    }

    private static int parseNonNegativeInt(String argument, String prefix) {
        int value = parseInt(argument, prefix);
        if (value < 0)
            throw new IllegalArgumentException("Expected value >= 0 for " + prefix + " in " + argument);
        return value;
    }

    private static int parseInt(String argument, String prefix) {
        String value = argument.substring(prefix.length());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + prefix + " in " + argument, exception);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: JavaParserBenchmarkRunner [options] <file-or-directory>...");
        System.out.println("Options:");
        System.out.println("  --warmup=<n>      Warmup iterations per dataset (default " + DEFAULT_WARMUP_ITERATIONS + ")");
        System.out.println("  --iterations=<n>  Measured iterations per dataset (default " + DEFAULT_MEASURE_ITERATIONS + ")");
        System.out.println("  --repeat=<n>      Repeat corpus parses inside each iteration (default " + DEFAULT_REPEAT_PER_ITERATION + ")");
        System.out.println("  --mode=<m>        syntax (default syntax)");
        System.out.println("  --slowest=<n>     Print N slowest files for full dataset (default " + DEFAULT_SLOWEST_FILES + ")");
        System.out.println("  --help            Show this help");
    }

    private static int countLines(String source) {
        if (source.isEmpty())
            return 0;

        int lines = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n')
                lines++;
        }

        return lines;
    }

    private static long percentileNanos(List<Long> samples, double percentile) {
        if (samples.isEmpty())
            return 0L;

        long[] copy = new long[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            copy[i] = samples.get(i);
        }
        Arrays.sort(copy);

        double clamped = Math.max(0.0, Math.min(1.0, percentile));
        int index = (int) Math.ceil(clamped * copy.length) - 1;
        index = Math.max(0, Math.min(index, copy.length - 1));
        return copy[index];
    }

    private static double meanNanos(List<Long> samples) {
        if (samples.isEmpty())
            return 0.0;

        long sum = 0L;
        for (long sample : samples) {
            sum += sample;
        }
        return (double) sum / (double) samples.size();
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static double bytesToMiB(long bytes) {
        return (double) bytes / (1024.0 * 1024.0);
    }

    private static String relativize(Path base, Path target) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(base))
            return normalizedTarget.toString();

        return base.relativize(normalizedTarget).toString();
    }

    private enum ParseMode {
        SYNTAX("syntax");

        private final String id;

        ParseMode(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private enum SizeBucket {
        SMALL("small"),
        MEDIUM("medium"),
        LARGE("large");

        private final String id;

        SizeBucket(String id) {
            this.id = id;
        }

        private static SizeBucket fromBytes(long bytes) {
            if (bytes <= SMALL_FILE_LIMIT_BYTES)
                return SMALL;
            if (bytes <= MEDIUM_FILE_LIMIT_BYTES)
                return MEDIUM;
            return LARGE;
        }
    }

    private record SourceUnit(Path path, String source, long bytes, int lines, SizeBucket bucket) {
    }

    private record BenchmarkDataset(String name, List<SourceUnit> units) {
        private long totalBytes() {
            long total = 0L;
            for (SourceUnit unit : units) {
                total += unit.bytes();
            }
            return total;
        }
    }

    private record BenchmarkOptions(
            int warmupIterations,
            int measureIterations,
            int repeatPerIteration,
            int slowestFiles,
            Set<ParseMode> modes,
            List<Path> inputs
    ) {
    }

    private record BenchmarkResult(
            ParseMode mode,
            String datasetName,
            long bytesPerIteration,
            int parsesPerIteration,
            List<Long> iterationNanos,
            Map<Path, FileTiming> perFileTiming
    ) {
        private double operationsPerSecond() {
            double meanNanos = meanNanos(iterationNanos);
            if (meanNanos <= 0.0)
                return 0.0;
            return parsesPerIteration / (meanNanos / 1_000_000_000.0);
        }

        private double mebibytesPerSecond() {
            double meanNanos = meanNanos(iterationNanos);
            if (meanNanos <= 0.0)
                return 0.0;
            double seconds = meanNanos / 1_000_000_000.0;
            return bytesToMiB(bytesPerIteration) / seconds;
        }
    }

    private static final class MutableFileTiming {
        private long totalNanos;
        private long maxNanos;
        private long samples;

        void record(long nanos) {
            totalNanos += nanos;
            maxNanos = Math.max(maxNanos, nanos);
            samples++;
        }

        FileTiming freeze() {
            long average = samples == 0 ? 0L : totalNanos / samples;
            return new FileTiming(average, maxNanos, samples);
        }
    }

    private record FileTiming(long averageNanos, long maxNanos, long samples) {
    }

    @FunctionalInterface
    private interface ParseStrategy {
        void parse(SourceUnit unit);
    }
}
