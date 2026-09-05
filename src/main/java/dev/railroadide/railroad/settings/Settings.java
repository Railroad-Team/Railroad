package dev.railroadide.railroad.settings;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettings;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettingsState;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Language;
import dev.railroadide.railroad.localization.Languages;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.spi.PluginDescriptor;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.theme.ThemeManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.railroadide.railroad.settings.handler.SettingsHandler.registerSetting;

/** Central definitions for Railroad's built-in application settings. */
public final class Settings {
    private Settings() {
        // utility class
    }

    /** Active theme identifier. */
    public static final Setting<String> THEME = registerSetting(Setting.builder(String.class, "railroad:theme")
        .treePath("appearance")
        .category(SettingCategory.simple("railroad:appearance.themes"))
        .codec(SettingCodecs.THEME)
        .defaultValue("default-dark")
        .noTitle()
        .noDescription()
        .addListener((oldValue, newValue) -> ThemeManager.setTheme(newValue))
        .canBeNull(false)
        .build());

    /** Active application language. */
    public static final Setting<Language> LANGUAGE = registerSetting(
        Setting.builder(Language.class, "railroad:language")
            .treePath("general")
            .category(SettingCategory.simple("railroad:general.language"))
            .codec(SettingCodecs.LANGUAGE)
            .defaultValue(Languages.EN_US)
            .noTitle()
            .noDescription()
            .addListener((oldValue, newValue) -> L18n.loadLanguage(newValue))
            .canBeNull(false)
            .build());

    /** Whether the editor automatically pairs delimiters inside string literals. */
    public static final Setting<Boolean> AUTO_PAIR_INSIDE_STRINGS = registerSetting(
        Setting.builder(Boolean.class, "railroad:auto_pair_inside_strings")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .build());

    /** Maximum number of simultaneously open editor tabs. */
    public static final Setting<Integer> EDITOR_TAB_LIMIT = registerSetting(
        Setting.builder(Integer.class, "railroad:editor_tab_limit")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.editor_tabs"))
            .codec(DefaultSettingCodecs.NON_NEGATIVE_INTEGER)
            .defaultValue(20)
            .canBeNull(false)
            .build());

    /** Maximum number of recently closed editor tabs retained for reopening. */
    public static final Setting<Integer> RECENTLY_CLOSED_TAB_LIMIT = registerSetting(
        Setting.builder(Integer.class, "railroad:recently_closed_tab_limit")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.editor_tabs"))
            .codec(DefaultSettingCodecs.NON_NEGATIVE_INTEGER)
            .defaultValue(20)
            .canBeNull(false)
            .build());

    /** Whether files open in preview tabs. */
    public static final Setting<Boolean> ENABLE_PREVIEW_TABS = registerSetting(
        Setting.builder(Boolean.class, "railroad:enable_preview_tabs")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.editor_tabs"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .canBeNull(false)
            .build());

    /** Whether the project explorer follows the active editor tab. */
    public static final Setting<Boolean> SYNCHRONIZE_PROJECT_EXPLORER_WITH_ACTIVE_TAB = registerSetting(
        Setting.builder(Boolean.class, "railroad:synchronize_project_explorer_with_active_tab")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.project_explorer"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(false)
            .canBeNull(false)
            .build());

