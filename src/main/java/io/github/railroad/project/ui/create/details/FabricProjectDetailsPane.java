package io.github.railroad.project.ui.create.details;

import io.github.railroad.minecraft.FabricAPIVersion;
import io.github.railroad.minecraft.FabricLoaderVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.RecommendableVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.minecraft.mapping.MappingHelper;
import io.github.railroad.minecraft.mapping.MappingVersion;
import io.github.railroad.project.License;
import io.github.railroad.project.ProjectType;
import io.github.railroad.project.data.FabricProjectData;
import io.github.railroad.project.data.ForgeProjectData;
import io.github.railroad.project.ui.create.widget.StarableListCell;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.form.*;
import io.github.railroad.ui.form.impl.*;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FabricProjectDetailsPane extends RRVBox {
    private final StringProperty createdAtPath = new SimpleStringProperty();

    private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> licenseCustomField = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<FabricLoaderVersion>> fabricLoaderVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> includeFapiCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<FabricAPIVersion>> fapiVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useAccessWidenerCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> splitSourcesCheckBox = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MappingChannel>> mappingChannelComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<MappingVersion>> mappingVersionComboBox = new SimpleObjectProperty<>();

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextArea> descriptionArea = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> issuesField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> homepageField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> sourcesField = new SimpleObjectProperty<>(); // optional

    private final ObjectProperty<TextField> groupIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> artifactIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> versionField = new SimpleObjectProperty<>();

    private final AtomicBoolean hasTypedInProjectName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    public FabricProjectDetailsPane() {
        TextFieldComponent projectNameComponent = FormComponent.textField("ProjectName", "railroad.project.creation.name")
                .required()
                .bindTextFieldTo(projectNameField)
                .promptText("railroad.project.creation.name.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.name.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.name.error.length_long");

                    if (text.length() < 3)
                        return ValidationResult.error("railroad.project.creation.name.error.length_short");

                    if (text.matches("[.<>:\"/\\\\|?*]"))
                        return ValidationResult.error("railroad.project.creation.name.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    String path = fixPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
                    createdAtPath.set(path);
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInProjectName.get() && !projectNameField.get().getText().isBlank())
                        hasTypedInProjectName.set(true);
                    else if (hasTypedInProjectName.get() && projectNameField.get().getText().isBlank())
                        hasTypedInProjectName.set(false);
                })
                .addTransformer(projectNameField, modIdField, text -> {
                    if (!hasTypedInModid.get() || modIdField.get().getText().isBlank())
                        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");

                    return text;
                })
                .addTransformer(projectNameField, modNameField, text -> {
                    if (!hasTypedInModName.get() || modNameField.get().getText().isBlank())
                        return text;

                    return text;
                })
                .build();

        DirectoryChooserComponent projectPathComponent = FormComponent.directoryChooser("ProjectPath", "railroad.project.creation.location")
                .required()
                .defaultPath(System.getProperty("user.home"))
                .bindTextFieldTo(projectPathField)
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.location.error.required");

                    if (!text.matches(".*[a-zA-Z0-9]"))
                        return ValidationResult.error("railroad.project.creation.location.error.invalid_characters");

                    try {
                        Path path = Path.of(text);
                        if (Files.notExists(path))
                            return ValidationResult.error("railroad.project.creation.location.error.not_exists");

                        if (!Files.isDirectory(path))
                            return ValidationResult.error("railroad.project.creation.location.error.not_directory");
                    } catch (InvalidPathException exception) {
                        return ValidationResult.error("railroad.project.creation.location.error.invalid_path");
                    }

                    if (text.contains("OneDrive"))
                        return ValidationResult.warning("railroad.project.creation.location.warning.onedrive");

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    String path = fixPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
                    createdAtPath.set(path);
                })
                .build();

        CheckBoxComponent createGitComponent = FormComponent.checkBox("CreateGit", "railroad.project.creation.git")
                .bindCheckBoxTo(createGitCheckBox)
                .build();

        ComboBoxComponent<License> licenseComponent = FormComponent.comboBox("License", "railroad.project.creation.license", License.class)
                .required()
                .bindComboBoxTo(licenseComboBox)
                .keyFunction(License::getName)
                .valueOfFunction(License::fromName)
                .translate(false)
                .items(Arrays.asList(License.values()))
                .defaultValue(() -> License.LGPL)
                .build();

        TextFieldComponent licenseCustomComponent = FormComponent.textField("CustomLicense", "railroad.project.creation.license.custom")
                .visible(licenseComboBox.get().valueProperty().isEqualTo(License.CUSTOM))
                .bindTextFieldTo(licenseCustomField)
                .promptText("railroad.project.creation.license.custom.prompt")
                .validator(textField -> {
                    if (textField.getText().isBlank())
                        return ValidationResult.error("railroad.project.creation.license.custom.error.required");

                    return ValidationResult.ok();
                })
                .build();

        List<MinecraftVersion> supportedVersions = MinecraftVersion.getSupportedVersions(ProjectType.FABRIC);
        MinecraftVersion latestVersion = supportedVersions.getFirst();
        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("MinecraftVersion", "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                .required()
                .items(supportedVersions)
                .defaultValue(() -> latestVersion)
                .bindComboBoxTo(minecraftVersionComboBox)
                .keyFunction(MinecraftVersion::id)
                .valueOfFunction(string -> MinecraftVersion.fromId(string).orElse(null))
                .addTransformer(minecraftVersionComboBox, fabricLoaderVersionComboBox, FabricLoaderVersion::getVersions)
                .addTransformer(minecraftVersionComboBox, fapiVersionComboBox, FabricAPIVersion::getVersions)
                .listener((node, observable, oldValue, newValue) -> {
                    fabricLoaderVersionComboBox.get().setValue(FabricLoaderVersion.getLatestVersion(newValue));
                    fapiVersionComboBox.get().setValue(FabricAPIVersion.getLatest());

                    MappingHelper.loadMappings(mappingChannelComboBox.get().getItems(), newValue);
                    mappingChannelComboBox.get().setValue(mappingChannelComboBox.get().getItems().getFirst());
                })
                .translate(false)
                .build();

        ComboBoxComponent<FabricLoaderVersion> fabricLoaderVersionComponent = FormComponent.comboBox("FabricLoaderVersion", "railroad.project.creation.fabric_loader_version", FabricLoaderVersion.class)
                .required()
                .bindComboBoxTo(fabricLoaderVersionComboBox)
                .keyFunction(version -> version.loaderVersion().version())
                .valueOfFunction(string -> FabricLoaderVersion.fromId(minecraftVersionComboBox.get().getValue(), string).orElse(null))
                .addTransformer(fabricLoaderVersionComboBox, fapiVersionComboBox, version -> FabricAPIVersion.getVersions(minecraftVersionComboBox.get().getValue()))
                .listener((node, observable, oldValue, newValue) ->
                        fapiVersionComboBox.get().setValue(FabricAPIVersion.getLatest()))
                .translate(false)
                .cellFactory(param -> new StarableListCell<>(
                        version -> Objects.equals(version.loaderVersion().version(), FabricLoaderVersion.getLatestVersion(minecraftVersionComboBox.get().getValue()).loaderVersion().version()),
                        version -> false,
                        fabricLoaderVersion -> fabricLoaderVersion.loaderVersion().version()))
                .buttonCell(new StarableListCell<>(
                        version -> Objects.equals(version.loaderVersion().version(), FabricLoaderVersion.getLatestVersion(minecraftVersionComboBox.get().getValue()).loaderVersion().version()),
                        version -> false,
                        fabricLoaderVersion -> fabricLoaderVersion.loaderVersion().version()))
                .defaultValue(() -> FabricLoaderVersion.getLatestVersion(latestVersion))
                .build();

        CheckBoxComponent includeFapiComponent = FormComponent.checkBox("IncludeFapi", "railroad.project.creation.include_fapi")
                .bindCheckBoxTo(includeFapiCheckBox)
                .selected(true)
                .build();

        ComboBoxComponent<FabricAPIVersion> fapiVersionComponent = FormComponent.comboBox("FabricAPIVersion", "railroad.project.creation.fabric_api_version", FabricAPIVersion.class)
                .required()
                .bindComboBoxTo(fapiVersionComboBox)
                .keyFunction(FabricAPIVersion::version)
                .valueOfFunction(string -> fapiVersionComboBox.get().getItems().stream()
                        .filter(version -> version.version().equals(string))
                        .findFirst().orElse(null))
                .translate(false)
                .cellFactory(param -> new StarableListCell<>(
                        version -> Objects.equals(version, FabricAPIVersion.getLatest()),
                        version -> false,
                        FabricAPIVersion::version))
                .buttonCell(new StarableListCell<>(
                        version -> Objects.equals(version, FabricAPIVersion.getLatest()),
                        version -> false,
                        FabricAPIVersion::version))
                .defaultValue(FabricAPIVersion::getLatest)
                .visible(createBinding(includeFapiCheckBox.get().selectedProperty()))
                .build();

        TextFieldComponent modIdComponent = FormComponent.textField("ModId", "railroad.project.creation.mod_id")
                .required()
                .bindTextFieldTo(modIdField)
                .promptText("railroad.project.creation.mod_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.mod_id.error.required");

                    if (text.length() < 3)
                        return ValidationResult.error("railroad.project.creation.mod_id.error.length_short");

                    if (text.length() > 64)
                        return ValidationResult.error("railroad.project.creation.mod_id.error.length_long");

                    if (!text.matches("^[a-z][a-z0-9_]{1,63}$"))
                        return ValidationResult.error("railroad.project.creation.mod_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInModid.get() && !modIdField.get().getText().isBlank())
                        hasTypedInModid.set(true);
                    else if (hasTypedInModid.get() && modIdField.get().getText().isBlank())
                        hasTypedInModid.set(false);
                })
                .addTransformer(modIdField, artifactIdField, text -> {
                    if (!hasTypedInArtifactId.get() || artifactIdField.get().getText().isBlank())
                        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");

                    return text;
                })
                .build();

        TextFieldComponent modNameComponent = FormComponent.textField("ModName", "railroad.project.creation.mod_name")
                .required()
                .bindTextFieldTo(modNameField)
                .promptText("railroad.project.creation.mod_name.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.mod_name.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.mod_name.error.length_long");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInModName.get() && !modNameField.get().getText().isBlank())
                        hasTypedInModName.set(true);
                    else if (hasTypedInModName.get() && modNameField.get().getText().isBlank())
                        hasTypedInModName.set(false);
                })
                .addTransformer(modNameField, mainClassField, text -> {
                    if (!hasTypedInMainClass.get() || mainClassField.get().getText().isBlank())
                        return text;

                    return text;
                })
                .build();

        TextFieldComponent mainClassComponent = FormComponent.textField("MainClass", "railroad.project.creation.main_class")
                .required()
                .bindTextFieldTo(mainClassField)
                .promptText("railroad.project.creation.main_class.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.main_class.error.required");

                    if (!text.matches("[a-zA-Z0-9_]+"))
                        return ValidationResult.error("railroad.project.creation.main_class.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInMainClass.get() && !mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(true);
                    else if (hasTypedInMainClass.get() && mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(false);
                })
                .build();

        CheckBoxComponent useAccessWidenerComponent = FormComponent.checkBox("UseAccessWidener", "railroad.project.creation.use_access_widener")
                .bindCheckBoxTo(useAccessWidenerCheckBox)
                .build();

        CheckBoxComponent splitSourcesComponent = FormComponent.checkBox("SplitSources", "railroad.project.creation.split_sources")
                .bindCheckBoxTo(splitSourcesCheckBox)
                .build();

        ComboBoxComponent<MappingChannel> mappingChannelComponent = FormComponent.comboBox("MappingChannel", "railroad.project.creation.mapping_channel", MappingChannel.class)
                .required()
                .items(Arrays.asList(MappingChannel.values()))
                .defaultValue(() -> MappingChannel.MOJMAP)
                .bindComboBoxTo(mappingChannelComboBox)
                .keyFunction(MappingChannel::getName)
                .valueOfFunction(string -> MappingChannel.valueOf(string.toUpperCase(Locale.ROOT)))
                .addTransformer(mappingChannelComboBox, mappingVersionComboBox, mappingChannel -> {
                    ObservableList<MappingVersion> items = FXCollections.observableArrayList();
                    MappingHelper.loadMappingsVersions(items, minecraftVersionComboBox.get().getValue(), mappingChannel);
                    return items;
                })
                .listener((node, observable, oldValue, newValue) ->
                        mappingVersionComboBox.get().setValue(mappingVersionComboBox.get().getItems().getFirst()))
                .translate(false)
                .build();

        ComboBoxComponent<MappingVersion> mappingVersionComponent = FormComponent.comboBox("MappingVersion", "railroad.project.creation.mapping_version", MappingVersion.class)
                .required()
                .bindComboBoxTo(mappingVersionComboBox)
                .cellFactory(param -> new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        MappingVersion::isLatest,
                        MappingVersion::getId))
                .buttonCell(new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        MappingVersion::isLatest,
                        MappingVersion::getId))
                .keyFunction(MappingVersion::getId)
                .translate(false)
                .build();

        TextFieldComponent authorComponent = FormComponent.textField("Author", "railroad.project.creation.author")
                .bindTextFieldTo(authorField)
                .promptText("railroad.project.creation.author.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.author.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextAreaComponent descriptionComponent = FormComponent.textArea("Description", "railroad.project.creation.description")
                .bindTextAreaTo(descriptionArea)
                .promptText("railroad.project.creation.description.prompt")
                .validator(textArea -> {
                    String text = textArea.getText();
                    if (text.length() > 1028)
                        return ValidationResult.error("railroad.project.creation.description.error.length_long");

                    return ValidationResult.ok();
                })
                .resize(true)
                .wrapText(true)
                .build();

        TextFieldComponent issuesComponent = FormComponent.textField("Issues", "railroad.project.creation.issues")
                .bindTextFieldTo(issuesField)
                .promptText("railroad.project.creation.issues.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.issues.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent homepageComponent = FormComponent.textField("Homepage", "railroad.project.creation.homepage")
                .bindTextFieldTo(homepageField)
                .promptText("railroad.project.creation.homepage.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.homepage.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent sourcesComponent = FormComponent.textField("Sources", "railroad.project.creation.sources")
                .bindTextFieldTo(sourcesField)
                .promptText("railroad.project.creation.sources.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.sources.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent groupIdComponent = FormComponent.textField("GroupId", "railroad.project.creation.group_id")
                .required()
                .bindTextFieldTo(groupIdField)
                .promptText("railroad.project.creation.group_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.group_id.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.group_id.error.length_long");

                    if (!text.matches("[a-zA-Z0-9.]+"))
                        return ValidationResult.error("railroad.project.creation.group_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent artifactIdComponent = FormComponent.textField("ArtifactId", "railroad.project.creation.artifact_id")
                .required()
                .bindTextFieldTo(artifactIdField)
                .promptText("railroad.project.creation.artifact_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.length_long");

                    if (!text.matches("[a-z0-9-]+"))
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInArtifactId.get() && !artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(true);
                    else if (hasTypedInArtifactId.get() && artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(false);
                })
                .build();

        TextFieldComponent versionComponent = FormComponent.textField("Version", "railroad.project.creation.version")
                .required()
                .bindTextFieldTo(versionField)
                .promptText("railroad.project.creation.version.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.version.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.version.error.length_long");

                    if (!text.matches("[a-zA-Z0-9.-]+"))
                        return ValidationResult.error("railroad.project.creation.version.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .build();

        Form form = Form.create()
                .spacing(15)
                .padding(10)
                .appendSection(FormSection.create("railroad.project.creation.section.project")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(projectNameComponent)
                        .appendComponent(projectPathComponent)
                        .appendComponent(createGitComponent)
                        .appendComponent(licenseComponent)
                        .appendComponent(licenseCustomComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.minecraft")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(minecraftVersionComponent)
                        .appendComponent(fabricLoaderVersionComponent)
                        .appendComponent(includeFapiComponent)
                        .appendComponent(fapiVersionComponent)
                        .appendComponent(modIdComponent)
                        .appendComponent(modNameComponent)
                        .appendComponent(mainClassComponent)
                        .appendComponent(useAccessWidenerComponent)
                        .appendComponent(splitSourcesComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.mappings")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(mappingChannelComponent)
                        .appendComponent(mappingVersionComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.optional")
                        .borderColor(Color.SLATEGRAY)
                        .appendComponent(authorComponent)
                        .appendComponent(descriptionComponent)
                        .appendComponent(issuesComponent)
                        .appendComponent(homepageComponent)
                        .appendComponent(sourcesComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.maven")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(groupIdComponent)
                        .appendComponent(artifactIdComponent)
                        .appendComponent(versionComponent))
                .disableResetButton()
                .onSubmit((theForm, formData) -> {
                    if (theForm.validate()) {
                        FabricProjectData data = createData(formData);
                        getScene().setRoot(new FabricProjectCreationPane(data));
                    } else {
                        theForm.runValidation(); // Show validation errors
                    }
                })
                .build();

        getChildren().add(form.createUI());

        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", createdAtPath, (projectPathField.get().getText() == null ? "" : projectPathField.get().getText()) + "\\" + (projectNameField.get().getText() == null ? "" : projectNameField.get().getText()));

        ComboBox<MappingVersion> mappingVersionComboBox = this.mappingVersionComboBox.get();
        MappingHelper.loadMappingsVersions(mappingVersionComboBox.getItems(), minecraftVersionComboBox.get().getValue(), mappingChannelComboBox.get().getValue());
        mappingVersionComboBox.setValue(mappingVersionComboBox.getItems().getFirst());
    }

    private static BooleanBinding createBinding(BooleanProperty property) {
        return Bindings.when(property).then(true).otherwise(false);
    }

    private static String fixPath(String path) {
        while (path.endsWith(" "))
            path = path.substring(0, path.length() - 1);

        path = path.replace("/", "\\");

        // Remove trailing backslashes
        while (path.endsWith("\\"))
            path = path.substring(0, path.length() - 1);

        // remove any whitespace before a backslash
        path = path.replaceAll("\\s+\\\\", "\\");

        // remove any whitespace after a backslash
        path = path.replaceAll("\\\\\\\\s+", "\\\\");

        // remove any double backslashes
        path = path.replaceAll("\\\\\\\\", "\\\\");

        // remove any trailing whitespace
        path = path.trim();

        return path;
    }

    protected static FabricProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.getEnum("License", License.class);
        String licenseCustom = license == License.CUSTOM ? formData.getString("CustomLicense") : null;
        MinecraftVersion minecraftVersion = formData.get("MinecraftVersion", MinecraftVersion.class);
        FabricLoaderVersion fabricVersion = formData.get("FabricLoaderVersion", FabricLoaderVersion.class);
        Optional<FabricAPIVersion> fapiVersion = Optional.ofNullable(formData.getBoolean("IncludeFapi") ? formData.get("FabricAPIVersion", FabricAPIVersion.class) : null);
        String modId = formData.getString("ModId");
        String modName = formData.getString("ModName");
        String mainClass = formData.getString("MainClass");
        boolean useAccessWidener = formData.getBoolean("UseAccessWidener");
        boolean splitSources = formData.getBoolean("SplitSources");
        MappingChannel mappingChannel = formData.get("MappingChannel", MappingChannel.class);
        MappingVersion mappingVersion = formData.get("MappingVersion", MappingVersion.class);
        Optional<String> author = Optional.ofNullable(formData.getString("Author")).filter(s -> !s.isBlank());
        Optional<String> description = Optional.ofNullable(formData.getString("Description")).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.ofNullable(formData.getString("Issues")).filter(s -> !s.isBlank());
        Optional<String> homepage = Optional.ofNullable(formData.getString("Homepage")).filter(s -> !s.isBlank());
        Optional<String> sources = Optional.ofNullable(formData.getString("Sources")).filter(s -> !s.isBlank());
        String groupId = formData.getString("GroupId");
        String artifactId = formData.getString("ArtifactId");
        String version = formData.getString("Version");

        return new FabricProjectData(projectName, projectPath, createGit, license, licenseCustom, minecraftVersion, fabricVersion, fapiVersion, modId, modName, mainClass, useAccessWidener, splitSources, mappingChannel, mappingVersion, author, description, issues, homepage, sources, groupId, artifactId, version);
    }
}