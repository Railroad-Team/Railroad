package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.railroad.project.DisplayTest;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.core.project.ProjectData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record UpdateForgeModsTomlStep(FilesService files) implements CreationStep {
    private static final Pattern TOML_COMMENT_PATTERN = Pattern.compile("^#(\\w+=)|(\\[.+\\])");

    @Override
    public String id() {
        return "railroad:update_forge_mods_toml";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.update_forge_mods_toml";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Updating mods.toml...");

        Path modsTomlPath = ctx.projectDir().resolve("src/main/resources/META-INF/mods.toml");
        if (!files.exists(modsTomlPath))
            throw new IllegalStateException("mods.toml not found at " + modsTomlPath);

        boolean hasIssues = ctx.data().contains(ProjectData.DefaultKeys.ISSUES_URL);
        boolean hasUpdateJson = ctx.data().contains(ForgeProjectKeys.UPDATE_JSON_URL);
        boolean hasDisplayUrl = ctx.data().contains(ForgeProjectKeys.DISPLAY_URL);
        boolean hasCredits = ctx.data().contains(ProjectData.DefaultKeys.CREDITS);
        DisplayTest displayTest = ctx.data().getAsEnum(ForgeProjectKeys.DISPLAY_TEST, DisplayTest.class, DisplayTest.MATCH_VERSION);
        boolean clientSideOnly = ctx.data().getAsBoolean(ForgeProjectKeys.CLIENT_SIDE_ONLY, false);

        List<String> lines = files.readLines(modsTomlPath);
        lines = lines.stream()
            .filter(line -> line != null && (!line.startsWith("#") || TOML_COMMENT_PATTERN.matcher(line).find()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("#issueTrackerURL=") && hasIssues) {
                lines.set(index, "issueTrackerURL=\"" + ctx.data().getAsString(ProjectData.DefaultKeys.ISSUES_URL) + "\"");
            } else if (line.startsWith("#updateJSONURL=") && hasUpdateJson) {
                lines.set(index, "updateJSONURL=\"" + ctx.data().getAsString(ForgeProjectKeys.UPDATE_JSON_URL) + "\"");
            } else if (line.startsWith("#displayURL=") && hasDisplayUrl) {
                lines.set(index, "displayURL=\"" + ctx.data().getAsString(ForgeProjectKeys.DISPLAY_URL) + "\"");
            } else if (line.startsWith("#displayTest=")) {
                lines.set(index, "displayTest=\"" + displayTest.name() + "\"");
            } else if (line.startsWith("#credits=") && hasCredits) {
                lines.set(index, "credits=\"" + ctx.data().getAsString(ProjectData.DefaultKeys.CREDITS) + "\"");
            } else if (line.startsWith("#clientSideOnly=")) {
                lines.set(index, "clientSideOnly=" + clientSideOnly);
            }
        }
    }
}
