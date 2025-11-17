package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Fluent builder for configuring and running the {@code jstat} diagnostic utility shipped with a {@link JDK}.
 * <p>
 * Provides helpers for selecting general options, stat counters, VM identifiers, and sampling intervals
 * before executing {@code jstat} via {@link ProcessExecution}.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jstat.html">jstat command documentation</a>
 */
public class JstatCLIBuilder implements CLIBuilder<Process, JstatCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jstat.exe" : "jstat";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private String generalOption;
    private String statOption;
    private String vmId;
    private String intervalExpression;
    private Integer sampleCount;

    private JstatCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Instantiates a builder bound to the given {@link JDK}.
     *
     * @param jdk the JDK that provides {@code jstat}
     * @return a new builder instance
     */
    public static JstatCLIBuilder create(JDK jdk) {
        return new JstatCLIBuilder(jdk);
    }

    @Override
    public JstatCLIBuilder addArgument(String arg) {
        ensureGeneralOptionNotSet();
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JstatCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JstatCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JstatCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JstatCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Requests {@code -help} output from {@code jstat}.
     *
     * @return this builder
     */
    public JstatCLIBuilder help() {
        selectGeneralOption("-help");
        return this;
    }

    /**
     * Adds {@code -options} to list available statistics.
     *
     * @return this builder
     */
    public JstatCLIBuilder listStatOptions() {
        selectGeneralOption("-options");
        return this;
    }

    /**
     * Specifies a pre-defined stat option.
     *
     * @param option stat option
     * @return this builder
     */
    public JstatCLIBuilder statOption(StatOption option) {
        Objects.requireNonNull(option, "Stat option cannot be null");
        return statOption(option.getFlag());
    }

    /**
     * Specifies a custom stat option flag.
     *
     * @param option stat option flag
     * @return this builder
     */
    public JstatCLIBuilder statOption(String option) {
        ensureGeneralOptionNotSet();
        Objects.requireNonNull(option, "Stat option cannot be null");
        if (option.isBlank())
            throw new IllegalArgumentException("Stat option cannot be blank");

        this.statOption = option.startsWith("-") ? option : "-" + option;
        return this;
    }

    /**
     * Requests timestamps via {@code -t}.
     *
     * @return this builder
     */
    public JstatCLIBuilder showTimestamp() {
        ensureGeneralOptionNotSet();
        this.arguments.add("-t");
        return this;
    }

    /**
     * Inserts {@code -h <lines>} to repeat headers.
     *
     * @param lines header frequency
     * @return this builder
     */
    public JstatCLIBuilder headerEvery(int lines) {
        ensureGeneralOptionNotSet();
        if (lines < 0)
            throw new IllegalArgumentException("Header frequency must be zero or greater");

        this.arguments.add("-h");
        this.arguments.add(Integer.toString(lines));
        return this;
    }

    /**
     * Adds a JVM argument with {@code -J}.
     *
     * @param option JVM option
     * @return this builder
     */
    public JstatCLIBuilder javaOption(String option) {
        ensureGeneralOptionNotSet();
        Objects.requireNonNull(option, "Java option cannot be null");
        if (option.isBlank())
            throw new IllegalArgumentException("Java option cannot be blank");

        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Adds multiple JVM options.
     *
     * @param options JVM options
     * @return this builder
     */
    public JstatCLIBuilder javaOptions(String... options) {
        Objects.requireNonNull(options, "Java options cannot be null");
        for (String option : options) {
            javaOption(option);
        }

        return this;
    }

    /**
     * Targets a VM using its numeric PID.
     *
     * @param pid process identifier
     * @return this builder
     */
    public JstatCLIBuilder vmId(long pid) {
        ensureGeneralOptionNotSet();
        if (pid <= 0)
            throw new IllegalArgumentException("PID must be positive");

        this.vmId = Long.toString(pid);
        return this;
    }

    /**
     * Targets a VM using a string identifier.
     *
     * @param vmIdentifier VM identifier
     * @return this builder
     */
    public JstatCLIBuilder vmId(String vmIdentifier) {
        ensureGeneralOptionNotSet();
        Objects.requireNonNull(vmIdentifier, "VM identifier cannot be null");
        if (vmIdentifier.isBlank())
            throw new IllegalArgumentException("VM identifier cannot be blank");

        this.vmId = vmIdentifier;
        return this;
    }

    /**
     * Sets the sampling interval via {@code <value><unit>}.
     *
     * @param interval interval value
     * @param unit     time unit
     * @return this builder
     */
    public JstatCLIBuilder samplingInterval(long interval, TimeUnit unit) {
        ensureGeneralOptionNotSet();
        if (interval <= 0)
            throw new IllegalArgumentException("Interval must be positive");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.intervalExpression = formatInterval(interval, unit);
        return this;
    }

    /**
     * Configures the number of samples to collect.
     *
     * @param count sample count
     * @return this builder
     */
    public JstatCLIBuilder sampleCount(int count) {
        ensureGeneralOptionNotSet();
        if (count <= 0)
            throw new IllegalArgumentException("Sample count must be positive");

        this.sampleCount = count;
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());

        if (generalOption != null) {
            command.add(generalOption);
        } else {
            if (statOption == null)
                throw new IllegalStateException("A stat option must be specified when not using a general option.");
            if (vmId == null)
                throw new IllegalStateException("A VM identifier must be specified when using jstat output options.");
            if (sampleCount != null && intervalExpression == null)
                throw new IllegalStateException("A sampling interval must be specified before setting a sample count.");

            command.add(statOption);
            command.addAll(arguments);
            command.add(vmId);

            if (intervalExpression != null) {
                command.add(intervalExpression);
                if (sampleCount != null)
                    command.add(Integer.toString(sampleCount));
            }
        }

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jstat");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jstat process", exception);
        }
    }

    private void selectGeneralOption(String optionFlag) {
        if (this.generalOption != null)
            throw new IllegalStateException("Only one jstat general option can be specified.");

        if (statOption != null || vmId != null || intervalExpression != null || sampleCount != null || !arguments.isEmpty())
            throw new IllegalStateException("General options cannot be combined with other jstat configuration.");

        this.generalOption = optionFlag;
    }

    private void ensureGeneralOptionNotSet() {
        if (generalOption != null)
            throw new IllegalStateException("Cannot combine general jstat options with other configuration.");
    }

    private static String formatInterval(long interval, TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval + "ms";
            case SECONDS -> interval + "s";
            default -> throw new IllegalArgumentException("jstat intervals support only seconds or milliseconds");
        };
    }

    public enum StatOption {
        CLASS("class"),
        COMPILER("compiler"),
        GC("gc"),
        GC_CAPACITY("gccapacity"),
        GC_CAUSE("gccause"),
        GC_NEW("gcnew"),
        GC_NEW_CAPACITY("gcnewcapacity"),
        GC_OLD("gcold"),
        GC_OLD_CAPACITY("gcoldcapacity"),
        GC_META_CAPACITY("gcmetacapacity"),
        GC_UTIL("gcutil"),
        PRINT_COMPILATION("printcompilation");

        private final String flag;

        StatOption(String flag) {
            this.flag = flag;
        }

        public String getFlag() {
            return "-" + flag;
        }
    }
}
