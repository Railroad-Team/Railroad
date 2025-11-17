package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for constructing and executing {@code javadoc} commands.
 * <p>
 * This builder provides comprehensive options for generating API documentation,
 * including source path configuration, module-related options, doclet customization,
 * and various output formatting controls.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/javadoc.html">javadoc command documentation</a>
 */
public class JavadocCLIBuilder implements CLIBuilder<Process, JavadocCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "javadoc.exe" : "javadoc";
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> packageNames = new ArrayList<>();
    private final List<Path> sourceFilePaths = new ArrayList<>();
    private final List<Path> argumentFilePaths = new ArrayList<>();
    private Path workingDirectory;
    private final Map<String, String> environmentVariables = new HashMap<>();
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JavadocCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new {@code JavadocCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code javadoc} command.
     * @return A new builder instance.
     */
    public static JavadocCLIBuilder create(JDK jdk) {
        return new JavadocCLIBuilder(jdk);
    }

    @Override
    public JavadocCLIBuilder addArgument(String arg) {
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JavadocCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JavadocCLIBuilder setEnvironmentVariable(String key, String value) {
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JavadocCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JavadocCLIBuilder setTimeout(long duration, TimeUnit unit) {
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Adds a package name to be documented.
     *
     * @param packageName The name of the package.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addPackageName(String packageName) {
        Objects.requireNonNull(packageName, "Package name cannot be null");
        this.packageNames.add(packageName);
        return this;
    }

    /**
     * Adds a source file path to be documented.
     *
     * @param sourceFilePath The path to the source file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addSourceFilePath(Path sourceFilePath) {
        Objects.requireNonNull(sourceFilePath, "Source file path cannot be null");
        this.sourceFilePaths.add(sourceFilePath);
        return this;
    }

    /**
     * Adds an argument file path.
     *
     * @param argumentFilePath The path to the argument file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addArgumentFilePath(Path argumentFilePath) {
        Objects.requireNonNull(argumentFilePath, "Argument file path cannot be null");
        this.argumentFilePaths.add(argumentFilePath);
        return this;
    }

    /**
     * Adds modules to be documented. Corresponds to the {@code --add-modules} option.
     *
     * @param modules The names of the modules to add.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");

        var modulesBuilder = new StringBuilder("--add-modules ");
        for (String module : modules) {
            modulesBuilder.append(",").append(module);
        }

        this.arguments.add(modulesBuilder.toString());
        return this;
    }

    /**
     * Adds all modules on the module path to be documented. Corresponds to the {@code --add-modules ALL-MODULE-PATH} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder addAllModules() {
        return addModules("ALL-MODULE-PATH");
    }

    /**
     * Appends entries to the bootstrap class path. Corresponds to the {@code --boot-class-path} option.
     *
     * @param bootClassPathEntries The entries to append.
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is 9 or above.
     */
    public JavadocCLIBuilder appendBootClassPath(String... bootClassPathEntries) {
        if (jdk.version().major() >= 9)
            throw new UnsupportedOperationException("The --boot-class-path option is not supported in JDK 9 and above.");

        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("--boot-class-path " + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    /**
     * Specifies the classpath for finding user class files. Corresponds to the {@code -cp} or {@code -classpath} option.
     *
     * @param classpathEntries The entries for the classpath.
     * @return This builder instance.
     */
    public JavadocCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    /**
     * Enables preview language features. Corresponds to the {@code --enable-preview} option.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is less than 12.
     */
    public JavadocCLIBuilder enablePreviewFeatures() {
        if (jdk.version().major() < 12)
            throw new UnsupportedOperationException("Preview features are only supported in JDK 12 and above.");

        this.arguments.add("--enable-preview");
        return this;
    }

    /**
     * Specifies the source file encoding name. Corresponds to the {@code -encoding} option.
     *
     * @param encoding The encoding name.
     * @return This builder instance.
     */
    public JavadocCLIBuilder encoding(String encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        this.arguments.add("-encoding " + encoding);
        return this;
    }

    /**
     * Specifies the source file encoding name. Corresponds to the {@code -encoding} option.
     *
     * @param encoding The encoding charset.
     * @return This builder instance.
     */
    public JavadocCLIBuilder encoding(Charset encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        return encoding(encoding.name());
    }

    /**
     * Specifies the directories in which to search for installed extensions. Corresponds to the {@code -extdirs} option.
     *
     * @param dirs The extension directories.
     * @return This builder instance.
     */
    public JavadocCLIBuilder extDirs(String... dirs) {
        Objects.requireNonNull(dirs, "Ext dirs cannot be null");
        this.arguments.add("-extdirs " + String.join(File.pathSeparator, dirs));
        return this;
    }

    /**
     * Specifies the directories in which to search for installed extensions. Corresponds to the {@code -extdirs} option.
     *
     * @param dirs The extension directories.
     * @return This builder instance.
     */
    public JavadocCLIBuilder extDirs(Path... dirs) {
        Objects.requireNonNull(dirs, "Ext dirs cannot be null");
        String[] dirStrings = Arrays.stream(dirs).map(Path::toString).toArray(String[]::new);
        return extDirs(dirStrings);
    }

    /**
     * Disables the generation of line-number information in Javadoc comments. Corresponds to the {@code --disable-line-doc-comments} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder disableLineDocComments() {
        this.arguments.add("--disable-line-doc-comments");
        return this;
    }

    /**
     * Limits the universe of observable modules. Corresponds to the {@code --limit-modules} option.
     *
     * @param modules The modules to limit to.
     * @return This builder instance.
     */
    public JavadocCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Adds module names to be documented. Corresponds to the {@code -module} option.
     *
     * @param moduleName The names of the modules to add.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addModuleNames(String... moduleName) {
        Objects.requireNonNull(moduleName, "Module names cannot be null");
        this.arguments.add("-module " + String.join(",", moduleName));
        return this;
    }

    /**
     * Specifies the module path. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The entries for the module path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Specifies the module path. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The entries for the module path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    /**
     * Specifies the module source path. Corresponds to the {@code --module-source-path} option.
     *
     * @param moduleSourcePaths The entries for the module source path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder moduleSourcePath(String... moduleSourcePaths) {
        Objects.requireNonNull(moduleSourcePaths, "Module source path entries cannot be null");
        this.arguments.add("--module-source-path " + String.join(File.pathSeparator, moduleSourcePaths));
        return this;
    }

    /**
     * Specifies the module source path. Corresponds to the {@code --module-source-path} option.
     *
     * @param moduleSourcePaths The entries for the module source path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder moduleSourcePath(Path... moduleSourcePaths) {
        Objects.requireNonNull(moduleSourcePaths, "Module source path entries cannot be null");
        String[] pathStrings = Arrays.stream(moduleSourcePaths).map(Path::toString).toArray(String[]::new);
        return moduleSourcePath(pathStrings);
    }

    /**
     * Specifies the release version to use. Corresponds to the {@code --release} option.
     *
     * @param version The release version.
     * @return This builder instance.
     */
    public JavadocCLIBuilder releaseVersion(String version) {
        Objects.requireNonNull(version, "Release version cannot be null");
        this.arguments.add("--release " + version);
        return this;
    }

    /**
     * Specifies the release version to use. Corresponds to the {@code --release} option.
     *
     * @param version The release version.
     * @return This builder instance.
     */
    public JavadocCLIBuilder releaseVersion(int version) {
        return releaseVersion(Integer.toString(version));
    }

    /**
     * Specifies the source code version. Corresponds to the {@code --source} option.
     *
     * @param version The source version.
     * @return This builder instance.
     */
    public JavadocCLIBuilder sourceVersion(String version) {
        Objects.requireNonNull(version, "Source version cannot be null");
        this.arguments.add("--source " + version);
        return this;
    }

    /**
     * Specifies the source code version. Corresponds to the {@code --source} option.
     *
     * @param version The source version.
     * @return This builder instance.
     */
    public JavadocCLIBuilder sourceVersion(int version) {
        return sourceVersion(Integer.toString(version));
    }

    /**
     * Specifies the source code path. Corresponds to the {@code -sourcepath} option.
     *
     * @param sourcePaths The entries for the source path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder sourcepath(String... sourcePaths) {
        Objects.requireNonNull(sourcePaths, "Source path entries cannot be null");
        this.arguments.add("-sourcepath " + String.join(File.pathSeparator, sourcePaths));
        return this;
    }

    /**
     * Specifies the source code path. Corresponds to the {@code -sourcepath} option.
     *
     * @param sourcePaths The entries for the source path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder sourcepath(Path... sourcePaths) {
        Objects.requireNonNull(sourcePaths, "Source path entries cannot be null");
        String[] pathStrings = Arrays.stream(sourcePaths).map(Path::toString).toArray(String[]::new);
        return sourcepath(pathStrings);
    }

    /**
     * Specifies the system JDK to use. Corresponds to the {@code --system} option.
     *
     * @param systemJdk The system JDK.
     * @return This builder instance.
     */
    public JavadocCLIBuilder systemJdk(JDK systemJdk) {
        Objects.requireNonNull(systemJdk, "System JDK cannot be null");
        this.arguments.add("--system " + systemJdk.path().toString());
        return this;
    }

    /**
     * Specifies the upgrade module path. Corresponds to the {@code --upgrade-module-path} option.
     *
     * @param upgradeModulePaths The entries for the upgrade module path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder upgradeModulePath(String... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, upgradeModulePaths));
        return this;
    }

    /**
     * Specifies the upgrade module path. Corresponds to the {@code --upgrade-module-path} option.
     *
     * @param upgradeModulePaths The entries for the upgrade module path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder upgradeModulePath(Path... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        String[] pathStrings = Arrays.stream(upgradeModulePaths).map(Path::toString).toArray(String[]::new);
        return upgradeModulePath(pathStrings);
    }

    /**
     * Enables the use of {@code BreakIterator} for determining sentence breaks. Corresponds to the {@code -breakiterator} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableBreakIterator() {
        this.arguments.add("-breakiterator");
        return this;
    }

    /**
     * Specifies the class name of the doclet to use. Corresponds to the {@code -doclet} option.
     *
     * @param docletClassName The class name of the doclet.
     * @return This builder instance.
     */
    public JavadocCLIBuilder doclet(String docletClassName) {
        Objects.requireNonNull(docletClassName, "Doclet class name cannot be null");
        this.arguments.add("-doclet " + docletClassName);
        return this;
    }

    /**
     * Specifies the path where the doclet and its supporting classes are located. Corresponds to the {@code -docletpath} option.
     *
     * @param docletPaths The entries for the doclet path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder docletPath(String... docletPaths) {
        Objects.requireNonNull(docletPaths, "Doclet path entries cannot be null");
        this.arguments.add("-docletpath " + String.join(File.pathSeparator, docletPaths));
        return this;
    }

    /**
     * Specifies the path where the doclet and its supporting classes are located. Corresponds to the {@code -docletpath} option.
     *
     * @param docletPaths The entries for the doclet path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder docletPath(Path... docletPaths) {
        Objects.requireNonNull(docletPaths, "Doclet path entries cannot be null");
        String[] pathStrings = Arrays.stream(docletPaths).map(Path::toString).toArray(String[]::new);
        return docletPath(pathStrings);
    }

    /**
     * Excludes packages from the documentation. Corresponds to the {@code -exclude} option.
     *
     * @param packageNames The names of the packages to exclude.
     * @return This builder instance.
     */
    public JavadocCLIBuilder excludePackages(String... packageNames) {
        Objects.requireNonNull(packageNames, "Package names cannot be null");
        this.arguments.add("-exclude " + String.join(":", packageNames));
        return this;
    }

    /**
     * Expands the set of modules to be documented. Corresponds to the {@code --expand-requires} option.
     *
     * @param expansionType The type of expansion (TRANSITIVE or ALL).
     * @return This builder instance.
     */
    public JavadocCLIBuilder expandRequires(ExpansionType expansionType) {
        Objects.requireNonNull(expansionType, "Expansion type cannot be null");
        this.arguments.add("--expand-requires " + expansionType.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Displays the help message. Corresponds to the {@code -?} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder help() {
        this.arguments.add("-?");
        return this;
    }

    /**
     * Displays help on extra non-standard options. Corresponds to the {@code -X} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder extraHelp() {
        this.arguments.add("-X");
        return this;
    }

    /**
     * Passes an argument directly to the Java Virtual Machine. Corresponds to the {@code -J} option.
     *
     * @param jflag The argument to pass to the JVM.
     * @return This builder instance.
     */
    public JavadocCLIBuilder jflag(String jflag) {
        Objects.requireNonNull(jflag, "JFlag cannot be null");
        this.arguments.add("-J" + jflag);
        return this;
    }

    /**
     * Specifies the locale to be used when generating documentation. Corresponds to the {@code -locale} option.
     *
     * @param locale The locale string.
     * @return This builder instance.
     */
    public JavadocCLIBuilder locale(String locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        this.arguments.add("-locale " + locale);
        return this;
    }

    /**
     * Specifies the locale to be used when generating documentation. Corresponds to the {@code -locale} option.
     *
     * @param locale The locale object.
     * @return This builder instance.
     */
    public JavadocCLIBuilder locale(Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        return locale(locale.toLanguageTag());
    }

    /**
     * Sets the minimum visibility level of members to be documented. Corresponds to options like {@code -public}, {@code -protected}, {@code -package}, {@code -private}.
     *
     * @param visibility The minimum visibility level.
     * @return This builder instance.
     */
    public JavadocCLIBuilder visibility(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("-" + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Suppresses messages, leaving only warnings and errors. Corresponds to the {@code -quiet} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder quiet() {
        this.arguments.add("-quiet");
        return this;
    }

    /**
     * Shows members with a specified visibility. Corresponds to the {@code --show-members} option.
     *
     * @param visibility The visibility level.
     * @return This builder instance.
     */
    public JavadocCLIBuilder showMembers(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("--show-members " + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Shows module contents with a specified granularity. Corresponds to the {@code --show-module-contents} option.
     *
     * @param granularity The granularity level (API or ALL).
     * @return This builder instance.
     */
    public JavadocCLIBuilder showModuleContents(ModuleGranularity granularity) {
        Objects.requireNonNull(granularity, "Module granularity cannot be null");
        this.arguments.add("--show-module-contents " + granularity.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Shows packages with a specified granularity. Corresponds to the {@code --show-packages} option.
     *
     * @param granularity The granularity level (EXPORTED or ALL).
     * @return This builder instance.
     */
    public JavadocCLIBuilder showPackages(PackageGranularity granularity) {
        Objects.requireNonNull(granularity, "Package granularity cannot be null");
        this.arguments.add("--show-packages " + granularity.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Shows types with a specified visibility. Corresponds to the {@code --show-types} option.
     *
     * @param visibility The visibility level.
     * @return This builder instance.
     */
    public JavadocCLIBuilder showTypes(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("--show-types " + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Recursively retrieves source files from the specified subpackages. Corresponds to the {@code -subpackages} option.
     *
     * @param packageNames The names of the subpackages.
     * @return This builder instance.
     */
    public JavadocCLIBuilder subpackages(String... packageNames) {
        Objects.requireNonNull(packageNames, "Package names cannot be null");
        this.arguments.add("-subpackages " + String.join(":", packageNames));
        return this;
    }

    /**
     * Enables verbose output. Corresponds to the {@code -verbose} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    /**
     * Prints version information and exits. Corresponds to the {@code --version} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Reports warnings as errors. Corresponds to the {@code -Werror} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder reportErrorOnWarnings() {
        this.arguments.add("-Werror");
        return this;
    }

    /**
     * Adds a read edge from a source module to target modules. Corresponds to the {@code --add-reads} option.
     *
     * @param sourceModule  The source module.
     * @param targetModules The target modules.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addReads(String sourceModule, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-reads " + sourceModule + "=" + String.join(",", targetModules));
        return this;
    }

    /**
     * Adds a read edge from a source module to all unnamed modules.
     *
     * @param sourceModule The source module.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addReadsAllUnnamed(String sourceModule) {
        return addReads(sourceModule, "ALL-UNNAMED");
    }

    /**
     * Adds an export of a package from a source module to target modules. Corresponds to the {@code --add-exports} option.
     *
     * @param sourceModule  The source module.
     * @param packageName   The package to export.
     * @param targetModules The target modules.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addExports(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-exports " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    /**
     * Adds an export of a package from a source module to all unnamed modules.
     *
     * @param sourceModule The source module.
     * @param packageName  The package to export.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addExportsAllUnnamed(String sourceModule, String packageName) {
        return addExports(sourceModule, packageName, "ALL-UNNAMED");
    }

    /**
     * Patches a module with classes and resources from specified paths. Corresponds to the {@code --patch-module} option.
     *
     * @param moduleName The module to patch.
     * @param patchPaths The paths to the patch content.
     * @return This builder instance.
     */
    public JavadocCLIBuilder patchModule(String moduleName, String... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        this.arguments.add("--patch-module " + moduleName + "=" + String.join(File.pathSeparator, patchPaths));
        return this;
    }

    /**
     * Patches a module with classes and resources from specified paths. Corresponds to the {@code --patch-module} option.
     *
     * @param moduleName The module to patch.
     * @param patchPaths The paths to the patch content.
     * @return This builder instance.
     */
    public JavadocCLIBuilder patchModule(String moduleName, Path... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        String[] pathStrings = Arrays.stream(patchPaths).map(Path::toString).toArray(String[]::new);
        return patchModule(moduleName, pathStrings);
    }

    /**
     * Sets the maximum number of errors to report. Corresponds to the {@code -Xmaxerrs} option.
     *
     * @param maxErrors The maximum number of errors.
     * @return This builder instance.
     * @throws IllegalArgumentException if maxErrors is negative.
     */
    public JavadocCLIBuilder maxErrors(int maxErrors) {
        if (maxErrors < 0)
            throw new IllegalArgumentException("Max errors cannot be negative");

        this.arguments.add("-Xmaxerrs " + maxErrors);
        return this;
    }

    /**
     * Sets the maximum number of warnings to report. Corresponds to the {@code -Xmaxwarns} option.
     *
     * @param maxWarnings The maximum number of warnings.
     * @return This builder instance.
     * @throws IllegalArgumentException if maxWarnings is negative.
     */
    public JavadocCLIBuilder maxWarnings(int maxWarnings) {
        if (maxWarnings < 0)
            throw new IllegalArgumentException("Max warnings cannot be negative");

        this.arguments.add("-Xmaxwarns " + maxWarnings);
        return this;
    }

    /**
     * Adds a script to the generated documentation. Corresponds to the {@code --add-script} option.
     *
     * @param script The script to add.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addScript(String script) {
        Objects.requireNonNull(script, "Script cannot be null");
        this.arguments.add("--add-script " + script);
        return this;
    }

    /**
     * Adds a script to the generated documentation. Corresponds to the {@code --add-script} option.
     *
     * @param scriptFilePath The path to the script file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addScript(Path scriptFilePath) {
        Objects.requireNonNull(scriptFilePath, "Script file path cannot be null");
        return addScript(scriptFilePath.toString());
    }

    /**
     * Adds a stylesheet to the generated documentation. Corresponds to the {@code --add-stylesheet} option.
     *
     * @param styleSheet The stylesheet to add.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addStylesheet(String styleSheet) {
        Objects.requireNonNull(styleSheet, "Style sheet cannot be null");
        this.arguments.add("--add-stylesheet " + styleSheet);
        return this;
    }

    /**
     * Adds a stylesheet to the generated documentation. Corresponds to the {@code --add-stylesheet} option.
     *
     * @param styleSheetPath The path to the stylesheet file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder addStylesheet(Path styleSheetPath) {
        Objects.requireNonNull(styleSheetPath, "Style sheet path cannot be null");
        return addStylesheet(styleSheetPath.toString());
    }

    /**
     * Allows JavaScript in Javadoc comments. Corresponds to the {@code --allow-script-in-comments} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder allowScriptInComments() {
        this.arguments.add("--allow-script-in-comments");
        return this;
    }

    /**
     * Includes {@code @author} tags in the generated documentation. Corresponds to the {@code -author} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder includeAuthorTags() {
        this.arguments.add("-author");
        return this;
    }

    /**
     * Specifies the text to be placed at the bottom of each generated page. Corresponds to the {@code -bottom} option.
     *
     * @param text The text to place at the bottom.
     * @return This builder instance.
     */
    public JavadocCLIBuilder textAtBottom(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-bottom " + text);
        return this;
    }

    /**
     * Specifies the charset for the generated HTML files. Corresponds to the {@code -charset} option.
     *
     * @param charset The charset name.
     * @return This builder instance.
     */
    public JavadocCLIBuilder charset(String charset) {
        Objects.requireNonNull(charset, "Charset cannot be null");
        this.arguments.add("-charset " + charset);
        return this;
    }

    /**
     * Specifies the charset for the generated HTML files. Corresponds to the {@code -charset} option.
     *
     * @param charset The charset object.
     * @return This builder instance.
     */
    public JavadocCLIBuilder charset(Charset charset) {
        Objects.requireNonNull(charset, "Charset cannot be null");
        return charset(charset.name());
    }

    /**
     * Specifies the destination directory for the generated HTML files. Corresponds to the {@code -d} option.
     *
     * @param outputDirectory The output directory.
     * @return This builder instance.
     */
    public JavadocCLIBuilder destinationDirectory(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "Output directory cannot be null");
        this.arguments.add("-d " + outputDirectory);
        return this;
    }

    /**
     * Specifies the encoding for the generated HTML files. Corresponds to the {@code -docencoding} option.
     *
     * @param encoding The encoding name.
     * @return This builder instance.
     */
    public JavadocCLIBuilder generatedEncoding(String encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        this.arguments.add("-docencoding " + encoding);
        return this;
    }

    /**
     * Specifies the encoding for the generated HTML files. Corresponds to the {@code -docencoding} option.
     *
     * @param encoding The encoding charset.
     * @return This builder instance.
     */
    public JavadocCLIBuilder generatedEncoding(Charset encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        return generatedEncoding(encoding.name());
    }

    /**
     * Recursively copies doc-files subdirectories. Corresponds to the {@code -docfilessubdirs} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder deepCopySubdirectories() {
        this.arguments.add("-docfilessubdirs");
        return this;
    }

    /**
     * Specifies the title to be placed in the HTML {@code <title>} tag. Corresponds to the {@code -doctitle} option.
     *
     * @param title The document title.
     * @return This builder instance.
     */
    public JavadocCLIBuilder documentTitle(String title) {
        Objects.requireNonNull(title, "Title cannot be null");
        this.arguments.add("-doctitle " + title);
        return this;
    }

    /**
     * Excludes subdirectories from the {@code doc-files} directory. Corresponds to the {@code -excludedocfilessubdir} option.
     *
     * @param dirNames The names of the directories to exclude.
     * @return This builder instance.
     */
    public JavadocCLIBuilder excludeSubdirectories(String... dirNames) {
        Objects.requireNonNull(dirNames, "Directory names cannot be null");
        this.arguments.add("-excludedocfilessubdir " + String.join(",", dirNames));
        return this;
    }

    /**
     * Specifies the footer text for each generated page. Corresponds to the {@code -footer} option.
     *
     * @param text The footer text.
     * @return This builder instance.
     */
    public JavadocCLIBuilder footerText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-footer " + text);
        return this;
    }

    /**
     * Specifies the header text for each generated page. Corresponds to the {@code -header} option.
     *
     * @param text The header text.
     * @return This builder instance.
     */
    public JavadocCLIBuilder headerText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-header " + text);
        return this;
    }

    /**
     * Separates packages into groups on the overview page. Corresponds to the {@code -group} option.
     *
     * @param groupDefinitions The group definitions.
     * @return This builder instance.
     */
    public JavadocCLIBuilder groups(String... groupDefinitions) {
        Objects.requireNonNull(groupDefinitions, "Group definitions cannot be null");
        this.arguments.add("-group " + String.join(",", groupDefinitions));
        return this;
    }

    /**
     * Specifies the path to an alternative help file. Corresponds to the {@code -helpfile} option.
     *
     * @param filename The name of the help file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder helpFilename(String filename) {
        Objects.requireNonNull(filename, "Filename cannot be null");
        this.arguments.add("-helpfile " + filename);
        return this;
    }

    /**
     * Specifies the path to an alternative help file. Corresponds to the {@code -helpfile} option.
     *
     * @param filePath The path to the help file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder helpFilename(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return helpFilename(filePath.toString());
    }

    /**
     * Generates HTML5 output. Corresponds to the {@code -html5} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableHtml5() {
        this.arguments.add("-html5");
        return this;
    }

    /**
     * Enables JavaFX-specific features. Corresponds to the {@code -javafx} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableJavaFX() {
        this.arguments.add("-javafx");
        return this;
    }

    /**
     * Adds HTML keywords to the generated documentation. Corresponds to the {@code -keywords} option.
     *
     * @param keywords The keywords to add.
     * @return This builder instance.
     */
    public JavadocCLIBuilder keywords(String... keywords) {
        Objects.requireNonNull(keywords, "Keywords cannot be null");
        this.arguments.add("-keywords " + String.join(",", keywords));
        return this;
    }

    /**
     * Creates links to existing Javadoc documentation. Corresponds to the {@code -link} option.
     *
     * @param url The URL of the external Javadoc documentation.
     * @return This builder instance.
     */
    public JavadocCLIBuilder link(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("-link " + url);
        return this;
    }

    /**
     * Creates links to existing Javadoc documentation. Corresponds to the {@code -link} option.
     *
     * @param urlPath The path to the external Javadoc documentation.
     * @return This builder instance.
     */
    public JavadocCLIBuilder link(Path urlPath) {
        Objects.requireNonNull(urlPath, "URL path cannot be null");
        return link("file://" + urlPath);
    }

    /**
     * Specifies how to handle modularity mismatches when linking. Corresponds to the {@code --link-modularity-mismatch} option.
     *
     * @param mismatchBehavior The behavior for modularity mismatches (WARN or INFO).
     * @return This builder instance.
     */
    public JavadocCLIBuilder linkModularityMismatch(LinkModularityMismatch mismatchBehavior) {
        Objects.requireNonNull(mismatchBehavior, "Mismatch behavior cannot be null");
        this.arguments.add("--link-modularity-mismatch " + mismatchBehavior.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Creates links to existing Javadoc documentation for offline access. Corresponds to the {@code -linkoffline} option.
     *
     * @param url             The URL of the external Javadoc documentation.
     * @param packageListPath The path to the {@code package-list} file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder linkOffline(String url, Path packageListPath) {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(packageListPath, "Package list path cannot be null");
        this.arguments.add("-linkoffline " + url + " " + packageListPath);
        return this;
    }

    /**
     * Creates links to existing Javadoc documentation for offline access. Corresponds to the {@code -linkoffline} option.
     *
     * @param url             The URL of the external Javadoc documentation.
     * @param packageListPath The path to the {@code package-list} file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder linkOffline(String url, String packageListPath) {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(packageListPath, "Package list path cannot be null");
        this.arguments.add("-linkoffline " + url + " " + packageListPath);
        return this;
    }

    /**
     * Links to platform properties. Corresponds to the {@code -linkplatformproperties} option.
     *
     * @param url The URL of the platform properties.
     * @return This builder instance.
     */
    public JavadocCLIBuilder linkPlatformProperties(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("-linkplatformproperties " + url);
        return this;
    }

    /**
     * Links to platform properties. Corresponds to the {@code -linkplatformproperties} option.
     *
     * @param urlPath The path to the platform properties.
     * @return This builder instance.
     */
    public JavadocCLIBuilder linkPlatformProperties(Path urlPath) {
        Objects.requireNonNull(urlPath, "URL path cannot be null");
        return linkPlatformProperties("file://" + urlPath);
    }

    /**
     * Includes HTML versions of the source files. Corresponds to the {@code -linksource} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder shouldLinkSource() {
        this.arguments.add("-linksource");
        return this;
    }

    /**
     * Specifies the path to an alternative stylesheet file. Corresponds to the {@code -stylesheetfile} option.
     *
     * @param filePath The path to the stylesheet file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder stylesheetFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("-stylesheetfile " + filePath);
        return this;
    }

    /**
     * Specifies the path to an alternative stylesheet file. Corresponds to the {@code -stylesheetfile} option.
     *
     * @param filePath The path to the stylesheet file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder stylesheetFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return stylesheetFile(filePath.toString());
    }

    /**
     * Suppresses the entire comment body, including the main description and all tags, from the generated documentation. Corresponds to the {@code -nocomment} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noComments() {
        this.arguments.add("-nocomment");
        return this;
    }

    /**
     * Excludes deprecated APIs from the generated documentation. Corresponds to the {@code -nodeprecated} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noDeprecated() {
        this.arguments.add("-nodeprecated");
        return this;
    }

    /**
     * Prevents the generation of the deprecated list. Corresponds to the {@code -nodeprecatedlist} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noDeprecatedList() {
        this.arguments.add("-nodeprecatedlist");
        return this;
    }

    /**
     * Suppresses the generation of font-related HTML tags. Corresponds to the {@code --nofonts} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noFonts() {
        this.arguments.add("--nofonts");
        return this;
    }

    /**
     * Suppresses the generation of the HELP link in the navigation bar. Corresponds to the {@code -nohelp} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noHelp() {
        this.arguments.add("-nohelp");
        return this;
    }

    /**
     * Suppresses the generation of the index. Corresponds to the {@code -noindex} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noIndex() {
        this.arguments.add("-noindex");
        return this;
    }

    /**
     * Suppresses the generation of the navigation bar. Corresponds to the {@code -nonavbar} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noNavBar() {
        this.arguments.add("-nonavbar");
        return this;
    }

    /**
     * Suppresses the generation of links to platform documentation. Corresponds to the {@code --no-platform-links} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noPlatformLinks() {
        this.arguments.add("--no-platform-links");
        return this;
    }

    /**
     * Excludes package name qualifiers from the generated output. Corresponds to the {@code -noqualifier} option.
     *
     * @param qualifiers The qualifiers to exclude.
     * @return This builder instance.
     */
    public JavadocCLIBuilder noQualifier(String... qualifiers) {
        Objects.requireNonNull(qualifiers, "Qualifiers cannot be null");
        this.arguments.add("-noqualifier " + String.join(",", qualifiers));
        return this;
    }

    /**
     * Suppresses the generation of {@code @since} tags. Corresponds to the {@code -nosince} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noSinceTag() {
        this.arguments.add("-nosince");
        return this;
    }

    /**
     * Suppresses the generation of a timestamp in the generated HTML. Corresponds to the {@code -notimestamp} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noTimestamp() {
        this.arguments.add("-notimestamp");
        return this;
    }

    /**
     * Suppresses the generation of the class hierarchy tree. Corresponds to the {@code -notree} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noTree() {
        this.arguments.add("-notree");
        return this;
    }

    /**
     * Specifies how to handle method override documentation. Corresponds to the {@code --override-methods} option.
     *
     * @param handling The handling strategy (DETAIL or SUMMARY).
     * @return This builder instance.
     */
    public JavadocCLIBuilder methodOverrideHandling(MethodOverrideHandling handling) {
        Objects.requireNonNull(handling, "Method override handling cannot be null");
        this.arguments.add("--override-methods " + handling.name().toLowerCase(Locale.ROOT));
        return this;
    }

    /**
     * Specifies the path to an HTML file that contains overview documentation. Corresponds to the {@code -overview} option.
     *
     * @param filePath The path to the overview file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder overviewFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("-overview " + filePath);
        return this;
    }

    /**
     * Specifies the path to an HTML file that contains overview documentation. Corresponds to the {@code -overview} option.
     *
     * @param filePath The path to the overview file.
     * @return This builder instance.
     */
    public JavadocCLIBuilder overviewFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return overviewFile(filePath.toString());
    }

    /**
     * Reports warnings about {@code @serial} tags. Corresponds to the {@code -serialwarn} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder reportSerialWarnings() {
        this.arguments.add("-serialwarn");
        return this;
    }

    /**
     * Includes documentation for APIs introduced in a specific release version. Corresponds to the {@code -since} option.
     *
     * @param releaseVersions The release versions.
     * @return This builder instance.
     */
    public JavadocCLIBuilder since(String... releaseVersions) {
        Objects.requireNonNull(releaseVersions, "Release versions cannot be null");
        this.arguments.add("-since " + String.join(",", releaseVersions));
        return this;
    }

    /**
     * Includes documentation for APIs introduced in a specific release version. Corresponds to the {@code -since} option.
     *
     * @param releaseVersions The release versions.
     * @return This builder instance.
     */
    public JavadocCLIBuilder since(int... releaseVersions) {
        Objects.requireNonNull(releaseVersions, "Release versions cannot be null");
        String[] versionStrings = Arrays.stream(releaseVersions).mapToObj(Integer::toString).toArray(String[]::new);
        return since(versionStrings);
    }

    /**
     * Specifies the label to be used for the "Since" heading. Corresponds to the {@code -sincelabel} option.
     *
     * @param label The label.
     * @return This builder instance.
     */
    public JavadocCLIBuilder sinceLabel(String label) {
        Objects.requireNonNull(label, "Label cannot be null");
        this.arguments.add("-sincelabel " + label);
        return this;
    }

    /**
     * Specifies the paths where snippet files are located. Corresponds to the {@code --snippet-path} option.
     *
     * @param paths The paths to the snippet files.
     * @return This builder instance.
     */
    public JavadocCLIBuilder snippetPaths(String... paths) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        this.arguments.add("--snippet-path " + String.join(File.pathSeparator, paths));
        return this;
    }

    /**
     * Specifies the paths where snippet files are located. Corresponds to the {@code --snippet-path} option.
     *
     * @param paths The paths to the snippet files.
     * @return This builder instance.
     */
    public JavadocCLIBuilder snippetPaths(Path... paths) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        String[] pathStrings = Arrays.stream(paths).map(Path::toString).toArray(String[]::new);
        return snippetPaths(pathStrings);
    }

    /**
     * Specifies the number of spaces each tab in the source file occupies. Corresponds to the {@code -sourcetab} option.
     *
     * @param spaces The number of spaces per tab.
     * @return This builder instance.
     * @throws IllegalArgumentException if spaces is not positive.
     */
    public JavadocCLIBuilder spacesPerTab(int spaces) {
        if (spaces <= 0)
            throw new IllegalArgumentException("Spaces per tab must be positive");

        this.arguments.add("-sourcetab " + spaces);
        return this;
    }

    /**
     * Specifies the base URL for the generated specification. Corresponds to the {@code --spec-base-url} option.
     *
     * @param url The base URL.
     * @return This builder instance.
     */
    public JavadocCLIBuilder specBaseUrl(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("--spec-base-url " + url);
        return this;
    }

    /**
     * Splits the index into multiple files, one for each letter. Corresponds to the {@code -splitindex} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder shouldSplitIndex() {
        this.arguments.add("-splitindex");
        return this;
    }

    /**
     * Enables syntax highlighting in the generated documentation. Corresponds to the {@code --syntaxhighlight} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableSyntaxHighlighting() {
        this.arguments.add("--syntaxhighlight");
        return this;
    }

    /**
     * Enables the Javadoc tool to interpret a simple custom block tag. Corresponds to the {@code -tag} option.
     *
     * @param name      The name of the tag.
     * @param locations The locations where the tag is valid.
     * @param header    The header text for the tag.
     * @return This builder instance.
     */
    public JavadocCLIBuilder tag(String name, String locations, String header) {
        Objects.requireNonNull(name, "Tag name cannot be null");
        Objects.requireNonNull(locations, "Locations cannot be null");
        Objects.requireNonNull(header, "Header cannot be null");
        this.arguments.add("-tag " + name + ":" + locations + ":" + header);
        return this;
    }

    /**
     * Specifies the class name of the taglet to use. Corresponds to the {@code -taglet} option.
     *
     * @param tagletClassName The class name of the taglet.
     * @return This builder instance.
     */
    public JavadocCLIBuilder taglet(String tagletClassName) {
        Objects.requireNonNull(tagletClassName, "Taglet class name cannot be null");
        this.arguments.add("-taglet " + tagletClassName);
        return this;
    }

    /**
     * Specifies the path where the taglet and its supporting classes are located. Corresponds to the {@code -tagletpath} option.
     *
     * @param tagletPaths The entries for the taglet path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder tagletPath(String... tagletPaths) {
        Objects.requireNonNull(tagletPaths, "Taglet path entries cannot be null");
        this.arguments.add("-tagletpath " + String.join(File.pathSeparator, tagletPaths));
        return this;
    }

    /**
     * Specifies the path where the taglet and its supporting classes are located. Corresponds to the {@code -tagletpath} option.
     *
     * @param tagletPaths The entries for the taglet path.
     * @return This builder instance.
     */
    public JavadocCLIBuilder tagletPath(Path... tagletPaths) {
        Objects.requireNonNull(tagletPaths, "Taglet path entries cannot be null");
        String[] pathStrings = Arrays.stream(tagletPaths).map(Path::toString).toArray(String[]::new);
        return tagletPath(pathStrings);
    }

    /**
     * Specifies the text to be placed at the top of each generated page. Corresponds to the {@code -top} option.
     *
     * @param text The text to place at the top.
     * @return This builder instance.
     */
    public JavadocCLIBuilder topText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-top " + text);
        return this;
    }

    /**
     * Creates a "Use" page for each class and package. Corresponds to the {@code -use} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder createUsagePages() {
        this.arguments.add("-use");
        return this;
    }

    /**
     * Includes {@code @version} tags in the generated documentation. Corresponds to the {@code -version} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder includeVersionTags() {
        this.arguments.add("-version");
        return this;
    }

    /**
     * Specifies the title to be placed in the HTML window title. Corresponds to the {@code -windowtitle} option.
     *
     * @param title The window title.
     * @return This builder instance.
     */
    public JavadocCLIBuilder windowTitle(String title) {
        Objects.requireNonNull(title, "Title cannot be null");
        this.arguments.add("-windowtitle " + title);
        return this;
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param iso8601Date The date and time in ISO 8601 format (e.g., "2023-10-05T14:48:00Z").
     * @return This builder instance.
     * @throws IllegalArgumentException if the date is not in ISO 8601 format or is outside a reasonable range.
     */
    public JavadocCLIBuilder date(String iso8601Date) {
        Objects.requireNonNull(iso8601Date, "Date cannot be null");
        try {
            OffsetDateTime dateTime = OffsetDateTime.parse(iso8601Date, ISO_OFFSET_FORMATTER);
            return date(dateTime);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must be in ISO 8601 format (e.g., 2023-10-05T14:48:00Z)", exception);
        }
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param date The date object.
     * @return This builder instance.
     */
    public JavadocCLIBuilder date(Date date) {
        Objects.requireNonNull(date, "Date cannot be null");
        return date(OffsetDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param epochMillis The date and time as epoch milliseconds.
     * @return This builder instance.
     */
    public JavadocCLIBuilder date(long epochMillis) {
        return date(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param dateTime The {@link LocalDateTime} object.
     * @return This builder instance.
     */
    public JavadocCLIBuilder date(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "DateTime cannot be null");
        return date(dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param dateTime The {@link OffsetDateTime} object.
     * @return This builder instance.
     * @throws IllegalArgumentException if the date is outside a reasonable range (10 years before or after current date).
     */
    private JavadocCLIBuilder date(OffsetDateTime dateTime) {
        Objects.requireNonNull(dateTime, "DateTime cannot be null");
        Instant candidate = dateTime.toInstant();
        Instant now = Instant.now();
        Instant tenYearsAgo = now.minus(10, ChronoUnit.YEARS);
        Instant tenYearsAhead = now.plus(10, ChronoUnit.YEARS);
        if (candidate.isBefore(tenYearsAgo) || candidate.isAfter(tenYearsAhead))
            throw new IllegalArgumentException("Date must be within 10 years of the current date");

        this.arguments.add("-date " + ISO_OFFSET_FORMATTER.format(dateTime));
        return this;
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param year   The year.
     * @param month  The month.
     * @param day    The day.
     * @param hour   The hour.
     * @param minute The minute.
     * @param second The second.
     * @return This builder instance.
     */
    public JavadocCLIBuilder date(int year, int month, int day, int hour, int minute, int second) {
        var dateTime = LocalDateTime.of(year, month, day, hour, minute, second);
        return date(dateTime);
    }

    /**
     * Specifies the date and time to be used for the generated documentation. Corresponds to the {@code -date} option.
     *
     * @param year  The year.
     * @param month The month.
     * @param day   The day.
     * @return This builder instance.
     */
    public JavadocCLIBuilder date(int year, int month, int day) {
        var dateTime = LocalDateTime.of(year, month, day, 0, 0, 0);
        return date(dateTime);
    }

    /**
     * Includes the default legal notices. Corresponds to the {@code --legal-notices default} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder defaultLegalNotices() {
        this.arguments.add("--legal-notices default");
        return this;
    }

    /**
     * Excludes legal notices. Corresponds to the {@code --legal-notices none} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder noLegalNotices() {
        this.arguments.add("--legal-notices none");
        return this;
    }

    /**
     * Includes custom legal notices from a specified file. Corresponds to the {@code --legal-notices} option.
     *
     * @param filePath The path to the file containing custom legal notices.
     * @return This builder instance.
     */
    public JavadocCLIBuilder customLegalNotices(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("--legal-notices " + filePath);
        return this;
    }

    /**
     * Suppresses the generation of frames. Corresponds to the {@code -noframes} option.
     *
     * @return This builder instance.
     * @deprecated This option is deprecated and may be removed in future versions.
     */
    @Deprecated
    public JavadocCLIBuilder noFrames() {
        this.arguments.add("-noframes");
        return this;
    }

    /**
     * Enables recommended doclint checks. Corresponds to the {@code -Xdoclint} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableRecommendedChecks() {
        this.arguments.add("-Xdoclint");
        return this;
    }

    /**
     * Disables all doclint checks. Corresponds to the {@code -Xdoclint:none} option.
     *
     * @return This builder instance.
     */
    public JavadocCLIBuilder disableAllChecks() {
        this.arguments.add("-Xdoclint:none");
        return this;
    }

    /**
     * Enables specific doclint checks. Corresponds to the {@code -Xdoclint:checks} option.
     *
     * @param checks A comma-separated list of checks to enable.
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableChecks(String checks) {
        Objects.requireNonNull(checks, "Checks cannot be null");
        this.arguments.add("-Xdoclint:" + checks);
        return this;
    }

    /**
     * Disables specific doclint checks. Corresponds to the {@code -Xdoclint:-checks} option.
     *
     * @param checks A comma-separated list of checks to disable.
     * @return This builder instance.
     */
    public JavadocCLIBuilder disableChecks(String checks) {
        Objects.requireNonNull(checks, "Checks cannot be null");
        this.arguments.add("-Xdoclint:-" + checks);
        return this;
    }

    /**
     * Enables doclint checks for specific packages. Corresponds to the {@code -Xdoclint/package:packages} option.
     *
     * @param packages A comma-separated list of packages to enable checks for.
     * @return This builder instance.
     */
    public JavadocCLIBuilder enableChecks(String... packages) {
        Objects.requireNonNull(packages, "Packages cannot be null");
        this.arguments.add("-Xdoclint/package:" + String.join(",", packages));
        return this;
    }

    /**
     * Disables doclint checks for specific packages. Corresponds to the {@code -Xdoclint/package:-packages} option.
     *
     * @param packages A comma-separated list of packages to disable checks for.
     * @return This builder instance.
     */
    public JavadocCLIBuilder disableChecks(String... packages) {
        Objects.requireNonNull(packages, "Packages cannot be null");
        this.arguments.add("-Xdoclint/package:-" + String.join(",", packages));
        return this;
    }

    /**
     * Specifies the URL of the parent directory for the generated documentation. Corresponds to the {@code -Xdocrootparent} option.
     *
     * @param url The URL of the parent directory.
     * @return This builder instance.
     */
    public JavadocCLIBuilder docRootParentUrl(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("-Xdocrootparent " + url);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.addAll(packageNames);
        command.addAll(sourceFilePaths.stream().map(Path::toString).toList());
        command.addAll(argumentFilePaths.stream().map(Path::toString).toList());

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "javadoc");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start Javadoc process", exception);
        }
    }

    /**
     * Represents the type of module expansion.
     */
    public enum ExpansionType {
        TRANSITIVE,
        ALL
    }

    /**
     * Represents the visibility levels for members.
     */
    public enum Visibility {
        PUBLIC,
        PROTECTED,
        PACKAGE,
        PRIVATE
    }

    /**
     * Represents the granularity levels for module contents.
     */
    public enum ModuleGranularity {
        API,
        ALL
    }

    /**
     * Represents the granularity levels for packages.
     */
    public enum PackageGranularity {
        EXPORTED,
        ALL
    }

    /**
     * Represents how to handle modularity mismatches when linking.
     */
    public enum LinkModularityMismatch {
        WARN,
        INFO
    }

    /**
     * Represents how to handle method override documentation.
     */
    public enum MethodOverrideHandling {
        DETAIL,
        SUMMARY
    }
}
