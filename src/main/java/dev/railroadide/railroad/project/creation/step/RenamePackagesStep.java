package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

import java.nio.file.Path;

public record RenamePackagesStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:rename_packages";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.renaming_packages";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        Path projectDir = ctx.projectDir();
        boolean splitSources = ctx.data().contains(FabricProjectKeys.SPLIT_SOURCES) && ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES);

        if (!splitSources) {
            reporter.info("Removing client source directory as split sources is disabled.");
            files.deleteDirectory(projectDir.resolve("src/client"));
        }

        String newFolderPath = ctx.data().getAsString(MavenProjectKeys.GROUP_ID).replace('.', '/') +
            "/" +
            ctx.data().getAsString(MinecraftProjectKeys.MOD_ID);

        reporter.info("Renaming packages to " + newFolderPath);
        Path mainJava = projectDir.resolve("src/main/java");
        Path newMainJava = mainJava.resolve(newFolderPath);
        files.createDirectories(newMainJava);

        reporter.info("Moving source files...");
        if(files.exists(mainJava.resolve("com/example/examplemod"))) {
            Path oldMainJava = mainJava.resolve("com/example/examplemod");
            files.extractDirectoryContents(oldMainJava, newMainJava);

            // if 'com/example/examplemod' is empty, delete it
            // if 'com/example' is empty, delete it
            // if 'com' is empty, delete it
            if (files.isDirectoryEmpty(oldMainJava)) {
                reporter.info("Cleaning up empty directories...");
                files.delete(oldMainJava);
                Path example = mainJava.resolve("example");
                if (files.isDirectoryEmpty(example)) {
                    files.delete(example);
                    Path com = mainJava.resolve("com");
                    if (files.isDirectoryEmpty(com)) {
                        files.delete(com);
                    }
                }
            }
        } else {
            Path oldMainJava = mainJava.resolve("com/example");
            files.extractDirectoryContents(oldMainJava, newMainJava);

            // if 'com/example' is empty, delete it
            // if 'com' is empty, delete it
            if (files.isDirectoryEmpty(oldMainJava)) {
                reporter.info("Cleaning up empty directories...");
                files.delete(oldMainJava);
                Path com = mainJava.resolve("com");
                if (files.isDirectoryEmpty(com)) {
                    files.delete(com);
                }
            }
        }

        if(splitSources) {
            reporter.info("Renaming client packages to " + newFolderPath);
            Path clientJava = projectDir.resolve("src/client/java");
            Path newClientJava = clientJava.resolve(newFolderPath);
            files.createDirectories(newClientJava);

            reporter.info("Moving client source files...");
            Path oldClientJava = clientJava.resolve("com/example");
            files.extractDirectoryContents(oldClientJava, newClientJava);

            // if 'com/example' is empty, delete it
            // if 'com' is empty, delete it
            if (files.isDirectoryEmpty(oldClientJava)) {
                reporter.info("Cleaning up empty client directories...");
                files.delete(oldClientJava);
                Path com = clientJava.resolve("com");
                if (files.isDirectoryEmpty(com)) {
                    files.delete(com);
                }
            }
        }
    }
}
