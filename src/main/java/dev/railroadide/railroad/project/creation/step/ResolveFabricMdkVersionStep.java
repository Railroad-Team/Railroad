package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class ResolveFabricMdkVersionStep implements CreationStep {
    @Override
    public String id() {
        return "railroad:resolve_fabric_mdk_version";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.resolving_mdk_version";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) {
        MinecraftVersion requested = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
        if (requested == null)
            throw new IllegalStateException("Minecraft version is not specified.");

        reporter.info("Resolving MDK version for " + requested.id() + "...");
        MinecraftVersion resolved = resolveMdkVersion(requested);
        ctx.put(ProjectContextKeys.MDK_VERSION, resolved);
        ctx.put(ProjectContextKeys.EXAMPLE_MOD_BRANCH, resolved.id());
    }

    private MinecraftVersion resolveMdkVersion(MinecraftVersion version) {
        MinecraftVersion.Type type = version.getType();
        if (type == MinecraftVersion.Type.OLD_ALPHA || type == MinecraftVersion.Type.OLD_BETA)
            throw new IllegalStateException("Fabric does not support Minecraft versions older than 1.14.");

        if (type != MinecraftVersion.Type.SNAPSHOT)
            return version;

        String id = version.id();
        int dashIndex = id.indexOf('-');
        if (dashIndex > 0) {
            String releaseId = id.substring(0, dashIndex);
            Optional<MinecraftVersion> release = fetchVersion(releaseId);
            if (release.isPresent())
                return release.get();
        }

        List<MinecraftVersion> versions = fetchAllVersions();
        long releaseTime = version.releaseTime().toEpochSecond(ZoneOffset.UTC);
        MinecraftVersion closest = null;
        for (MinecraftVersion candidate : versions) {
            if (candidate.getType() != MinecraftVersion.Type.RELEASE)
                continue;

            if (closest == null) {
                closest = candidate;
                continue;
            }

            long epochSecond = candidate.releaseTime().toEpochSecond(ZoneOffset.UTC);
            long candidateDiff = Math.abs(epochSecond - releaseTime);
            long closestDiff = Math.abs(epochSecond - releaseTime);
            if (candidateDiff < closestDiff) {
                closest = candidate;
            }
        }

        if (closest != null)
            return closest;

        throw new IllegalStateException("Fabric does not support Minecraft versions older than 1.14.");
    }

    private Optional<MinecraftVersion> fetchVersion(String id) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving MDK version", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to resolve MDK version", exception);
        }
    }

    private List<MinecraftVersion> fetchAllVersions() {
        try {
            return SwitchboardRepositories.MINECRAFT.getAllVersionsSync();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving MDK version", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to resolve MDK version", exception);
        }
    }
}