    /** Whether importing a project switches to the IDE workspace afterward. */
    public static final Setting<Boolean> SWITCH_TO_IDE_AFTER_IMPORT = registerSetting(
        Setting.builder(Boolean.class, "railroad:switch_to_ide_after_import")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.import"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .build());

    /** Default group ID used when creating projects. */
    public static final Setting<String> DEFAULT_PROJECT_GROUP_ID = registerSetting(
        Setting.builder(String.class, "railroad:projects.default_group_id")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.defaults.group_id"))
            .codec(DefaultSettingCodecs.STRING)
            .defaultValue("")
            .canBeNull(true)
            .build());

    /** Default project version used when creating projects. */
    public static final Setting<String> DEFAULT_PROJECT_VERSION = registerSetting(
        Setting.builder(String.class, "railroad:projects.default_version")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.defaults.version"))
            .codec(DefaultSettingCodecs.STRING)
            .defaultValue("")
            .canBeNull(true)
            .build());

    /** Default author used when creating projects. */
    public static final Setting<String> DEFAULT_PROJECT_AUTHOR = registerSetting(
        Setting.builder(String.class, "railroad:projects.default_author")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.defaults.author"))
            .codec(DefaultSettingCodecs.STRING)
            .defaultValue("")
            .canBeNull(true)
            .build());

    /** User-defined and default keyboard and mouse bindings. */
    public static final Setting<Map<String, List<KeybindData>>> KEYBINDS = registerSetting(
        Setting.builder(new TypeToken<Map<String, List<KeybindData>>>() {
        }, "railroad:keybinds")
            .hasTitle(false)
            .hasDescription(false)
            .category(SettingCategory.builder("railroad:keybinds")
                .noDescription()
                .build())
            .defaultValue(KeybindHandler.getDefaults())
            .canBeNull(false)
            .codec(SettingCodecs.KEYBINDS)
            .treePath("keybinds")
            .build());

    /** Runtime enabled state for loaded plugins. */
    public static final Setting<Map<PluginDescriptor, Boolean>> ENABLED_PLUGINS = registerSetting(
        Setting.builder((Class<Map<PluginDescriptor, Boolean>>) (Class<?>) Map.class, "railroad:enabled_plugins")
            .treePath("plugins")
            .category(SettingCategory.builder("railroad:plugins")
                .noDescription()
                .build())
            .codec(SettingCodecs.ENABLED_PLUGINS)
            .noTitle()
            .noDescription()
            .persisted(false)
            .addListener((oldValue, newValue) -> {
                if (newValue == null) {
                    newValue = new HashMap<>();
                }

                for (Map.Entry<PluginDescriptor, Boolean> entry : newValue.entrySet()) {
                    PluginDescriptor plugin = entry.getKey();
                    boolean enabled = entry.getValue();

                    if (PluginManager.isPluginEnabledForce(plugin) != enabled) {
                        if (enabled) {
                            PluginManager.enablePlugin(plugin);
                        } else {
                            PluginManager.disablePlugin(plugin);
                        }
                    }
                }
            })
            .defaultValue(new HashMap<>())
            .build());

    /** Additional directories searched for installed JDKs. */
    public static final Setting<List<Path>> ADDITIONAL_JDK_SCAN_PATHS = registerSetting(
        Setting.builder(new TypeToken<List<Path>>() {
        }, "railroad:additional_jdk_scan_paths")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.jdk_management"))
            .codec(SettingCodecs.DIRECTORY_PATH_LIST)
            .defaultValue(List.of())
            .build());

    /** Directories excluded from JDK discovery. */
    public static final Setting<List<Path>> EXCLUDED_JDK_SCAN_PATHS = registerSetting(
        Setting.builder(new TypeToken<List<Path>>() {
        }, "railroad:excluded_jdk_scan_paths")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.jdk_management"))
            .codec(SettingCodecs.DIRECTORY_PATH_LIST)
            .defaultValue(List.of())
            .build());

    /** Explicit JDK executable files added to the available JDK list. */
    public static final Setting<List<Path>> ADDITIONAL_JDKS = registerSetting(
        Setting.builder(new TypeToken<List<Path>>() {
        }, "railroad:additional_jdks")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.jdk_management"))
            .codec(SettingCodecs.FILE_PATH_LIST)
            .defaultValue(List.of())
            .build());

    /** Current Java inspection rule configuration. */
    public static final Setting<JavaInspectionRuleSettingsState> JAVA_INSPECTION_RULE_SETTINGS = registerSetting(
        Setting.builder(JavaInspectionRuleSettingsState.class, "railroad:java_inspection_rule_settings")
            .treePath("ide")
            .category(SettingCategory.builder("railroad:ide.inspections")
                .title("Inspection Rules")
                .noDescription()
                .build())
            .codec(SettingCodecs.JAVA_INSPECTION_RULE_SETTINGS)
            .defaultValue(JavaInspectionRuleSettingsState.snapshot())
            .persisted(false)
            .noTitle()
            .noDescription()
            .canBeNull(false)
            .addListener((oldValue, newValue) -> {
                if (newValue == null) {
                    newValue = JavaInspectionRuleSettingsState.empty();
                }
                JavaInspectionRuleSettings.replaceAll(newValue);
                ConfigHandler.saveConfig();
            })
            .build());

    /** Timeout in milliseconds for detecting a Java version. */
    public static final Setting<Long> JAVA_VERSION_DETECTION_TIMEOUT_MS = registerSetting(
        Setting.builder(Long.class, "railroad:java_version_detection_timeout_ms")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.jdk_management"))
            .codec(DefaultSettingCodecs.LONG)
            .defaultValue(3000L)
            .build());

    /** Optional path to the Windows Terminal settings file. */
    public static final Setting<Path> WINDOWS_TERMINAL_SETTINGS_PATH = registerSetting(
        Setting.builder(Path.class, "railroad:windows_terminal_settings_path")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad:appearance.terminal"))
            .codec(SettingCodecs.WINDOWS_TERMINAL_SETTINGS_PATH)
            .defaultValue(null)
            .canBeNull(true)
            .build());

    /** Strategy used to select the terminal font. */
    public static final Setting<TerminalFontMode> TERMINAL_FONT_MODE = registerSetting(
        Setting.builder(TerminalFontMode.class, "railroad:terminal_font_mode")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad:appearance.terminal"))
            .codec(SettingCodecs.TERMINAL_FONT_MODE)
            .defaultValue(TerminalFontMode.AUTO)
            .canBeNull(false)
            .build());

    /** Optional installed font selected for the terminal. */
    public static final Setting<String> TERMINAL_INSTALLED_FONT = registerSetting(
        Setting.builder(String.class, "railroad:terminal_installed_font")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad:appearance.terminal"))
            .codec(SettingCodecs.INSTALLED_FONT)
            .defaultValue(null)
            .canBeNull(true)
            .build());

    /** Optional custom terminal font family name. */
    public static final Setting<String> TERMINAL_CUSTOM_FONT_FAMILY = registerSetting(
        Setting.builder(String.class, "railroad:terminal_custom_font_family")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad:appearance.terminal"))
            .codec(SettingCodecs.TERMINAL_CUSTOM_FONT_FAMILY)
            .defaultValue("")
            .canBeNull(true)
            .build());

    /** Timeout in milliseconds for searching for the Git executable. */
    public static final Setting<Long> GIT_BINARY_SEARCH_COMMAND_TIMEOUT_MS = registerSetting(
        Setting.builder(Long.class, "railroad:git_binary_command_search_timeout_ms")
            .treePath("vcs")
            .category(SettingCategory.simple("railroad:vcs"))
            .codec(DefaultSettingCodecs.LONG)
            .defaultValue(5000L)
            .build());

    /** Timeout in milliseconds for running Git version detection. */
    public static final Setting<Long> GIT_VERSION_COMMAND_TIMEOUT_MS = registerSetting(
        Setting.builder(Long.class, "railroad:git_version_command_timeout_ms")
            .treePath("vcs")
            .category(SettingCategory.simple("railroad:vcs"))
            .codec(DefaultSettingCodecs.LONG)
            .defaultValue(5000L)
            .build());

    /** Optional explicitly selected Git executable. */
    public static final Setting<Path> GIT_EXECUTABLE_PATH = registerSetting(
        Setting.builder(Path.class, "railroad:git_executable_path")
            .treePath("vcs")
            .category(SettingCategory.simple("railroad:vcs"))
            .codec(SettingCodecs.GIT_EXECUTABLE_PATH)
            .defaultValue(null)
            .canBeNull(true)
            .build());

    /** Editor indentation character mode. */
    public static final Setting<IndentMode> INDENT_MODE = registerSetting(
        Setting.builder(IndentMode.class, "railroad:indent_mode")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(DefaultSettingCodecs.ofEnum("railroad:indent_mode", IndentMode.class))
            .defaultValue(IndentMode.TABS)
            .canBeNull(false)
            .build());

    /** Number of columns represented by one indentation level. */
    public static final Setting<Integer> INDENT_WIDTH = registerSetting(
        Setting.builder(Integer.class, "railroad:indent_width")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(DefaultSettingCodecs.INTEGER)
            .defaultValue(4)
            .canBeNull(false)
            .build());

    /** Number of columns represented by a tab character. */
    public static final Setting<Integer> TAB_WIDTH = registerSetting(
        Setting.builder(Integer.class, "railroad:tab_width")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(DefaultSettingCodecs.INTEGER)
            .defaultValue(4)
            .canBeNull(false)
            .build());

    /** Font family used by editor controls. */
    public static final Setting<String> EDITOR_FONT_FAMILY = registerSetting(
        Setting.builder(String.class, "railroad:editor_font_family")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(SettingCodecs.EDITOR_FONT_FAMILY)
            .defaultValue("JetBrains Mono")
            .canBeNull(false)
            .build());

    /** Whether the last project opens automatically at startup. */
    public static final Setting<Boolean> OPEN_LAST_PROJECT_ON_START = registerSetting(
        Setting.builder(Boolean.class, "railroad:open_last_project_on_start")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.project"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(false)
            .build());

    /** Triggers class initialization so all built-in settings are registered. */
    public static void initialize() {
        // intentionally empty - triggers class loading and static init
    }
}
