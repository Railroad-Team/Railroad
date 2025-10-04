package dev.railroadide.railroad.project.details;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.impl.TextFieldComponent;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextField;

/**
 * Provides reusable builders for project coordinate fields (group, artifact, version).
 */
public final class ProjectCoordinatesComponents {
    private final ObjectProperty<TextField> groupIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> artifactIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> versionField = new SimpleObjectProperty<>();

    private final TextFieldComponent.Builder groupIdBuilder;
    private final TextFieldComponent.Builder artifactIdBuilder;
    private final TextFieldComponent.Builder versionBuilder;

    private boolean built;

    public ProjectCoordinatesComponents() {
        groupIdBuilder = FormComponent.textField("GroupId", "railroad.project.creation.group_id")
            .required()
            .bindTextFieldTo(groupIdField)
            .promptText("railroad.project.creation.group_id.prompt")
            .validator(ProjectValidators::validateGroupId);

        artifactIdBuilder = FormComponent.textField("ArtifactId", "railroad.project.creation.artifact_id")
            .required()
            .bindTextFieldTo(artifactIdField)
            .promptText("railroad.project.creation.artifact_id.prompt")
            .validator(ProjectValidators::validateArtifactId);

        versionBuilder = FormComponent.textField("Version", "railroad.project.creation.version")
            .required()
            .bindTextFieldTo(versionField)
            .promptText("railroad.project.creation.version.prompt")
            .validator(ProjectValidators::validateVersion);
    }

    public TextFieldComponent.Builder groupIdBuilder() {
        return groupIdBuilder;
    }

    public TextFieldComponent.Builder artifactIdBuilder() {
        return artifactIdBuilder;
    }

    public TextFieldComponent.Builder versionBuilder() {
        return versionBuilder;
    }

    public ObjectProperty<TextField> groupIdFieldProperty() {
        return groupIdField;
    }

    public ObjectProperty<TextField> artifactIdFieldProperty() {
        return artifactIdField;
    }

    public ObjectProperty<TextField> versionFieldProperty() {
        return versionField;
    }

    public Components build() {
        if (built)
            throw new IllegalStateException("ProjectCoordinatesComponents already built");

        built = true;
        TextFieldComponent groupIdComponent = groupIdBuilder.build();
        TextFieldComponent artifactIdComponent = artifactIdBuilder.build();
        TextFieldComponent versionComponent = versionBuilder.build();

        return new Components(groupIdComponent, artifactIdComponent, versionComponent);
    }

    public record Components(
        TextFieldComponent groupIdComponent,
        TextFieldComponent artifactIdComponent,
        TextFieldComponent versionComponent
    ) {}
}
