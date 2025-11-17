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

    public JavadocCLIBuilder addPackageName(String packageName) {
        Objects.requireNonNull(packageName, "Package name cannot be null");
        this.packageNames.add(packageName);
        return this;
    }

    public JavadocCLIBuilder addSourceFilePath(Path sourceFilePath) {
        Objects.requireNonNull(sourceFilePath, "Source file path cannot be null");
        this.sourceFilePaths.add(sourceFilePath);
        return this;
    }

    public JavadocCLIBuilder addArgumentFilePath(Path argumentFilePath) {
        Objects.requireNonNull(argumentFilePath, "Argument file path cannot be null");
        this.argumentFilePaths.add(argumentFilePath);
        return this;
    }

    public JavadocCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");

        var modulesBuilder = new StringBuilder("--add-modules ");
        for (String module : modules) {
            modulesBuilder.append(",").append(module);
        }

        this.arguments.add(modulesBuilder.toString());
        return this;
    }

    public JavadocCLIBuilder addAllModules() {
        return addModules("ALL-MODULE-PATH");
    }

    public JavadocCLIBuilder appendBootClassPath(String... bootClassPathEntries) {
        if (jdk.version().major() >= 9)
            throw new UnsupportedOperationException("The --boot-class-path option is not supported in JDK 9 and above.");

        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("--boot-class-path " + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    public JavadocCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    public JavadocCLIBuilder enablePreviewFeatures() {
        if (jdk.version().major() < 12)
            throw new UnsupportedOperationException("Preview features are only supported in JDK 12 and above.");

        this.arguments.add("--enable-preview");
        return this;
    }

    public JavadocCLIBuilder encoding(String encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        this.arguments.add("-encoding " + encoding);
        return this;
    }

    public JavadocCLIBuilder encoding(Charset encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        return encoding(encoding.name());
    }

    public JavadocCLIBuilder extDirs(String... dirs) {
        Objects.requireNonNull(dirs, "Ext dirs cannot be null");
        this.arguments.add("-extdirs " + String.join(File.pathSeparator, dirs));
        return this;
    }

    public JavadocCLIBuilder extDirs(Path... dirs) {
        Objects.requireNonNull(dirs, "Ext dirs cannot be null");
        String[] dirStrings = Arrays.stream(dirs).map(Path::toString).toArray(String[]::new);
        return extDirs(dirStrings);
    }

    public JavadocCLIBuilder disableLineDocComments() {
        this.arguments.add("--disable-line-doc-comments");
        return this;
    }

    public JavadocCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    public JavadocCLIBuilder addModuleNames(String... moduleName) {
        Objects.requireNonNull(moduleName, "Module names cannot be null");
        this.arguments.add("-module " + String.join(",", moduleName));
        return this;
    }

    public JavadocCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JavadocCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    public JavadocCLIBuilder moduleSourcePath(String... moduleSourcePaths) {
        Objects.requireNonNull(moduleSourcePaths, "Module source path entries cannot be null");
        this.arguments.add("--module-source-path " + String.join(File.pathSeparator, moduleSourcePaths));
        return this;
    }

    public JavadocCLIBuilder moduleSourcePath(Path... moduleSourcePaths) {
        Objects.requireNonNull(moduleSourcePaths, "Module source path entries cannot be null");
        String[] pathStrings = Arrays.stream(moduleSourcePaths).map(Path::toString).toArray(String[]::new);
        return moduleSourcePath(pathStrings);
    }

    public JavadocCLIBuilder releaseVersion(String version) {
        Objects.requireNonNull(version, "Release version cannot be null");
        this.arguments.add("--release " + version);
        return this;
    }

    public JavadocCLIBuilder releaseVersion(int version) {
        return releaseVersion(Integer.toString(version));
    }

    public JavadocCLIBuilder sourceVersion(String version) {
        Objects.requireNonNull(version, "Source version cannot be null");
        this.arguments.add("--source " + version);
        return this;
    }

    public JavadocCLIBuilder sourceVersion(int version) {
        return sourceVersion(Integer.toString(version));
    }

    public JavadocCLIBuilder sourcepath(String... sourcePaths) {
        Objects.requireNonNull(sourcePaths, "Source path entries cannot be null");
        this.arguments.add("-sourcepath " + String.join(File.pathSeparator, sourcePaths));
        return this;
    }

    public JavadocCLIBuilder sourcepath(Path... sourcePaths) {
        Objects.requireNonNull(sourcePaths, "Source path entries cannot be null");
        String[] pathStrings = Arrays.stream(sourcePaths).map(Path::toString).toArray(String[]::new);
        return sourcepath(pathStrings);
    }

    public JavadocCLIBuilder systemJdk(JDK systemJdk) {
        Objects.requireNonNull(systemJdk, "System JDK cannot be null");
        this.arguments.add("--system " + systemJdk.path().toString());
        return this;
    }

    public JavadocCLIBuilder upgradeModulePath(String... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, upgradeModulePaths));
        return this;
    }

    public JavadocCLIBuilder upgradeModulePath(Path... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        String[] pathStrings = Arrays.stream(upgradeModulePaths).map(Path::toString).toArray(String[]::new);
        return upgradeModulePath(pathStrings);
    }

    public JavadocCLIBuilder enableBreakIterator() {
        this.arguments.add("-breakiterator");
        return this;
    }

    public JavadocCLIBuilder doclet(String docletClassName) {
        Objects.requireNonNull(docletClassName, "Doclet class name cannot be null");
        this.arguments.add("-doclet " + docletClassName);
        return this;
    }

    public JavadocCLIBuilder docletPath(String... docletPaths) {
        Objects.requireNonNull(docletPaths, "Doclet path entries cannot be null");
        this.arguments.add("-docletpath " + String.join(File.pathSeparator, docletPaths));
        return this;
    }

    public JavadocCLIBuilder docletPath(Path... docletPaths) {
        Objects.requireNonNull(docletPaths, "Doclet path entries cannot be null");
        String[] pathStrings = Arrays.stream(docletPaths).map(Path::toString).toArray(String[]::new);
        return docletPath(pathStrings);
    }

    public JavadocCLIBuilder excludePackages(String... packageNames) {
        Objects.requireNonNull(packageNames, "Package names cannot be null");
        this.arguments.add("-exclude " + String.join(":", packageNames));
        return this;
    }

    public JavadocCLIBuilder expandRequires(ExpansionType expansionType) {
        Objects.requireNonNull(expansionType, "Expansion type cannot be null");
        this.arguments.add("--expand-requires " + expansionType.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder help() {
        this.arguments.add("-?");
        return this;
    }

    public JavadocCLIBuilder extraHelp() {
        this.arguments.add("-X");
        return this;
    }

    public JavadocCLIBuilder jflag(String jflag) {
        Objects.requireNonNull(jflag, "JFlag cannot be null");
        this.arguments.add("-J" + jflag);
        return this;
    }

    public JavadocCLIBuilder locale(String locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        this.arguments.add("-locale " + locale);
        return this;
    }

    public JavadocCLIBuilder locale(Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        return locale(locale.toLanguageTag());
    }

    public JavadocCLIBuilder visibility(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("-" + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder quiet() {
        this.arguments.add("-quiet");
        return this;
    }

    public JavadocCLIBuilder showMembers(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("--show-members " + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder showModuleContents(ModuleGranularity granularity) {
        Objects.requireNonNull(granularity, "Module granularity cannot be null");
        this.arguments.add("--show-module-contents " + granularity.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder showPackages(PackageGranularity granularity) {
        Objects.requireNonNull(granularity, "Package granularity cannot be null");
        this.arguments.add("--show-packages " + granularity.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder showTypes(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add("--show-types " + visibility.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder subpackages(String... packageNames) {
        Objects.requireNonNull(packageNames, "Package names cannot be null");
        this.arguments.add("-subpackages " + String.join(":", packageNames));
        return this;
    }

    public JavadocCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    public JavadocCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    public JavadocCLIBuilder reportErrorOnWarnings() {
        this.arguments.add("-Werror");
        return this;
    }

    public JavadocCLIBuilder addReads(String sourceModule, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-reads " + sourceModule + "=" + String.join(",", targetModules));
        return this;
    }

    public JavadocCLIBuilder addReadsAllUnnamed(String sourceModule) {
        return addReads(sourceModule, "ALL-UNNAMED");
    }

    public JavadocCLIBuilder addExports(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-exports " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    public JavadocCLIBuilder addExportsAllUnnamed(String sourceModule, String packageName) {
        return addExports(sourceModule, packageName, "ALL-UNNAMED");
    }

    public JavadocCLIBuilder patchModule(String moduleName, String... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        this.arguments.add("--patch-module " + moduleName + "=" + String.join(File.pathSeparator, patchPaths));
        return this;
    }

    public JavadocCLIBuilder patchModule(String moduleName, Path... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        String[] pathStrings = Arrays.stream(patchPaths).map(Path::toString).toArray(String[]::new);
        return patchModule(moduleName, pathStrings);
    }

    public JavadocCLIBuilder maxErrors(int maxErrors) {
        if (maxErrors < 0)
            throw new IllegalArgumentException("Max errors cannot be negative");

        this.arguments.add("-Xmaxerrs " + maxErrors);
        return this;
    }

    public JavadocCLIBuilder maxWarnings(int maxWarnings) {
        if (maxWarnings < 0)
            throw new IllegalArgumentException("Max warnings cannot be negative");

        this.arguments.add("-Xmaxwarns " + maxWarnings);
        return this;
    }

    public JavadocCLIBuilder addScript(String script) {
        Objects.requireNonNull(script, "Script cannot be null");
        this.arguments.add("--add-script " + script);
        return this;
    }

    public JavadocCLIBuilder addScript(Path scriptFilePath) {
        Objects.requireNonNull(scriptFilePath, "Script file path cannot be null");
        return addScript(scriptFilePath.toString());
    }

    public JavadocCLIBuilder addStylesheet(String styleSheet) {
        Objects.requireNonNull(styleSheet, "Style sheet cannot be null");
        this.arguments.add("--add-stylesheet " + styleSheet);
        return this;
    }

    public JavadocCLIBuilder addStylesheet(Path styleSheetPath) {
        Objects.requireNonNull(styleSheetPath, "Style sheet path cannot be null");
        return addStylesheet(styleSheetPath.toString());
    }

    public JavadocCLIBuilder allowScriptInComments() {
        this.arguments.add("--allow-script-in-comments");
        return this;
    }

    public JavadocCLIBuilder includeAuthorTags() {
        this.arguments.add("-author");
        return this;
    }

    public JavadocCLIBuilder textAtBottom(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-bottom " + text);
        return this;
    }

    public JavadocCLIBuilder charset(String charset) {
        Objects.requireNonNull(charset, "Charset cannot be null");
        this.arguments.add("-charset " + charset);
        return this;
    }

    public JavadocCLIBuilder charset(Charset charset) {
        Objects.requireNonNull(charset, "Charset cannot be null");
        return charset(charset.name());
    }

    public JavadocCLIBuilder destinationDirectory(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "Output directory cannot be null");
        this.arguments.add("-d " + outputDirectory);
        return this;
    }

    public JavadocCLIBuilder generatedEncoding(String encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        this.arguments.add("-docencoding " + encoding);
        return this;
    }

    public JavadocCLIBuilder generatedEncoding(Charset encoding) {
        Objects.requireNonNull(encoding, "Encoding cannot be null");
        return generatedEncoding(encoding.name());
    }

    public JavadocCLIBuilder deepCopySubdirectories() {
        this.arguments.add("-docfilessubdirs");
        return this;
    }

    public JavadocCLIBuilder documentTitle(String title) {
        Objects.requireNonNull(title, "Title cannot be null");
        this.arguments.add("-doctitle " + title);
        return this;
    }

    public JavadocCLIBuilder excludeSubdirectories(String... dirNames) {
        Objects.requireNonNull(dirNames, "Directory names cannot be null");
        this.arguments.add("-excludedocfilessubdir " + String.join(",", dirNames));
        return this;
    }

    public JavadocCLIBuilder footerText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-footer " + text);
        return this;
    }

    public JavadocCLIBuilder headerText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-header " + text);
        return this;
    }

    public JavadocCLIBuilder groups(String... groupDefinitions) {
        Objects.requireNonNull(groupDefinitions, "Group definitions cannot be null");
        this.arguments.add("-group " + String.join(",", groupDefinitions));
        return this;
    }

    public JavadocCLIBuilder helpFilename(String filename) {
        Objects.requireNonNull(filename, "Filename cannot be null");
        this.arguments.add("-helpfile " + filename);
        return this;
    }

    public JavadocCLIBuilder helpFilename(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return helpFilename(filePath.toString());
    }

    public JavadocCLIBuilder enableHtml5() {
        this.arguments.add("-html5");
        return this;
    }

    public JavadocCLIBuilder enableJavaFX() {
        this.arguments.add("-javafx");
        return this;
    }

    public JavadocCLIBuilder keywords(String... keywords) {
        Objects.requireNonNull(keywords, "Keywords cannot be null");
        this.arguments.add("-keywords " + String.join(",", keywords));
        return this;
    }

    public JavadocCLIBuilder link(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("-link " + url);
        return this;
    }

    public JavadocCLIBuilder link(Path urlPath) {
        Objects.requireNonNull(urlPath, "URL path cannot be null");
        return link("file://" + urlPath);
    }

    public JavadocCLIBuilder linkModularityMismatch(LinkModularityMismatch mismatchBehavior) {
        Objects.requireNonNull(mismatchBehavior, "Mismatch behavior cannot be null");
        this.arguments.add("--link-modularity-mismatch " + mismatchBehavior.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder linkOffline(String url, Path packageListPath) {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(packageListPath, "Package list path cannot be null");
        this.arguments.add("-linkoffline " + url + " " + packageListPath);
        return this;
    }

    public JavadocCLIBuilder linkOffline(String url, String packageListPath) {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(packageListPath, "Package list path cannot be null");
        this.arguments.add("-linkoffline " + url + " " + packageListPath);
        return this;
    }

    public JavadocCLIBuilder linkPlatformProperties(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("-linkplatformproperties " + url);
        return this;
    }

    public JavadocCLIBuilder linkPlatformProperties(Path urlPath) {
        Objects.requireNonNull(urlPath, "URL path cannot be null");
        return linkPlatformProperties("file://" + urlPath);
    }

    public JavadocCLIBuilder shouldLinkSource() {
        this.arguments.add("-linksource");
        return this;
    }

    public JavadocCLIBuilder stylesheetFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("-stylesheetfile " + filePath);
        return this;
    }

    public JavadocCLIBuilder stylesheetFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return stylesheetFile(filePath.toString());
    }

    public JavadocCLIBuilder noComments() {
        this.arguments.add("-nocomment");
        return this;
    }

    public JavadocCLIBuilder noDeprecated() {
        this.arguments.add("-nodeprecated");
        return this;
    }

    public JavadocCLIBuilder noDeprecatedList() {
        this.arguments.add("-nodeprecatedlist");
        return this;
    }

    public JavadocCLIBuilder noFonts() {
        this.arguments.add("--nofonts");
        return this;
    }

    public JavadocCLIBuilder noHelp() {
        this.arguments.add("-nohelp");
        return this;
    }

    public JavadocCLIBuilder noIndex() {
        this.arguments.add("-noindex");
        return this;
    }

    public JavadocCLIBuilder noNavBar() {
        this.arguments.add("-nonavbar");
        return this;
    }

    public JavadocCLIBuilder noPlatformLinks() {
        this.arguments.add("--no-platform-links");
        return this;
    }

    public JavadocCLIBuilder noQualifier(String... qualifiers) {
        Objects.requireNonNull(qualifiers, "Qualifiers cannot be null");
        this.arguments.add("-noqualifier " + String.join(",", qualifiers));
        return this;
    }

    public JavadocCLIBuilder noSinceTag() {
        this.arguments.add("-nosince");
        return this;
    }

    public JavadocCLIBuilder noTimestamp() {
        this.arguments.add("-notimestamp");
        return this;
    }

    public JavadocCLIBuilder noTree() {
        this.arguments.add("-notree");
        return this;
    }

    public JavadocCLIBuilder methodOverrideHandling(MethodOverrideHandling handling) {
        Objects.requireNonNull(handling, "Method override handling cannot be null");
        this.arguments.add("--override-methods " + handling.name().toLowerCase(Locale.ROOT));
        return this;
    }

    public JavadocCLIBuilder overviewFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("-overview " + filePath);
        return this;
    }

    public JavadocCLIBuilder overviewFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        return overviewFile(filePath.toString());
    }

    public JavadocCLIBuilder reportSerialWarnings() {
        this.arguments.add("-serialwarn");
        return this;
    }

    public JavadocCLIBuilder since(String... releaseVersions) {
        Objects.requireNonNull(releaseVersions, "Release versions cannot be null");
        this.arguments.add("-since " + String.join(",", releaseVersions));
        return this;
    }

    public JavadocCLIBuilder since(int... releaseVersions) {
        Objects.requireNonNull(releaseVersions, "Release versions cannot be null");
        String[] versionStrings = Arrays.stream(releaseVersions).mapToObj(Integer::toString).toArray(String[]::new);
        return since(versionStrings);
    }

    public JavadocCLIBuilder sinceLabel(String label) {
        Objects.requireNonNull(label, "Label cannot be null");
        this.arguments.add("-sincelabel " + label);
        return this;
    }

    public JavadocCLIBuilder snippetPaths(String... paths) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        this.arguments.add("--snippet-path " + String.join(File.pathSeparator, paths));
        return this;
    }

    public JavadocCLIBuilder snippetPaths(Path... paths) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        String[] pathStrings = Arrays.stream(paths).map(Path::toString).toArray(String[]::new);
        return snippetPaths(pathStrings);
    }

    public JavadocCLIBuilder spacesPerTab(int spaces) {
        if (spaces <= 0)
            throw new IllegalArgumentException("Spaces per tab must be positive");

        this.arguments.add("-sourcetab " + spaces);
        return this;
    }

    public JavadocCLIBuilder specBaseUrl(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        this.arguments.add("--spec-base-url " + url);
        return this;
    }

    public JavadocCLIBuilder shouldSplitIndex() {
        this.arguments.add("-splitindex");
        return this;
    }

    public JavadocCLIBuilder enableSyntaxHighlighting() {
        this.arguments.add("--syntaxhighlight");
        return this;
    }

    public JavadocCLIBuilder tag(String name, String locations, String header) {
        Objects.requireNonNull(name, "Tag name cannot be null");
        Objects.requireNonNull(locations, "Locations cannot be null");
        Objects.requireNonNull(header, "Header cannot be null");
        this.arguments.add("-tag " + name + ":" + locations + ":" + header);
        return this;
    }

    public JavadocCLIBuilder taglet(String tagletClassName) {
        Objects.requireNonNull(tagletClassName, "Taglet class name cannot be null");
        this.arguments.add("-taglet " + tagletClassName);
        return this;
    }

    public JavadocCLIBuilder tagletPath(String... tagletPaths) {
        Objects.requireNonNull(tagletPaths, "Taglet path entries cannot be null");
        this.arguments.add("-tagletpath " + String.join(File.pathSeparator, tagletPaths));
        return this;
    }

    public JavadocCLIBuilder tagletPath(Path... tagletPaths) {
        Objects.requireNonNull(tagletPaths, "Taglet path entries cannot be null");
        String[] pathStrings = Arrays.stream(tagletPaths).map(Path::toString).toArray(String[]::new);
        return tagletPath(pathStrings);
    }

    public JavadocCLIBuilder topText(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        this.arguments.add("-top " + text);
        return this;
    }

    public JavadocCLIBuilder createUsagePages() {
        this.arguments.add("-use");
        return this;
    }

    public JavadocCLIBuilder includeVersionTags() {
        this.arguments.add("-version");
        return this;
    }

    public JavadocCLIBuilder windowTitle(String title) {
        Objects.requireNonNull(title, "Title cannot be null");
        this.arguments.add("-windowtitle " + title);
        return this;
    }

    public JavadocCLIBuilder date(String iso8601Date) {
        Objects.requireNonNull(iso8601Date, "Date cannot be null");
        try {
            OffsetDateTime dateTime = OffsetDateTime.parse(iso8601Date, ISO_OFFSET_FORMATTER);
            return date(dateTime);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must be in ISO 8601 format (e.g., 2023-10-05T14:48:00Z)", exception);
        }
    }

    public JavadocCLIBuilder date(Date date) {
        Objects.requireNonNull(date, "Date cannot be null");
        return date(OffsetDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    public JavadocCLIBuilder date(long epochMillis) {
        return date(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    public JavadocCLIBuilder date(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "DateTime cannot be null");
        return date(dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

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

    public JavadocCLIBuilder date(int year, int month, int day, int hour, int minute, int second) {
        var dateTime = LocalDateTime.of(year, month, day, hour, minute, second);
        return date(dateTime);
    }

    public JavadocCLIBuilder date(int year, int month, int day) {
        var dateTime = LocalDateTime.of(year, month, day, 0, 0, 0);
        return date(dateTime);
    }

    public JavadocCLIBuilder defaultLegalNotices() {
        this.arguments.add("--legal-notices default");
        return this;
    }

    public JavadocCLIBuilder noLegalNotices() {
        this.arguments.add("--legal-notices none");
        return this;
    }

    public JavadocCLIBuilder customLegalNotices(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.arguments.add("--legal-notices " + filePath);
        return this;
    }

    @Deprecated
    public JavadocCLIBuilder noFrames() {
        this.arguments.add("-noframes");
        return this;
    }

    public JavadocCLIBuilder enableRecommendedChecks() {
        this.arguments.add("-Xdoclint");
        return this;
    }

    public JavadocCLIBuilder disableAllChecks() {
        this.arguments.add("-Xdoclint:none");
        return this;
    }

    public JavadocCLIBuilder enableChecks(String checks) {
        Objects.requireNonNull(checks, "Checks cannot be null");
        this.arguments.add("-Xdoclint:" + checks);
        return this;
    }

    public JavadocCLIBuilder disableChecks(String checks) {
        Objects.requireNonNull(checks, "Checks cannot be null");
        this.arguments.add("-Xdoclint:-" + checks);
        return this;
    }

    public JavadocCLIBuilder enableChecks(String... packages) {
        Objects.requireNonNull(packages, "Packages cannot be null");
        this.arguments.add("-Xdoclint/package:" + String.join(",", packages));
        return this;
    }

    public JavadocCLIBuilder disableChecks(String... packages) {
        Objects.requireNonNull(packages, "Packages cannot be null");
        this.arguments.add("-Xdoclint/package:-" + String.join(",", packages));
        return this;
    }

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

    public enum ExpansionType {
        TRANSITIVE,
        ALL
    }

    public enum Visibility {
        PUBLIC,
        PROTECTED,
        PACKAGE,
        PRIVATE
    }

    public enum ModuleGranularity {
        API,
        ALL
    }

    public enum PackageGranularity {
        EXPORTED,
        ALL
    }

    public enum LinkModularityMismatch {
        WARN,
        INFO
    }

    public enum MethodOverrideHandling {
        DETAIL,
        SUMMARY
    }
}
