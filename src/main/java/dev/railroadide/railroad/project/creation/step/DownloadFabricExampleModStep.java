package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.ChecksumService;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;

import java.net.URI;
import java.nio.file.Path;

/**
 * @param checksum TODO: Possibly consider holding some known checksums for example mods?
 */
public record DownloadFabricExampleModStep(HttpService http, FilesService files, ZipService zip,
                                           ChecksumService checksum) implements CreationStep {
    @Override
    public String id() {
        return "railroad:download_fabric_example_mod";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.downloading_example_mod";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        MinecraftVersion mcVersion = ctx.get(ProjectContextKeys.MDK_VERSION);
        URI url = URI.create("https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/" + mcVersion.id() + ".zip");
        Path zipPath = ctx.projectDir().resolve("example-mod.zip");

        reporter.info("Downloading example mod from " + url);
        http.download(url, zipPath);
    }
}
