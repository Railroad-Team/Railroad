package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.ChecksumService;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;

import java.net.URI;
import java.nio.file.Path;

public record DownloadForgeMdkStep(HttpService http, FilesService files, ZipService zip, ChecksumService checksum) implements CreationStep {
    @Override
    public String id() {
        return "railroad:download_forge_mdk";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.download_forge_mdk";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Downloading Forge MDK...");

        String forgeVersion = ctx.data().getAsString(ForgeProjectKeys.FORGE_VERSION);
        if(forgeVersion == null)
            throw new IllegalStateException("Forge version is not set");

        Path projectDir = ctx.projectDir();

        String mdkUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + forgeVersion + "/forge-" + forgeVersion + "-mdk.zip";
        String sha256Url = mdkUrl + ".sha256";
        Path mdkPath = projectDir.resolve("forge-mdk.zip");
        http.download(new URI(mdkUrl), mdkPath);

        reporter.info("Verifying Forge MDK checksum...");
        Path mdkSha256Path = projectDir.resolve("forge-mdk.zip.sha256");
        http.download(new URI(sha256Url), mdkSha256Path);
        String expectedChecksum = files.readString(mdkSha256Path).trim();
        if(!checksum.verify(mdkPath, "SHA-256", expectedChecksum)) {
            reporter.info("Cleaning up invalid Forge MDK files...");
            files.delete(mdkPath);
            files.delete(mdkSha256Path);
            throw new IllegalStateException("Downloaded Forge MDK checksum does not match expected value");
        }

        reporter.info("Cleanup temporary files...");
        files.delete(mdkSha256Path);
    }
}
