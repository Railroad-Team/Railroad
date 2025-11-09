package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.core.form.*;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.onboarding.ProjectValidators;
import dev.railroadide.railroad.settings.ui.DetectedJdkListPane;
import dev.railroadide.railroad.utility.StringUtils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ComboBox;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class GradleRunConfigurationData extends RunConfigurationData {
    private String task;
    private Path gradleProjectPath; // TODO: A different data type(?)
    private Map<String, String> environmentVariables = new HashMap<>();
    private String[] vmOptions = new String[0];
    private JDK javaHome;

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.GRADLE;
    }

    @Override
    public Form createConfigurationForm(Project project) {
        ObjectProperty<ComboBox<JDK>> javaHomeComboBoxProperty = new SimpleObjectProperty<>();
        javaHomeComboBoxProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.setMinHeight(72);
                newValue.setPrefHeight(72);
                newValue.setMaxHeight(72);
            }
        });
        return createBaseFormBuilder(project)
            .appendSection(FormSection.create("railroad.runconfig.gradle.configuration.title")
                .appendComponent(FormComponent.textField("task", "railroad.runconfig.gradle.configuration.task.label")
                    .required()
                    .text(() -> this.task != null ? this.task : "")
                    .promptText("railroad.runconfig.gradle.configuration.task.prompt")
                    .validator(null) // TODO: Check if its a valid Gradle task
                    .build())
                .appendComponent(FormComponent.directoryChooser("gradleProjectPath", "railroad.runconfig.gradle.configuration.projectPath.label")
                    .required()
                    .defaultPath(this.gradleProjectPath)
                    .validator(ProjectValidators::validateDirectoryPath) // TODO: Check if it's a valid Gradle project path
                    .build())
                .appendComponent(FormComponent.textField("environmentVariables", "railroad.runconfig.gradle.configuration.envVariables.label")
                    .required()
                    .text(() -> StringUtils.environmentVariablesToString(this.environmentVariables))
                    .promptText("railroad.runconfig.gradle.configuration.envVariables.prompt")
                    .validator(textField -> !StringUtils.isValidEnvironmentVariablesString(textField.getText()) ?
                        ValidationResult.error("railroad.runconfig.gradle.configuration.envVariables.invalid") :
                        ValidationResult.ok())
                    .build())
                .appendComponent(FormComponent.textField("vmOptions", "railroad.runconfig.gradle.configuration.vmOptions.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.vmOptions, " "))
                    .promptText("railroad.runconfig.gradle.configuration.vmOptions.prompt")
                    .build())
                .appendComponent(FormComponent.comboBox("javaHome", "railroad.runconfig.gradle.configuration.javaHome.label", JDK.class)
                    .required()
                    .defaultValue(this::getJavaHome)
                    .items(JDKManager::getAvailableJDKs)
                    .translate(false)
                    .buttonCell(new DetectedJdkListPane.JdkCell())
                    .cellFactory($ -> new DetectedJdkListPane.JdkCell())
                    .keyFunction(jdk -> jdk != null ? jdk.path().toString() : "")
                    .valueOfFunction(jdkPath -> JDKManager.getAvailableJDKs()
                        .stream()
                        .filter(jdk -> jdk.path().toString().equals(jdkPath))
                        .findFirst()
                        .orElse(null))
                    .bindComboBoxTo(javaHomeComboBoxProperty)
                    .build())
            ).build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.task = formData.get("task", String.class);
        this.gradleProjectPath = Path.of(formData.get("gradleProjectPath", String.class));
        this.environmentVariables = StringUtils.stringToEnvironmentVariables(formData.get("environmentVariables", String.class));
        this.vmOptions = StringUtils.stringToStringArray(formData.get("vmOptions", String.class), " ");
        this.javaHome = formData.get("javaHome", JDK.class);
    }
}
