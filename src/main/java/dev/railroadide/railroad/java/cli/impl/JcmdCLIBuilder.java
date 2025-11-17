package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for constructing and executing {@code jcmd} commands.
 * <p>
 * This builder provides methods to interact with a running JVM, allowing for
 * diagnostic command execution, listing JVM processes, and retrieving help information.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html">jcmd command documentation</a>
 */
public class JcmdCLIBuilder implements CLIBuilder<Process, JcmdCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jcmd.exe" : "jcmd";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<List<String>> diagnosticCommands = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private Mode mode = Mode.COMMAND;
    private Target target = Target.none();
    private Path commandFile;

    private JcmdCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new {@code JcmdCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jcmd} command.
     * @return A new builder instance.
     */
    public static JcmdCLIBuilder create(JDK jdk) {
        return new JcmdCLIBuilder(jdk);
    }

    @Override
    public JcmdCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JcmdCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JcmdCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JcmdCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JcmdCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Configures the builder to list all running Java processes. Corresponds to the {@code -l} option.
     *
     * @return This builder instance.
     */
    public JcmdCLIBuilder listJavaProcesses() {
        this.mode = Mode.LIST;
        this.commandFile = null;
        this.diagnosticCommands.clear();
        return this;
    }

    /**
     * Configures the builder to display help information. Corresponds to the {@code -h} option.
     *
     * @return This builder instance.
     */
    public JcmdCLIBuilder showHelp() {
        this.mode = Mode.HELP;
        this.commandFile = null;
        this.diagnosticCommands.clear();
        return this;
    }

    /**
     * Sets the target JVM by its process ID (PID).
     *
     * @param pid The process ID of the target JVM.
     * @return This builder instance.
     * @throws IllegalArgumentException if the PID is negative.
     */
    public JcmdCLIBuilder targetPid(long pid) {
        if (pid < 0)
            throw new IllegalArgumentException("PID cannot be negative");

        this.target = Target.pid(Long.toString(pid));
        this.mode = Mode.COMMAND;
        return this;
    }

    /**
     * Sets the target JVM by its main class name.
     *
     * @param mainClass The main class name of the target JVM.
     * @return This builder instance.
     */
    public JcmdCLIBuilder targetMainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "Main class cannot be null");
        this.target = Target.mainClass(mainClass);
        this.mode = Mode.COMMAND;
        return this;
    }

    /**
     * Sets the target to all running Java processes. Corresponds to using "0" as the PID.
     *
     * @return This builder instance.
     */
    public JcmdCLIBuilder targetAllJavaProcesses() {
        this.target = Target.pid("0");
        this.mode = Mode.COMMAND;
        return this;
    }

    /**
     * Executes diagnostic commands from a specified file. Corresponds to the {@code -f} option.
     *
     * @param file The path to the command file.
     * @return This builder instance.
     */
    public JcmdCLIBuilder executeCommandsFromFile(Path file) {
        Objects.requireNonNull(file, "Command file cannot be null");
        this.commandFile = file;
        this.mode = Mode.COMMAND;
        return this;
    }

    /**
     * Adds a diagnostic command to be executed.
     *
     * @param command The name of the diagnostic command (e.g., "GC.heap_info").
     * @param args    Optional arguments for the command.
     * @return This builder instance.
     */
    public JcmdCLIBuilder addDiagnosticCommand(String command, String... args) {
        Objects.requireNonNull(command, "Command name cannot be null");
        var commandTokens = new ArrayList<String>();
        commandTokens.add(command);
        if (args != null) {
            commandTokens.addAll(Arrays.asList(args));
        }

        this.diagnosticCommands.add(commandTokens);
        this.mode = Mode.COMMAND;
        return this;
    }

    /**
     * Executes the "PerfCounter.print" diagnostic command.
     *
     * @return This builder instance.
     */
    public JcmdCLIBuilder perfCounterPrint() {
        return addDiagnosticCommand("PerfCounter.print");
    }

    @Override
    public Process run() {
        if (mode == Mode.COMMAND && commandFile == null && diagnosticCommands.isEmpty())
            throw new IllegalStateException("At least one diagnostic command or command file must be specified.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());

        switch (mode) {
            case HELP -> command.add("-h");
            case LIST -> command.add("-l");
            case COMMAND -> {
                if (target.value() != null)
                    command.add(target.value());
                if (commandFile != null) {
                    command.add("-f");
                    command.add(commandFile.toString());
                }
            }
        }

        command.addAll(arguments);
        if (mode == Mode.COMMAND && commandFile == null) {
            for (List<String> diagnosticCommand : diagnosticCommands) {
                command.addAll(diagnosticCommand);
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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jcmd");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jcmd process", exception);
        }
    }

    /**
     * Represents the operation mode for the {@code jcmd} command.
     */
    private enum Mode {
        COMMAND,
        LIST,
        HELP
    }

    /**
     * Represents the target JVM for the {@code jcmd} command.
     */
    private record Target(TargetType type, String value) {
        static Target none() {
            return new Target(TargetType.NONE, null);
        }

        static Target pid(String pid) {
            return new Target(TargetType.PID, pid);
        }

        static Target mainClass(String mainClass) {
            return new Target(TargetType.MAIN_CLASS, mainClass);
        }
    }

    /**
     * Represents the type of target for the {@code jcmd} command.
     */
    private enum TargetType {
        NONE,
        PID,
        MAIN_CLASS
    }
}
