package io.github.railroad.settings.handler;

import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.localization.Language;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class SettingsManager {
    private final ObservableMap<Class<?>, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();
    private final Settings settings = new Settings();

    public SettingsManager() {
        defaultCodecs();
        defaultSettings();
    }

    public void registerCodec(SettingCodec<?, ?, ?> codec) {
        Railroad.LOGGER.info("Registering codec for type: {}", codec.getType());
        var res = codecs.put(codec.getType(), codec);
        if (res != null) {
            Railroad.LOGGER.warn("WARNING: SettingCodec for type: {} has been overwritten! This may cause problems!", codec.getType());
        }
    }

    public Setting<?> getSetting(String id) {
        return settings.getSetting(id);
    }

    public SettingCodec<?, ?, ?> getCodec(Class<?> type) {
        return codecs.get(type);
    }

    public TreeView createTree() {
        var tree = new TreeView<>(new TreeItem<>("Settings"));

        for (String id : settings.getSettingsMap().keySet()) {
            var setting = getSetting(id);
            SettingCodec codec = getCodec(setting.getType());

            if (codec == null) {
                Railroad.LOGGER.warn("No codec found for setting: {}", id);
                continue;
            }

            var item = codec.getNodeCreator().apply(setting.getDefaultValue());
            ((Node)item).addEventHandler(setting.getEventType(), setting.getEventHandler());
            ((Node)item).addEventHandler(ActionEvent.ANY, e -> {
                //FIXME stupid generics
                setting.setValue(codec.getNodeToValFunction().apply(item));
            });

            tree.getRoot().getChildren().add(new TreeItem(item));
        }

        return tree;
    }

    private void defaultCodecs() {
        Railroad.LOGGER.info("Registering default codecs");
        registerCodec(new SettingCodec<>(Language.class, ComboBox.class, JsonPrimitive.class,
                comboBox -> Language.fromName((String) comboBox.getValue()),
                (comboBox, language) -> comboBox.setValue(language.getName()),
                language -> {
                    var nc = new ComboBox<>();

                    var longest = "";
                    for (Language value : Language.values()) {
                        nc.getItems().add(value.getName());
                        if (value.getName().length() > longest.length()) {
                            longest = value.getName();
                        }
                    }
                    nc.setPrefSize(longest.length() * 10, 30);
                    nc.setValue(((Language) language).getName());
                    return nc;
                },
                language -> new JsonPrimitive(language.getName()),
                json -> Language.fromName(json.getAsString())
                ));
    }

    private void defaultSettings() {
        Railroad.LOGGER.info("Registering default settings");
        settings.registerSetting(
                new Setting<>("railroad:language.select", Language.class, Language.EN_US,
                        Event.ANY,
                        event -> Railroad.LOGGER.info("Language changed to: {}", ((ComboBox) event.getSource()).getValue())));
    }
}