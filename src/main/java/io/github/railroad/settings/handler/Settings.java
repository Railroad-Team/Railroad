package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
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
    public static final Setting<Languages> LANGUAGE = registerSetting(Setting.builder(Languages.class, "railroad:language")
            .treePath("general")
            .category(SettingCategory.simple("railroad.settings.general.language"))
            .codec(SettingCodecs.LANGUAGE)
            .defaultValue(Languages.EN_US)
            .addListener($ -> L18n.loadLanguage())
            .canBeNull(false)
            .build());

    public static final Setting<String> THEME = registerSetting(Setting.builder(String.class, "railroad:theme")
            .treePath("appearance")
            .category(SettingCategory.simple("railroad.settings.themes"))
            .codec(SettingCodecs.THEME)
            .defaultValue("default-dark")
            .addListener(Railroad::updateTheme)
            .canBeNull(false)
            .build());

    public static final Setting<Boolean> AUTO_PAIR_INSIDE_STRINGS = registerSetting(Setting.builder(Boolean.class, "railroad:auto_pair_inside_strings")
            .treePath("ide")
            .category(SettingCategory.simple("railroad.settings.code_style"))
            .codec(DefaultSettingCodecs.BOOLEAN)
            .defaultValue(true)
            .build());

    public static final Setting<Map<PluginDescriptor, Boolean>> ENABLED_PLUGINS = registerSetting(Setting.builder((Class<Map<PluginDescriptor, Boolean>>) (Class<?>) Map.class, "railroad:enabled_plugins")
            .treePath("plugins")
            .category(SettingCategory.builder("railroad.settings.plugins")
                    .noTitle()
                    .noDescription()
                    .build())
            .codec(SettingCodecs.ENABLED_PLUGINS)
            .addListener(enabledPlugins -> {
                if(enabledPlugins == null) {
                    enabledPlugins = new HashMap<>();
                }

                for (Map.Entry<PluginDescriptor, Boolean> entry : enabledPlugins.entrySet()) {
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
