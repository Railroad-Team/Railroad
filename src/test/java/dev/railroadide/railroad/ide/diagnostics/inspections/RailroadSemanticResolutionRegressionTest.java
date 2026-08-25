package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailroadSemanticResolutionRegressionTest {
    @Test
    void realRailroadStartupCodeDoesNotReportKnownFalseCallAndExceptionDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path railroadPath = sourceRoot.resolve("dev/railroadide/railroad/Railroad.java").normalize();
        Path preloaderPath = sourceRoot.resolve("dev/railroadide/railroad/RailroadPreloader.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> railroadCallDiagnostics = runProvider(new CoreCallResolutionInspection(), railroadPath,
            Files.readString(railroadPath), symbolIndex);
        assertFalse(railroadCallDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'InitializationStep'")),
            () -> railroadCallDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
        assertFalse(railroadCallDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'publish'")),
            () -> railroadCallDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> preloaderExceptionDiagnostics = runProvider(new CoreExceptionInspection(),
            preloaderPath, Files.readString(preloaderPath), symbolIndex);
        assertFalse(preloaderExceptionDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("ErrorNotification")
                && diagnostic.message().contains("must extend Throwable")));
    }

    @Test
    void projectEventBusPublishCallsResolveAcrossRealSources() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<String> unresolvedPublishDiagnostics = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path sourceFile : paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList()) {
                String source = Files.readString(sourceFile);
                if (!source.contains(".publish("))
                    continue;

                List<SemanticDiagnostic> diagnostics = runProvider(new CoreCallResolutionInspection(), sourceFile,
                    source, symbolIndex);
                diagnostics.stream()
                    .filter(diagnostic -> diagnostic.message().equals("Cannot resolve call 'publish'"))
                    .map(diagnostic -> sourceRoot.relativize(sourceFile) + ": " + diagnostic.message())
                    .forEach(unresolvedPublishDiagnostics::add);
            }
        }

        assertTrue(unresolvedPublishDiagnostics.isEmpty(), () -> String.join("\n", unresolvedPublishDiagnostics));
    }

    @Test
    void realRailroadPreloaderVarDeclarationsDoNotReportVoidAssignmentDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path preloaderPath = sourceRoot.resolve("dev/railroadide/railroad/RailroadPreloader.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAssignmentInspection(), preloaderPath,
            Files.readString(preloaderPath), symbolIndex);

        assertFalse(diagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains(" to 'void'")),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realServicesAnonymousInterfacesAndGenericServiceLookupDoNotReportKnownFalseDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path servicesPath = sourceRoot.resolve("dev/railroadide/railroad/Services.java").normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> callDiagnostics = runProvider(new CoreCallResolutionInspection(), servicesPath,
            Files.readString(servicesPath), symbolIndex);
        assertFalse(callDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'ApplicationInfoService'")
                || diagnostic.message().equals("Cannot resolve call 'LocalizationService'")),
            () -> callDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> typeDiagnostics = runProvider(new CoreTypeResolutionInspection(), servicesPath,
            Files.readString(servicesPath), symbolIndex);
        assertFalse(typeDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve type 'T'")),
            () -> typeDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realDefaultGradleEnvironmentRecordAndObjectMembersDoNotReportKnownFalseDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path environmentPath = sourceRoot.resolve("dev/railroadide/railroad/DefaultGradleEnvironment.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(environmentPath);

        List<SemanticDiagnostic> callDiagnostics = runProvider(new CoreCallResolutionInspection(), environmentPath,
            source, symbolIndex);
        assertFalse(callDiagnostics.stream()
            .anyMatch(diagnostic -> Set.of(
                "Cannot resolve call 'equals'",
                "Cannot resolve call 'isUseWrapper'",
                "Cannot resolve call 'getGradleUserHome'",
                "Cannot resolve call 'getGradleJvm'",
                "Cannot resolve call 'getConfigurations'",
                "Cannot resolve call 'getVmOptions'",
                "Cannot resolve call 'isDaemonEnabled'",
                "Cannot resolve call 'getDaemonIdleTimeout'",
                "Cannot resolve call 'getTask'",
                "Cannot resolve call 'getGradleProjectPath'",
                "Cannot resolve call 'getJavaHome'").contains(diagnostic.message())),
            () -> callDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> inheritanceDiagnostics = runProvider(new CoreInheritanceInspection(), environmentPath,
            source, symbolIndex);
        assertFalse(inheritanceDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("project()")),
            () -> inheritanceDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> assignmentDiagnostics = runProvider(new CoreDefiniteAssignmentInspection(),
            environmentPath, source, symbolIndex);
        assertFalse(assignmentDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("Variable 'project'")
                || diagnostic.message().contains("Variable 'settings'")
                || diagnostic.message().contains("Variable 'gradleInstallationPath'")),
            () -> assignmentDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));

        List<SemanticDiagnostic> memberDiagnostics = runProvider(new CoreMemberResolutionInspection(), environmentPath,
            source, symbolIndex);
        assertFalse(memberDiagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve member 'length'")),
            () -> memberDiagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowManagerFullyQualifiedAccessAndOverloadedConstructorsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path windowManagerPath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowManager.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(windowManagerPath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreMemberResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), windowManagerPath, source, symbolIndex));
        diagnostics
            .addAll(runProvider(new CoreDuplicateDeclarationInspection(), windowManagerPath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'getPrimaryStage'",
            "Cannot resolve member 'Railroad'",
            "Cannot resolve member 'railroad'",
            "Cannot resolve member 'railroadide'",
            "Cannot resolve name 'dev'",
            "Duplicate declaration for 'WindowManager'").contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowEventsPublishCallsAndLambdaParametersResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path windowEventsPath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowEvents.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(windowEventsPath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), windowEventsPath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreNameResolutionInspection(), windowEventsPath, source, symbolIndex));
        diagnostics
            .addAll(runProvider(new CoreDuplicateDeclarationInspection(), windowEventsPath, source, symbolIndex));

        assertFalse(
            diagnostics.stream().anyMatch(diagnostic -> diagnostic.message().equals("Cannot resolve call 'publish'")
                || diagnostic.message().equals("Cannot resolve name 'event'")
                || diagnostic.message().equals("Duplicate declaration for 'event'")),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realWindowBuilderFluentGenericsAndLambdaParametersResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourcePath = sourceRoot.resolve("dev/railroadide/railroad/window/WindowBuilder.java").normalize();
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(sourcePath);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(runProvider(new CoreCallResolutionInspection(), sourcePath, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourcePath, source, symbolIndex));

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> Set.of(
            "Cannot resolve call 'title'",
            "Cannot resolve call 'content'",
            "Cannot resolve call 'onClose'",
            "Cannot resolve call 'translateContent'",
            "Cannot resolve call 'onConfirm'",
            "Cannot resolve call 'onCancel'",
            "Cannot assign 'boolean' to 'dev.railroadide.railroad.window.DialogBuilder'")
            .contains(diagnostic.message())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realCoreAccessibilityInspectionLambdaParameterCallsResolve() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/ide/diagnostics/inspections/CoreAccessibilityInspection.java");
        Path compiledClasses = Path.of("build/classes/java/main").toAbsolutePath().normalize();
        var symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(List.of(compiledClasses)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = Files.readString(sourceFile);

        List<SemanticDiagnostic> diagnostics = new ArrayList<>(runProvider(
            new CoreCallResolutionInspection(), sourceFile, source, symbolIndex));
        diagnostics.addAll(runProvider(new CoreAssignmentInspection(), sourceFile, source, symbolIndex));
        List<String> unresolved = diagnostics.stream()
            .filter(diagnostic -> diagnostic.message().contains("'kind'")
                || diagnostic.message().contains("'id'")
                || diagnostic.message().contains("Cannot assign 'java.util.Optional'"))
            .map(SemanticDiagnostic::message)
            .toList();

        assertTrue(unresolved.isEmpty(), () -> String.join(System.lineSeparator(), unresolved));
    }

    @Test
    void realNestedFormDataMembersRemainAccessibleFromTheirEnclosingComponent() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path sourceFile = sourceRoot.resolve(
            "dev/railroadide/railroad/form/impl/CheckBoxComponent.java");
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()));

        List<SemanticDiagnostic> diagnostics = runProvider(
            new CoreAccessibilityInspection(), sourceFile, Files.readString(sourceFile), symbolIndex);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> "SEM_INACCESSIBLE_MEMBER".equals(diagnostic.code())),
            () -> diagnostics.stream().map(diagnostic -> diagnostic.startOffset() + " " + diagnostic.message())
                .collect(Collectors.joining("\n")));
    }

}
