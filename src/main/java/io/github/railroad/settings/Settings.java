package io.github.railroad.settings;

import io.github.railroad.Railroad;
import io.github.railroad.core.localization.Language;
import io.github.railroad.core.settings.DefaultSettingCodecs;
import io.github.railroad.core.settings.Setting;
import io.github.railroad.core.settings.SettingCategory;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Languages;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.railroadpluginapi.PluginDescriptor;

import java.util.HashMap;
import java.util.Map;

import static io.github.railroad.settings.handler.SettingsHandler.registerSetting;

public class Settings {
    public static final Setting<Language> LANGUAGE = registerSetting(Setting.builder(Language.class, "railroad:language")
            .treePath("general")
            .category(SettingCategory.simple("railroad:general.language"))
            .codec(SettingCodecs.LANGUAGE)
            .defaultValue(Languages.EN_US)
            .noTitle()
            .noDescription()
            .addListener((oldValue, newValue) -> L18n.loadLanguage(newValue))
            .canBeNull(false)
            .build());

    public static final Setting<String> THEME = registerSetting(Setting.builder(String.class, "railroad:theme")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad:appearance.themes"))
            .codec(SettingCodecs.THEME)
            .defaultValue("default-dark")
            .noTitle()
            .noDescription()
            .addListener((oldValue, newValue) -> Railroad.updateTheme(newValue))
            .canBeNull(false)
            .build());

    public static final Setting<Boolean> AUTO_PAIR_INSIDE_STRINGS = registerSetting(Setting.builder(Boolean.class, "railroad:auto_pair_inside_strings")
            .treePath("ide")
            .category(SettingCategory.simple("railroad:ide.code_style"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .build());

    public static final Setting<Boolean> SWITCH_TO_IDE_AFTER_IMPORT = registerSetting(Setting.builder(Boolean.class, "railroad:switch_to_ide_after_import")
            .treePath("projects")
            .category(SettingCategory.simple("railroad:projects.import"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .build());

    public static final Setting<Map<PluginDescriptor, Boolean>> ENABLED_PLUGINS = registerSetting(Setting.builder((Class<Map<PluginDescriptor, Boolean>>) (Class<?>) Map.class, "railroad:enabled_plugins")
            .treePath("plugins")
            .category(SettingCategory.builder("railroad:plugins")
                    .noDescription()
                    .build())
            .codec(SettingCodecs.ENABLED_PLUGINS)
            .noTitle()
            .noDescription()
            .addListener((oldValue, newValue) -> {
                if(newValue == null) {
                    newValue = new HashMap<>();
                }

                for (Map.Entry<PluginDescriptor, Boolean> entry : newValue.entrySet()) {
                    PluginDescriptor plugin = entry.getKey();
                    boolean enabled = entry.getValue();

                    if(PluginManager.isPluginEnabledForce(plugin) != enabled) {
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

    public static void initialize() {}
}
