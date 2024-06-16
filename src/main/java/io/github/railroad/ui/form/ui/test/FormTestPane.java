package io.github.railroad.ui.form.ui.test;

import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.form.Form;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormSection;
import io.github.railroad.ui.form.ValidationResult;
import io.github.railroad.ui.form.impl.CheckBoxComponent;
import io.github.railroad.ui.form.impl.ComboBoxComponent;
import io.github.railroad.ui.form.impl.DirectoryChooserComponent;
import io.github.railroad.ui.form.impl.TextFieldComponent;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class FormTestPane extends RRVBox {
    private final ObjectProperty<TextField> modidProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitRepoProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<ForgeVersion>> forgeVersionProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> directoryProperty = new SimpleObjectProperty<>();

    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);

    public FormTestPane() {
        super();
        setPadding(new Insets(20));

        TextFieldComponent modidComponent = FormComponent.textField("railroad.home.project.modid")
                .bindTextFieldTo(modidProperty)
                .required()
                .validator(textField -> {
                    if (textField.getText().length() < 3) {
                        return ValidationResult.error("Mod ID must be at least 3 characters long");
                    }

                    if (textField.getText().contains(" ")) {
                        return ValidationResult.error("Mod ID cannot contain spaces");
                    }

                    if (!textField.getText().matches("[a-z][a-z0-9_-]*")) {
                        return ValidationResult.error("Mod ID must start with a letter and only contain lowercase letters, numbers, and underscores");
                    }

                    if (textField.getText().length() > 64) {
                        return ValidationResult.error("Mod ID must be at most 64 characters long");
                    }

                    if (textField.getText().equalsIgnoreCase("minecraft")) {
                        return ValidationResult.error("Mod ID cannot be 'minecraft'");
                    }

                    // Use regex to do this instead
                    if (textField.getText().contains("__") || textField.getText().contains("--")) {
                        return ValidationResult.warning("Mod ID probably shouldn't contain double underscores or hyphens");
                    }
                    if (textField.getText().contains("-_") || textField.getText().contains("_-")) {
                        return ValidationResult.warning("Mod ID probably shouldn't contain a hyphen and underscore next to each other");
                    }

                    if (textField.getText().equalsIgnoreCase("forge") || textField.getText().equalsIgnoreCase("fabric")) {
                        return ValidationResult.warning("Mod ID probably shouldn't be 'forge' or 'fabric'");
                    }

                    if (textField.getText().contains("tutorial")) {
                        return ValidationResult.warning("Unless you are a tutorial maker, you probably shouldn't name your mod 'tutorial'");
                    }

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Mod ID: " + newValue);
                })
                .keyTypedHandler(event -> {
                    String text = modidProperty.get().getText();
                    hasTypedInModid.set(text != null && !text.isBlank());
                })
                .addTransformer(this.modidProperty, this.modNameProperty, str -> {
                    if (hasTypedInModName.get())
                        return this.modNameProperty.get().getText();

                    // Replace - and _ with space
                    String modName = str.replace("-", " ").replace("_", " ");
                    if (modName.isBlank())
                        return "";

                    // Capitalize first letter of each word
                    var sb = new StringBuilder();
                    for (String word : modName.split(" ")) {
                        sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1)).append(" ");
                    }

                    // Trim any leading/trailing whitespace
                    return sb.toString().trim();
                })
                .build();

        TextFieldComponent modNameComponent = FormComponent.textField("railroad.home.project.modname")
                .bindTextFieldTo(modNameProperty)
                .required()
                .validator(textField -> {
                    if (textField.getText().length() < 3) {
                        return ValidationResult.error("Mod Name must be at least 3 characters long");
                    }

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Mod Name: " + newValue);
                })
                .keyTypedHandler(event -> {
                    String text = modNameProperty.get().getText();
                    hasTypedInModName.set(text != null && !text.isBlank());
                })
                .addTransformer(this.modNameProperty, this.modidProperty, str -> {
                    if (hasTypedInModid.get())
                        return this.modidProperty.get().getText();

                    if (str.isBlank())
                        return "";

                    // Replace space with -
                    return str.toLowerCase(Locale.ROOT).replace(" ", "_").replaceAll("[^a-z0-9-_]", "");
                })
                .build();

        CheckBoxComponent createGitRepoComponent = FormComponent.checkBox("railroad.home.project.git")
                .bindCheckBoxTo(createGitRepoProperty)
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Create Git Repo: " + newValue);
                })
                .build();
        //createGitRepoComponent.getComponent().addInformationLabel("This will create a git repository for your project");

        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("railroad.home.minecraft.version", MinecraftVersion.class)
                .items(MinecraftVersion.getVersions())
                .required()
                .keyFunction(MinecraftVersion::id)
                .valueOfFunction(str -> MinecraftVersion.fromId(str).orElseGet(MinecraftVersion::getLatestStableVersion))
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Minecraft Version: " + newValue);

                    forgeVersionProperty.get().getItems().setAll(ForgeVersion.getVersions(newValue));
                })
                .translate(false)
                .bindComboBoxTo(minecraftVersionProperty)
                .addTransformer(this.minecraftVersionProperty, this.forgeVersionProperty, ForgeVersion::getLatestVersion)
                .build();

        ComboBoxComponent<ForgeVersion> forgeVersionComponent = FormComponent.comboBox("railroad.home.project.forge.version", ForgeVersion.class)
                .items(ForgeVersion.getVersions(MinecraftVersion.getLatestStableVersion()))
                .required()
                .keyFunction(ForgeVersion::id)
                .valueOfFunction(str -> ForgeVersion.fromId(str).orElse(null))
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Forge Version: " + newValue);
                })
                .translate(false)
                .bindComboBoxTo(forgeVersionProperty)
                .build();

        DirectoryChooserComponent directoryChooserComponent = FormComponent.directoryChooser("railroad.home.project.location")
                .listener((node, observable, oldValue, newValue) -> {
                    System.out.println("Directory: " + newValue);
                })
                .bindTextFieldTo(directoryProperty)
                .required()
                .defaultPath(System.getProperty("user.home"))
                .validator(textField -> {
                    try {
                        Path path = Path.of(textField.getText());
                        if (Files.notExists(path))
                            return ValidationResult.error("Directory does not exist");

                        if (!Files.isReadable(path))
                            return ValidationResult.error("Directory is not readable");

                        if (!Files.isWritable(path))
                            return ValidationResult.error("Directory is not writable");

                        if (!Files.isDirectory(path))
                            return ValidationResult.error("Path is not a directory");
                    } catch (Exception exception) {
                        return ValidationResult.error("Invalid path");
                    }

                    return ValidationResult.ok();
                })
                .build();
        directoryChooserComponent.getComponent().addInformationLabel("railroad.home.project.tooltips.projectcreated");

        Form form = Form.create()
                .appendSection(FormSection.create("railroad.home.minecraft")
                        .appendComponent(modidComponent)
                        .appendComponent(modNameComponent)
                        .appendComponent(createGitRepoComponent)
                        .appendComponent(minecraftVersionComponent)
                        .appendComponent(forgeVersionComponent)
                        .appendComponent(directoryChooserComponent)
                        .build())
                .build();

        getChildren().add(form.createUI());
    }
}
