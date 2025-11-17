package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JfrCLIBuilder implements CLIBuilder<Process, JfrCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jfr.exe" : "jfr";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private Subcommand subcommand;

    private JfrCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JfrCLIBuilder create(JDK jdk) {
        return new JfrCLIBuilder(jdk);
    }

    @Override
    public JfrCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JfrCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JfrCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JfrCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JfrCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JfrCLIBuilder print(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.PRINT;
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder view(String viewName, Path file) {
        Objects.requireNonNull(viewName, "View name cannot be null");
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.VIEW;
        this.arguments.add(viewName);
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder configure() {
        this.subcommand = Subcommand.CONFIGURE;
        return this;
    }

    public JfrCLIBuilder metadata(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.METADATA;
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder metadata() {
        this.subcommand = Subcommand.METADATA;
        return this;
    }

    public JfrCLIBuilder summary(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.SUMMARY;
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder scrub(Path inputFile, Path outputFile) {
        Objects.requireNonNull(inputFile, "Input file cannot be null");
        Objects.requireNonNull(outputFile, "Output file cannot be null");
        this.subcommand = Subcommand.SCRUB;
        this.arguments.add(inputFile.toString());
        this.arguments.add(outputFile.toString());
        return this;
    }

    public JfrCLIBuilder scrub(Path inputFile) {
        Objects.requireNonNull(inputFile, "Input file cannot be null");
        this.subcommand = Subcommand.SCRUB;
        this.arguments.add(inputFile.toString());
        return this;
    }

    public JfrCLIBuilder assemble(Path repository, Path file) {
        Objects.requireNonNull(repository, "Repository cannot be null");
        Objects.requireNonNull(file, "Output file cannot be null");
        this.subcommand = Subcommand.ASSEMBLE;
        this.arguments.add(repository.toString());
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder disassemble(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.DISASSEMBLE;
        this.arguments.add(file.toString());
        return this;
    }

    public JfrCLIBuilder printXml() {
        this.arguments.add("--xml");
        return this;
    }

    public JfrCLIBuilder printJson() {
        this.arguments.add("--json");
        return this;
    }

    public JfrCLIBuilder printExact() {
        this.arguments.add("--exact");
        return this;
    }

    public JfrCLIBuilder categoriesFilter(String filter) {
        Objects.requireNonNull(filter, "Category filter cannot be null");
        this.arguments.add("--categories " + filter);
        return this;
    }

    public JfrCLIBuilder eventFilter(String filter) {
        Objects.requireNonNull(filter, "Event filter cannot be null");
        this.arguments.add("--events " + filter);
        return this;
    }

    public JfrCLIBuilder stackDepth(int depth) {
        if (depth < 0)
            throw new IllegalArgumentException("Stack depth cannot be negative");

        this.arguments.add("--stack-depth " + depth);
        return this;
    }

    public JfrCLIBuilder viewVerbose() {
        this.arguments.add("--verbose");
        return this;
    }

    public JfrCLIBuilder viewWidth(int width) {
        this.arguments.add("--width " + width);
        return this;
    }

    public JfrCLIBuilder viewTruncateMode(String mode) {
        Objects.requireNonNull(mode, "Truncate mode cannot be null");
        this.arguments.add("--truncate " + mode);
        return this;
    }

    public JfrCLIBuilder viewCellHeight(int height) {
        this.arguments.add("--cell-height " + height);
        return this;
    }

    public JfrCLIBuilder configureInteractive() {
        this.arguments.add("--interactive");
        return this;
    }

    public JfrCLIBuilder configureVerbose() {
        this.arguments.add("--verbose");
        return this;
    }

    public JfrCLIBuilder configureInput(String files) {
        Objects.requireNonNull(files, "Input files cannot be null");
        this.arguments.add("--input " + files);
        return this;
    }

    public JfrCLIBuilder configureOutput(Path file) {
        Objects.requireNonNull(file, "Output file cannot be null");
        this.arguments.add("--output " + file);
        return this;
    }

    public JfrCLIBuilder configureOption(String option, String value) {
        Objects.requireNonNull(option, "Option cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        this.arguments.add(option + "=" + value);
        return this;
    }

    public JfrCLIBuilder configureEventSetting(String setting, String value) {
        Objects.requireNonNull(setting, "Setting cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        this.arguments.add(setting + "=" + value);
        return this;
    }

    public JfrCLIBuilder metadataCategoryFilter(String filter) {
        Objects.requireNonNull(filter, "Category filter cannot be null");
        this.arguments.add("--categories " + filter);
        return this;
    }

    public JfrCLIBuilder metadataEventFilter(String filter) {
        Objects.requireNonNull(filter, "Event filter cannot be null");
        this.arguments.add("--events " + filter);
        return this;
    }

    public JfrCLIBuilder scrubIncludeEvents(String filter) {
        Objects.requireNonNull(filter, "Include events filter cannot be null");
        this.arguments.add("--include-events " + filter);
        return this;
    }

    public JfrCLIBuilder scrubExcludeEvents(String filter) {
        Objects.requireNonNull(filter, "Exclude events filter cannot be null");
        this.arguments.add("--exclude-events " + filter);
        return this;
    }

    public JfrCLIBuilder scrubIncludeCategories(String filter) {
        Objects.requireNonNull(filter, "Include categories filter cannot be null");
        this.arguments.add("--include-categories " + filter);
        return this;
    }

    public JfrCLIBuilder scrubExcludeCategories(String filter) {
        Objects.requireNonNull(filter, "Exclude categories filter cannot be null");
        this.arguments.add("--exclude-categories " + filter);
        return this;
    }

    public JfrCLIBuilder scrubIncludeThreads(String filter) {
        Objects.requireNonNull(filter, "Include threads filter cannot be null");
        this.arguments.add("--include-threads " + filter);
        return this;
    }

    public JfrCLIBuilder scrubExcludeThreads(String filter) {
        Objects.requireNonNull(filter, "Exclude threads filter cannot be null");
        this.arguments.add("--exclude-threads " + filter);
        return this;
    }

    public JfrCLIBuilder disassembleOutput(Path directory) {
        Objects.requireNonNull(directory, "Output directory cannot be null");
        this.arguments.add("--output " + directory);
        return this;
    }

    public JfrCLIBuilder disassembleMaxChunks(int chunks) {
        if (chunks <= 0)
            throw new IllegalArgumentException("Max chunks must be positive");

        this.arguments.add("--max-chunks " + chunks);
        return this;
    }

    public JfrCLIBuilder disassembleMaxSize(String size) {
        Objects.requireNonNull(size, "Max size cannot be null");
        this.arguments.add("--max-size " + size);
        return this;
    }

    public JfrCLIBuilder version() {
        this.subcommand = Subcommand.VERSION;
        return this;
    }

    public JfrCLIBuilder help() {
        this.subcommand = Subcommand.HELP;
        return this;
    }

    public JfrCLIBuilder help(String subcommand) {
        Objects.requireNonNull(subcommand, "Subcommand cannot be null");
        this.subcommand = Subcommand.HELP;
        this.arguments.add(subcommand);
        return this;
    }

    @Override
    public Process run() {
        if (subcommand == null)
            throw new IllegalStateException("A jfr subcommand must be selected.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.add(subcommand.command());
        command.addAll(arguments);

        var processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        if (useSystemEnvVars) {
            Map<String, String> env = processBuilder.environment();
            env.putAll(environmentVariables);
        } else {
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environmentVariables);
        }

        try {
            Process process = processBuilder.start();
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jfr");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jfr process", exception);
        }
    }

    private enum Subcommand {
        PRINT("print"),
        VIEW("view"),
        CONFIGURE("configure"),
        METADATA("metadata"),
        SUMMARY("summary"),
        SCRUB("scrub"),
        ASSEMBLE("assemble"),
        DISASSEMBLE("disassemble"),
        VERSION("version"),
        HELP("--help");

        private final String command;

        Subcommand(String command) {
            this.command = command;
        }

        public String command() {
            return command;
        }
    }
}
