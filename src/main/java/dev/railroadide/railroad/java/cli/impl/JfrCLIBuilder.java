package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for constructing and executing {@code jfr} commands.
 * <p>
 * This builder provides methods to interact with Java Flight Recorder (JFR) recordings,
 * allowing for printing, viewing, configuring, and analyzing JFR data.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html">jfr command documentation</a>
 */
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

    /**
     * Creates a new {@code JfrCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jfr} command.
     * @return A new builder instance.
     */
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

    /**
     * Adds a "print" subcommand to the JFR CLI with the specified recording file.
     *
     * @param file the recording file to print; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JfrCLIBuilder print(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.PRINT;
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds a "view" subcommand to the JFR CLI with the specified view name and recording file.
     *
     * @param viewName the name of the view; must not be null
     * @param file     the recording file to view; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the view name or file is null
     */
    public JfrCLIBuilder view(String viewName, Path file) {
        Objects.requireNonNull(viewName, "View name cannot be null");
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.VIEW;
        this.arguments.add(viewName);
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds a "configure" subcommand to the JFR CLI.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder configure() {
        this.subcommand = Subcommand.CONFIGURE;
        return this;
    }

    /**
     * Adds a "metadata" subcommand to the JFR CLI with the specified recording file.
     *
     * @param file the recording file for metadata; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JfrCLIBuilder metadata(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.METADATA;
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds a "metadata" subcommand to the JFR CLI without specifying a file.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder metadata() {
        this.subcommand = Subcommand.METADATA;
        return this;
    }

    /**
     * Adds a "summary" subcommand to the JFR CLI with the specified recording file.
     *
     * @param file the recording file for the summary; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JfrCLIBuilder summary(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.SUMMARY;
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds a "scrub" subcommand to the JFR CLI with the specified input and output files.
     *
     * @param inputFile  the input file to scrub; must not be null
     * @param outputFile the output file to save the scrubbed data; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the input or output file is null
     */
    public JfrCLIBuilder scrub(Path inputFile, Path outputFile) {
        Objects.requireNonNull(inputFile, "Input file cannot be null");
        Objects.requireNonNull(outputFile, "Output file cannot be null");
        this.subcommand = Subcommand.SCRUB;
        this.arguments.add(inputFile.toString());
        this.arguments.add(outputFile.toString());
        return this;
    }

    /**
     * Adds a "scrub" subcommand to the JFR CLI with the specified input file.
     *
     * @param inputFile the input file to scrub; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the input file is null
     */
    public JfrCLIBuilder scrub(Path inputFile) {
        Objects.requireNonNull(inputFile, "Input file cannot be null");
        this.subcommand = Subcommand.SCRUB;
        this.arguments.add(inputFile.toString());
        return this;
    }

    /**
     * Adds an "assemble" subcommand to the JFR CLI with the specified repository and output file.
     *
     * @param repository the repository path; must not be null
     * @param file       the output file to save the assembled data; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the repository or output file is null
     */
    public JfrCLIBuilder assemble(Path repository, Path file) {
        Objects.requireNonNull(repository, "Repository cannot be null");
        Objects.requireNonNull(file, "Output file cannot be null");
        this.subcommand = Subcommand.ASSEMBLE;
        this.arguments.add(repository.toString());
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds a "disassemble" subcommand to the JFR CLI with the specified recording file.
     *
     * @param file the recording file to disassemble; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JfrCLIBuilder disassemble(Path file) {
        Objects.requireNonNull(file, "Recording file cannot be null");
        this.subcommand = Subcommand.DISASSEMBLE;
        this.arguments.add(file.toString());
        return this;
    }

    /**
     * Adds the "--xml" option to the current subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder printXml() {
        this.arguments.add("--xml");
        return this;
    }

    /**
     * Adds the "--json" option to the current subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder printJson() {
        this.arguments.add("--json");
        return this;
    }

    /**
     * Adds the "--exact" option to the current subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder printExact() {
        this.arguments.add("--exact");
        return this;
    }

    /**
     * Adds a category filter to the current subcommand.
     *
     * @param filter the category filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder categoriesFilter(String filter) {
        Objects.requireNonNull(filter, "Category filter cannot be null");
        this.arguments.add("--categories " + filter);
        return this;
    }

    /**
     * Adds an event filter to the current subcommand.
     *
     * @param filter the event filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder eventFilter(String filter) {
        Objects.requireNonNull(filter, "Event filter cannot be null");
        this.arguments.add("--events " + filter);
        return this;
    }

    /**
     * Sets the stack depth for the current subcommand.
     *
     * @param depth the stack depth; must be non-negative
     * @return the current `JfrCLIBuilder` instance
     * @throws IllegalArgumentException if the depth is negative
     */
    public JfrCLIBuilder stackDepth(int depth) {
        if (depth < 0)
            throw new IllegalArgumentException("Stack depth cannot be negative");

        this.arguments.add("--stack-depth " + depth);
        return this;
    }

    /**
     * Adds the "--verbose" option to the "view" subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder viewVerbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Sets the width for the "view" subcommand.
     *
     * @param width the width value
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder viewWidth(int width) {
        this.arguments.add("--width " + width);
        return this;
    }

    /**
     * Sets the truncate mode for the "view" subcommand.
     *
     * @param mode the truncate mode; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the mode is null
     */
    public JfrCLIBuilder viewTruncateMode(String mode) {
        Objects.requireNonNull(mode, "Truncate mode cannot be null");
        this.arguments.add("--truncate " + mode);
        return this;
    }

    /**
     * Sets the cell height for the "view" subcommand.
     *
     * @param height the cell height value
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder viewCellHeight(int height) {
        this.arguments.add("--cell-height " + height);
        return this;
    }

    /**
     * Adds the "--interactive" option to the "configure" subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder configureInteractive() {
        this.arguments.add("--interactive");
        return this;
    }

    /**
     * Adds the "--verbose" option to the "configure" subcommand.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder configureVerbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Sets the input files for the "configure" subcommand.
     *
     * @param files the input files; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the files are null
     */
    public JfrCLIBuilder configureInput(String files) {
        Objects.requireNonNull(files, "Input files cannot be null");
        this.arguments.add("--input " + files);
        return this;
    }

    /**
     * Sets the output file for the "configure" subcommand.
     *
     * @param file the output file; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JfrCLIBuilder configureOutput(Path file) {
        Objects.requireNonNull(file, "Output file cannot be null");
        this.arguments.add("--output " + file);
        return this;
    }

    /**
     * Adds an option and its value to the "configure" subcommand.
     *
     * @param option the option name; must not be null
     * @param value  the option value; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the option or value is null
     */
    public JfrCLIBuilder configureOption(String option, String value) {
        Objects.requireNonNull(option, "Option cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        this.arguments.add(option + "=" + value);
        return this;
    }

    /**
     * Adds an event setting and its value to the "configure" subcommand.
     *
     * @param setting the event setting name; must not be null
     * @param value   the event setting value; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the setting or value is null
     */
    public JfrCLIBuilder configureEventSetting(String setting, String value) {
        Objects.requireNonNull(setting, "Setting cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        this.arguments.add(setting + "=" + value);
        return this;
    }

    /**
     * Adds a category filter to the "metadata" subcommand.
     *
     * @param filter the category filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder metadataCategoryFilter(String filter) {
        Objects.requireNonNull(filter, "Category filter cannot be null");
        this.arguments.add("--categories " + filter);
        return this;
    }

    /**
     * Adds an event filter to the "metadata" subcommand.
     *
     * @param filter the event filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder metadataEventFilter(String filter) {
        Objects.requireNonNull(filter, "Event filter cannot be null");
        this.arguments.add("--events " + filter);
        return this;
    }

    /**
     * Adds an include events filter to the "scrub" subcommand.
     *
     * @param filter the include events filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubIncludeEvents(String filter) {
        Objects.requireNonNull(filter, "Include events filter cannot be null");
        this.arguments.add("--include-events " + filter);
        return this;
    }

    /**
     * Adds an exclude events filter to the "scrub" subcommand.
     *
     * @param filter the exclude events filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubExcludeEvents(String filter) {
        Objects.requireNonNull(filter, "Exclude events filter cannot be null");
        this.arguments.add("--exclude-events " + filter);
        return this;
    }

    /**
     * Adds an include categories filter to the "scrub" subcommand.
     *
     * @param filter the include categories filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubIncludeCategories(String filter) {
        Objects.requireNonNull(filter, "Include categories filter cannot be null");
        this.arguments.add("--include-categories " + filter);
        return this;
    }

    /**
     * Adds an exclude categories filter to the "scrub" subcommand.
     *
     * @param filter the exclude categories filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubExcludeCategories(String filter) {
        Objects.requireNonNull(filter, "Exclude categories filter cannot be null");
        this.arguments.add("--exclude-categories " + filter);
        return this;
    }

    /**
     * Adds an include threads filter to the "scrub" subcommand.
     *
     * @param filter the include threads filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubIncludeThreads(String filter) {
        Objects.requireNonNull(filter, "Include threads filter cannot be null");
        this.arguments.add("--include-threads " + filter);
        return this;
    }

    /**
     * Adds an exclude threads filter to the "scrub" subcommand.
     *
     * @param filter the exclude threads filter; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the filter is null
     */
    public JfrCLIBuilder scrubExcludeThreads(String filter) {
        Objects.requireNonNull(filter, "Exclude threads filter cannot be null");
        this.arguments.add("--exclude-threads " + filter);
        return this;
    }

    /**
     * Sets the output directory for the "disassemble" subcommand.
     *
     * @param directory the output directory; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the directory is null
     */
    public JfrCLIBuilder disassembleOutput(Path directory) {
        Objects.requireNonNull(directory, "Output directory cannot be null");
        this.arguments.add("--output " + directory);
        return this;
    }

    /**
     * Sets the maximum number of chunks for the "disassemble" subcommand.
     *
     * @param chunks the maximum number of chunks; must be positive
     * @return the current `JfrCLIBuilder` instance
     * @throws IllegalArgumentException if the chunks value is not positive
     */
    public JfrCLIBuilder disassembleMaxChunks(int chunks) {
        if (chunks <= 0)
            throw new IllegalArgumentException("Max chunks must be positive");

        this.arguments.add("--max-chunks " + chunks);
        return this;
    }

    /**
     * Sets the maximum size for the "disassemble" subcommand.
     *
     * @param size the maximum size; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the size is null
     */
    public JfrCLIBuilder disassembleMaxSize(String size) {
        Objects.requireNonNull(size, "Max size cannot be null");
        this.arguments.add("--max-size " + size);
        return this;
    }

    /**
     * Adds a "version" subcommand to the JFR CLI.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder version() {
        this.subcommand = Subcommand.VERSION;
        return this;
    }

    /**
     * Adds a "help" subcommand to the JFR CLI.
     *
     * @return the current `JfrCLIBuilder` instance
     */
    public JfrCLIBuilder help() {
        this.subcommand = Subcommand.HELP;
        return this;
    }

    /**
     * Adds a "help" subcommand to the JFR CLI with the specified subcommand.
     *
     * @param subcommand the subcommand to display help for; must not be null
     * @return the current `JfrCLIBuilder` instance
     * @throws NullPointerException if the subcommand is null
     */
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
