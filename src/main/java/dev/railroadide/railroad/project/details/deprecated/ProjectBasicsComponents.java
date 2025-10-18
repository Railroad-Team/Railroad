package dev.railroadide.railroad.project.details.deprecated;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.impl.CheckBoxComponent;
import dev.railroadide.core.form.impl.ComboBoxComponent;
import dev.railroadide.core.form.impl.DirectoryChooserComponent;
import dev.railroadide.core.form.impl.TextFieldComponent;
import dev.railroadide.core.project.License;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.details.ProjectValidators;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextField;

/**
 * Encapsulates the common project basics form components so they can be reused across project types.
 */
public final class ProjectBasicsComponents {
    private final StringProperty createdPathProperty = new SimpleStringProperty(ProjectValidators.getRepairedPath(System.getProperty("user.home") + "\\"));
    private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> licenseCustomField = new SimpleObjectProperty<>();

    private final TextFieldComponent.Builder projectNameBuilder;
    private final DirectoryChooserComponent.Builder projectPathBuilder;
    private final CheckBoxComponent.Builder createGitBuilder;
    private final ComboBoxComponent.Builder<License> licenseBuilder;
    private final TextFieldComponent.Builder licenseCustomBuilder;

    private boolean built;

    public ProjectBasicsComponents() {
        projectNameBuilder = FormComponent.textField("ProjectName", "railroad.project.creation.name")
            .required()
            .bindTextFieldTo(projectNameField)
            .promptText("railroad.project.creation.name.prompt")
            .validator(ProjectValidators::validateProjectName)
            .listener((node, observable, oldValue, newValue) -> {
                TextField projectPath = projectPathField.get();
                String rawPath = (projectPath == null ? "" : projectPath.getText()) + "\\" + (newValue == null ? "" : newValue.trim());
                createdPathProperty.set(ProjectValidators.getRepairedPath(rawPath));
            });

        projectPathBuilder = FormComponent.directoryChooser("ProjectPath", "railroad.project.creation.location")
            .required()
            .defaultPath(System.getProperty("user.home"))
            .bindTextFieldTo(projectPathField)
            .validator(ProjectValidators::validatePath)
            .listener((node, observable, oldValue, newValue) -> {
                TextField projectName = projectNameField.get();
                String rawPath = (newValue == null ? "" : newValue.trim()) + "\\" + (projectName == null ? "" : projectName.getText().trim());
                createdPathProperty.set(ProjectValidators.getRepairedPath(rawPath));
            });

        createGitBuilder = FormComponent.checkBox("CreateGit", "railroad.project.creation.git")
            .bindCheckBoxTo(createGitCheckBox)
            .selected(true);

        licenseBuilder = FormComponent.comboBox("License", "railroad.project.creation.license", License.class)
            .required()
            .bindComboBoxTo(licenseComboBox)
            .keyFunction(License::getName)
            .valueOfFunction(License::fromName)
            .translate(false)
            .items(License.REGISTRY::values)
            .defaultValue(() -> LicenseRegistry.LGPL);

        licenseCustomBuilder = FormComponent.textField("CustomLicense", "railroad.project.creation.license.custom")
            .visible(licenseComboBox.map(ComboBoxBase::valueProperty).map(property -> property.isEqualTo(LicenseRegistry.CUSTOM)).getValue())
            .bindTextFieldTo(licenseCustomField)
            .promptText("railroad.project.creation.license.custom.prompt")
            .validator(ProjectValidators::validateCustomLicense);
    }

    public TextFieldComponent.Builder projectNameBuilder() {
        return projectNameBuilder;
    }

    public DirectoryChooserComponent.Builder projectPathBuilder() {
        return projectPathBuilder;
    }

    public CheckBoxComponent.Builder createGitBuilder() {
        return createGitBuilder;
    }

    public ComboBoxComponent.Builder<License> licenseBuilder() {
        return licenseBuilder;
    }

    public TextFieldComponent.Builder licenseCustomBuilder() {
        return licenseCustomBuilder;
    }

    public ObjectProperty<TextField> projectNameFieldProperty() {
        return projectNameField;
    }

    public ObjectProperty<TextField> projectPathFieldProperty() {
        return projectPathField;
    }

    public ObjectProperty<CheckBox> createGitCheckBoxProperty() {
        return createGitCheckBox;
    }

    public ObjectProperty<ComboBox<License>> licenseComboBoxProperty() {
        return licenseComboBox;
    }

    public ObjectProperty<TextField> licenseCustomFieldProperty() {
        return licenseCustomField;
    }

    public StringProperty createdPathProperty() {
        return createdPathProperty;
    }

    public Components build() {
        if (built)
            throw new IllegalStateException("ProjectBasicsComponents already built");

        built = true;
        TextFieldComponent projectNameComponent = projectNameBuilder.build();
        DirectoryChooserComponent projectPathComponent = projectPathBuilder.build();
        CheckBoxComponent createGitComponent = createGitBuilder.build();
        ComboBoxComponent<License> licenseComponent = licenseBuilder.build();
        TextFieldComponent licenseCustomComponent = licenseCustomBuilder.build();

        return new Components(projectNameComponent, projectPathComponent, createGitComponent, licenseComponent, licenseCustomComponent);
    }

    public record Components(
        TextFieldComponent projectNameComponent,
        DirectoryChooserComponent projectPathComponent,
        CheckBoxComponent createGitComponent,
        ComboBoxComponent<License> licenseComponent,
        TextFieldComponent licenseCustomComponent
    ) {}
}
