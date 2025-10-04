package dev.railroadide.railroad.project.creation.step;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

import java.nio.file.Path;

public record RenameClassesStep(FilesService files) implements CreationStep {
    private static final PrinterConfiguration DEFAULT_PRINTER_CONFIGURATION = new DefaultPrinterConfiguration()
        .addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.ORDER_IMPORTS, true))
        .addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.INDENT_CASE_IN_SWITCH, true))
        .addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS, true))
        .addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_JAVADOC, true))
        .addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.SPACE_AROUND_OPERATORS, true));

    @Override
    public String id() {
        return "railroad:rename_classes";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.renaming_classes";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Renaming main class...");

        String mainClassName = ctx.data().getAsString(MinecraftProjectKeys.MAIN_CLASS);
        String groupId = ctx.data().getAsString(MavenProjectKeys.GROUP_ID);
        String modId = ctx.data().getAsString(MinecraftProjectKeys.MOD_ID);

        // Move the main class file
        Path projectDir = ctx.projectDir();
        Path mainJava = projectDir.resolve("src/main/java");
        Path rootPackagePath = mainJava.resolve(groupId.replace('.', '/') + "/" + modId);
        Path newMainClassPath = rootPackagePath.resolve(mainClassName + ".java");
        files.move(rootPackagePath.resolve("ExampleMod.java"), newMainClassPath);

        // Update the content of the main class
        reporter.info("Updating main class content...");
        String content = files.readString(newMainClassPath);
        CompilationUnit compilationUnit = StaticJavaParser.parse(content);
        compilationUnit.getClassByName("ExampleMod").ifPresent(c -> c.setName(mainClassName));
        compilationUnit.setPackageDeclaration(groupId + "." + modId);
        files.writeString(newMainClassPath, compilationUnit.toString(DEFAULT_PRINTER_CONFIGURATION));

        // Find the example mixin
        Path mixinPath = rootPackagePath.resolve("mixins").resolve("ExampleMixin.java");
        if (files.exists(mixinPath)) {
            reporter.info("Updating mixin content...");
            String mixinContent = files.readString(mixinPath);
            CompilationUnit mixinCompilationUnit = StaticJavaParser.parse(mixinContent);
            mixinCompilationUnit.setPackageDeclaration(groupId + "." + modId + ".mixins");
            files.writeString(mixinPath, mixinCompilationUnit.toString(DEFAULT_PRINTER_CONFIGURATION));
        }

        Path configPath = rootPackagePath.resolve("Config.java");
        if (files.exists(configPath)) {
            reporter.info("Updating config class content...");
            String configContent = files.readString(configPath);
            CompilationUnit configCompilationUnit = StaticJavaParser.parse(configContent);
            configCompilationUnit.setPackageDeclaration(groupId + "." + modId);
            files.writeString(configPath, configCompilationUnit.toString(DEFAULT_PRINTER_CONFIGURATION));
        }

        boolean splitSources = ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES, false);
        if (splitSources) {
            // Move and update the client main class
            reporter.info("Renaming client main class...");
            Path clientJava = projectDir.resolve("src/client/java");
            Path clientPackagePath = clientJava.resolve(groupId.replace('.', '/') + "/" + modId);
            Path newClientMainClassPath = clientPackagePath.resolve(mainClassName + "Client.java");
            files.move(clientPackagePath.resolve("ExampleModClient.java"), newClientMainClassPath);

            // Update the content of the client main class
            reporter.info("Updating client main class content...");
            String clientContent = files.readString(newClientMainClassPath);
            CompilationUnit clientCompilationUnit = StaticJavaParser.parse(clientContent);
            clientCompilationUnit.getClassByName("ExampleModClient").ifPresent(c -> c.setName(mainClassName + "Client"));
            clientCompilationUnit.setPackageDeclaration(groupId + "." + modId);
            files.writeString(newClientMainClassPath, clientCompilationUnit.toString(DEFAULT_PRINTER_CONFIGURATION));

            // Find the example client mixin
            Path clientMixinPath = clientPackagePath.resolve("mixins").resolve("ExampleMixin.java");
            if (files.exists(clientMixinPath)) {
                reporter.info("Updating client mixin content...");
                String clientMixinContent = files.readString(clientMixinPath);
                CompilationUnit clientMixinCompilationUnit = StaticJavaParser.parse(clientMixinContent);
                clientMixinCompilationUnit.setPackageDeclaration(groupId + "." + modId + ".mixins");
                files.writeString(clientMixinPath, clientMixinCompilationUnit.toString(DEFAULT_PRINTER_CONFIGURATION));
            }
        }
    }
}
