package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.ProjectDiagnosticsFeatureFactory;
import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Runs registered language diagnostics over every source file in a project.
 */
public final class ProjectDiagnosticsScanner {
    private ProjectDiagnosticsScanner() {
    }

    public static Path scan(Project project) {
        Objects.requireNonNull(project, "project");

        ProjectDiagnosticsContext diagnosticsContext = ProjectDiagnosticsContext.create(project);
        Path reportPath = diagnosticsContext.indexContext().projectRoot()
            .resolve(".railroad")
            .resolve("diagnostics")
            .resolve("project-diagnostics.txt");
        return scan(diagnosticsContext, reportPath);
    }

    public static Path scan(Project project, Path reportPath) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(reportPath, "reportPath");
        return scan(ProjectDiagnosticsContext.create(project), reportPath);
    }

    private static Path scan(ProjectDiagnosticsContext diagnosticsContext, Path reportPath) {
        Project project = diagnosticsContext.project();
        ProjectIndexContext context = diagnosticsContext.indexContext();
        List<FileScanResult> fileResults = new ArrayList<>();
        Map<String, Integer> countsByCode = new TreeMap<>();
        Map<Diagnostic.Kind, Integer> countsByKind = new LinkedHashMap<>();
        List<ScanTarget> targets = scanTargets(context);
        long scanStartedAt = System.nanoTime();
        int parallelism = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<FileScanResult> completionService = new ExecutorCompletionService<>(executor);

        Railroad.LOGGER.warn(
            "Starting project diagnostics scan for {}: {} files using {} workers",
            context.projectRoot(),
            targets.size(),
            parallelism);
        try {
            for (ScanTarget target : targets) {
                completionService.submit(() -> scanOne(diagnosticsContext, target));
            }

            int scannedFiles = 0;
            for (int index = 0; index < targets.size(); index++) {
                FileScanResult result = completionService.take().get();
                fileResults.add(result);
                scannedFiles++;

                if (result.failure() != null) {
                    Railroad.LOGGER.warn("Failed to scan diagnostics for {}", result.path(), result.failure());
                } else if (!result.diagnostics().isEmpty()) {
                    result.diagnostics().forEach(diagnostic -> {
                        String code = diagnostic.getCode() == null ? "<none>" : diagnostic.getCode();
                        countsByCode.merge(code, 1, Integer::sum);
                        countsByKind.merge(diagnostic.getKind(), 1, Integer::sum);
                    });
                }

                logProgress(
                    context.projectRoot(),
                    result.path(),
                    scannedFiles,
                    targets.size(),
                    result.diagnostics().size(),
                    result.startedAtNanos(),
                    scanStartedAt);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Project diagnostics scan was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Project diagnostics scan failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }

        fileResults.sort(Comparator.comparing(file -> file.path().toString()));
        List<FileDiagnostics> files = fileResults.stream()
            .filter(result -> result.failure() == null && !result.diagnostics().isEmpty())
            .map(result -> new FileDiagnostics(result.path(), result.languageId(), result.diagnostics()))
            .toList();

        String report = renderReport(context, targets.size(), files, countsByKind, countsByCode);
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write project diagnostics report to " + reportPath, exception);
        }

        int diagnosticCount = countsByCode.values().stream().mapToInt(Integer::intValue).sum();
        Railroad.LOGGER.warn(
            "Project diagnostics scan complete: files={}, affectedFiles={}, diagnostics={}, report={}",
            targets.size(),
            files.size(),
            diagnosticCount,
            reportPath);
        return reportPath;
    }

    private static FileScanResult scanOne(ProjectDiagnosticsContext diagnosticsContext, ScanTarget target) {
        long fileStartedAt = System.nanoTime();
        try {
            String source = Files.readString(target.path());
            DiagnosticsProvider provider = createDiagnosticsProvider(diagnosticsContext, target);
            List<EditorDiagnostic> diagnostics = provider.compute(source);
            return new FileScanResult(target.path(), target.support().languageId(), diagnostics, null, fileStartedAt);
        } catch (Exception | StackOverflowError exception) {
            return new FileScanResult(target.path(), target.support().languageId(), List.of(), exception,
                fileStartedAt);
        }
    }

    private static DiagnosticsProvider createDiagnosticsProvider(
        ProjectDiagnosticsContext diagnosticsContext,
        ScanTarget target
    ) {
        ProjectDiagnosticsFeatureFactory<DiagnosticsProvider> projectDiagnosticsFactory = target.support()
            .projectDiagnosticsFactory();
        if (projectDiagnosticsFactory != null)
            return projectDiagnosticsFactory.create(diagnosticsContext, target.path());

        return target.support().diagnosticsFactory().create(diagnosticsContext.project(), target.path());
    }

    private static List<ScanTarget> scanTargets(ProjectIndexContext context) {
        List<ScanTarget> targets = new ArrayList<>();
        for (LanguageSupport support : LanguageSupportRegistry.all()) {
            if (!support.supportsDiagnostics() || support.diagnosticsFactory() == null)
                continue;

            sourceFiles(context, support).stream()
                .map(path -> new ScanTarget(support, path))
                .forEach(targets::add);
        }
        targets.sort(Comparator.comparing(target -> target.path().toString()));
        return List.copyOf(targets);
    }

    private static void logProgress(
        Path projectRoot,
        Path sourceFile,
        int completed,
        int total,
        int diagnosticCount,
        long fileStartedAt,
        long scanStartedAt
    ) {
        long fileNanos = System.nanoTime() - fileStartedAt;
        long elapsedNanos = System.nanoTime() - scanStartedAt;
        long remainingNanos = completed == 0
            ? 0
            : Math.max(0, (elapsedNanos / completed) * (total - completed));
        double percentage = total == 0 ? 100.0 : completed * 100.0 / total;

        Railroad.LOGGER.warn(
            "Project diagnostics progress: {}/{} ({}%), file={}, diagnostics={}, fileTime={}, elapsed={}, eta={}",
            completed,
            total,
            String.format(Locale.ROOT, "%.1f", percentage),
            displayPath(projectRoot, sourceFile),
            diagnosticCount,
            formatDuration(fileNanos),
            formatDuration(elapsedNanos),
            formatDuration(remainingNanos));
    }

    private static String formatDuration(long nanos) {
        Duration duration = Duration.ofNanos(nanos);
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();
        if (hours > 0)
            return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
        if (minutes > 0)
            return "%dm %02ds".formatted(minutes, seconds);
        if (seconds > 0)
            return "%d.%03ds".formatted(seconds, millis);
        return "%dms".formatted(duration.toMillis());
    }

    private static List<Path> sourceFiles(ProjectIndexContext context, LanguageSupport support) {
        Set<Path> roots = new LinkedHashSet<>();
        LanguageIndexContext languageContext = context.language(support.languageId());
        if (languageContext == null) {
            roots.add(context.projectRoot());
        } else {
            roots.addAll(languageContext.sourceRoots());
            roots.addAll(languageContext.generatedRoots());
            if (roots.isEmpty()) {
                roots.add(context.projectRoot());
            }
        }

        Set<Path> files = new LinkedHashSet<>();
        for (Path root : roots) {
            if (root == null || Files.notExists(root))
                continue;
            if (Files.isRegularFile(root)) {
                if (support.supports(root)) {
                    files.add(root.toAbsolutePath().normalize());
                }
                continue;
            }

            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                    .filter(support::supports)
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(files::add);
            } catch (IOException exception) {
                Railroad.LOGGER.warn("Failed to enumerate diagnostic source root {}", root, exception);
            }
        }
        return files.stream().sorted().toList();
    }

    private static String renderReport(
        ProjectIndexContext context,
        int scannedFiles,
        List<FileDiagnostics> files,
        Map<Diagnostic.Kind, Integer> countsByKind,
        Map<String, Integer> countsByCode
    ) {
        var report = new StringBuilder();
        int diagnosticCount = countsByCode.values().stream().mapToInt(Integer::intValue).sum();
        report.append("Project: ").append(context.project().getAlias()).append('\n');
        report.append("Root: ").append(context.projectRoot()).append('\n');
        report.append("Generated: ").append(Instant.now()).append('\n');
        report.append("Scanned files: ").append(scannedFiles).append('\n');
        report.append("Affected files: ").append(files.size()).append('\n');
        report.append("Diagnostics: ").append(diagnosticCount).append('\n');
        countsByKind.forEach((kind, count) -> report.append("  ").append(kind).append(": ").append(count).append('\n'));
        report.append("\nBy code:\n");
        countsByCode.forEach((code, count) -> report.append("  ").append(code).append(": ").append(count).append('\n'));

        for (FileDiagnostics file : files) {
            report.append("\n")
                .append(displayPath(context.projectRoot(), file.path()))
                .append(" [").append(file.languageId()).append("] (")
                .append(file.diagnostics().size()).append(")\n");
            for (EditorDiagnostic diagnostic : file.diagnostics()) {
                report.append("  [").append(diagnostic.getKind()).append("] ")
                    .append("line ").append(diagnostic.getLineNumber())
                    .append(", column ").append(diagnostic.getColumnNumber())
                    .append(", offsets ").append(diagnostic.getStartPosition())
                    .append("-").append(diagnostic.getEndPosition())
                    .append(", ").append(diagnostic.getCode())
                    .append(": ").append(diagnostic.getMessage(null))
                    .append('\n');
            }
        }
        return report.toString();
    }

    private static Path displayPath(Path projectRoot, Path file) {
        try {
            return projectRoot.relativize(file);
        } catch (IllegalArgumentException exception) {
            return file;
        }
    }

    private record FileDiagnostics(Path path, String languageId, List<EditorDiagnostic> diagnostics) {
        private FileDiagnostics {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record FileScanResult(
        Path path,
        String languageId,
        List<EditorDiagnostic> diagnostics,
        Throwable failure,
        long startedAtNanos) {
        private FileScanResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record ScanTarget(LanguageSupport support, Path path) {
    }
}
