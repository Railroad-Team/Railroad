package dev.railroadide.railroad.settings;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Languages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsSearchHandlerTest {
    @BeforeAll
    public static void loadLanguage() {
        L18n.loadLanguage(Languages.EN_US);
    }

    @Test
    public void searchesCategoryTextWhenTheEditorHasNoTitle() {
        var setting = Setting.builder(String.class, "test:language")
            .treePath("general")
            .category(SettingCategory.simple("railroad:general.language"))
            .codec(DefaultSettingCodecs.STRING)
            .defaultValue("")
            .noTitle()
            .noDescription()
            .build();
        var search = new SettingsSearchHandler(List.of(setting));
        assertEquals("general", search.mostRelevantFolder(" LANGUAGE "));
        assertNull(search.mostRelevantFolder("   "));
        assertNull(search.mostRelevantFolder("zzzzzzzzzzzzzz"));
    }

    @Test
    public void searchesActualSettingTitleAndPreservesNestedPath() {
        var setting = Setting.builder(String.class, "test:language")
            .treePath("plugin.preferences.language")
            .title("railroad.settings.general.language.title")
            .description("railroad.settings.general.language.description")
            .category(SettingCategory.builder("test:empty").noTitle().noDescription().build())
            .codec(DefaultSettingCodecs.STRING)
            .defaultValue("")
            .build();
        var search = new SettingsSearchHandler(List.of(setting));
        assertEquals("plugin.preferences.language", search.mostRelevantFolder("language"));
        assertEquals("plugin.preferences.language", search.mostRelevantFolder("Railroad interface"));
    }

    @Test
    public void findsBuiltInSettingsInTheirNewPages() {
        var search = new SettingsSearchHandler(List.of(Settings.THEME, Settings.EDITOR_FONT_FAMILY,
            Settings.COMPACT_MIDDLE_PACKAGES, Settings.ADDITIONAL_JDK_SCAN_PATHS,
            Settings.TERMINAL_FONT_MODE, Settings.JAVA_INSPECTION_RULE_SETTINGS, Settings.DEFAULT_PROJECT_AUTHOR));
        assertEquals("editor", search.mostRelevantFolder("Editor Font Family"));
        assertEquals("project_explorer", search.mostRelevantFolder("Compact Middle Packages"));
        assertEquals("java", search.mostRelevantFolder("Additional Scan Paths"));
        assertEquals("terminal", search.mostRelevantFolder("Terminal Font Mode"));
        assertEquals("inspections", search.mostRelevantFolder("Code Inspections"));
        assertEquals("projects", search.mostRelevantFolder("New Project Defaults"));
        assertEquals(Settings.DEFAULT_PROJECT_GROUP_ID.getCategory(), Settings.DEFAULT_PROJECT_AUTHOR.getCategory());
    }
}
