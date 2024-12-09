package io.github.railroad.settings.handler;

import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.localization.Language;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class SettingsManager {
    public static final SettingsManager INSTANCE = new SettingsManager();

    private final ObservableMap<String, Setting<?>> settings = FXCollections.observableHashMap();
    private final ObservableMap<Class<?>, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();

    public SettingsManager() {

    }

    public void registerSetting(Setting<?> setting) {
        settings.put(setting.getId(), setting);
    }

    public void registerCodec(SettingCodec<?, ?, ?> codec) {
        if (codecs.put(codec.getType(), codec) != null) {
            Railroad.LOGGER.warn("WARNING: SettingCodec for type: {} has been overwritten! This may cause unintended problems!", codec.getType());
        }
    }

    public Setting<?> getSetting(String id) {
        return settings.get(id);
    }

    public SettingCodec<?, ?, ?> getCodec(Class<?> type) {
        return codecs.get(type);
    }

    private void defaultCodecs() {
        registerCodec(new SettingCodec<>(Language.class, ComboBox.class, JsonPrimitive.class,
                comboBox -> Language.fromName((String) comboBox.getValue()),
                (comboBox, language) -> comboBox.setValue(language.getName()),
                language -> {
                    var nc = new ComboBox<>();
                    for (Language value : Language.values()) {
                        nc.getItems().add(value);
                    }
                    nc.setValue(language);
                    return nc;
                },
                language -> new JsonPrimitive(language.getName()),
                json -> Language.fromName(json.getAsString())
                ));
    }

    public TreeView createTree() {
        //find lowest level settings
        var tree = new TreeView<>();
        tree.setRoot(new TreeItem<>(null));

        for (String id : settings.keySet()) {
            Setting<?> setting = settings.get(id);
            SettingCodec<?, ?, ?> codec = getCodec(setting.getType());
            if (codec == null) {
                Railroad.LOGGER.warn("No codec found for setting: {}", id);
                continue;
            }
            //TODO fix this?????
            Node item = codec.getNodeCreator().apply(setting.getDefaultValue());
            tree.getRoot().getChildren().add(new TreeItem<>(item));
        }

        return tree;
    }
}