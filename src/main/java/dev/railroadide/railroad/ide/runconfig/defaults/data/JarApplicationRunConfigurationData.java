package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.core.form.*;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.onboarding.ProjectValidators;
import dev.railroadide.railroad.settings.ui.DetectedJdkListPane;
import dev.railroadide.railroad.utility.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class JarApplicationRunConfigurationData extends RunConfigurationData {
    private Path jarPath;
    private String[] vmOptions = new String[0];
    private String[] programArguments = new String[0];
    private Path workingDirectory;
    private Map<String, String> environmentVariables = new HashMap<>();
    private JDK jre; // TODO: JRE class(?)

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.JAR_APPLICATION;
    }

    @Override
    public Form createConfigurationForm(Project project, RunConfiguration<?> configuration) {
        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.jar_application.configuration.section.title")
                .appendComponent(FormComponent.fileChooser("jarPath", "railroad.runconfig.jar_application.configuration.jarPath.label")
                    .required()
                    .defaultPath(this.jarPath)
                    .validator(ProjectValidators::validateJarFilePath)
                    .build())
                .appendComponent(FormComponent.textField("vmOptions", "railroad.runconfig.jar_application.configuration.vmOptions.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.vmOptions, " "))
                    .promptText("railroad.runconfig.jar_application.configuration.vmOptions.prompt")
                    .build())
                .appendComponent(FormComponent.textField("programArguments", "railroad.runconfig.jar_application.configuration.programArguments.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.programArguments, " "))
                    .promptText("railroad.runconfig.jar_application.configuration.programArguments.prompt")
                    .build())
                .appendComponent(FormComponent.directoryChooser("workingDirectory", "railroad.runconfig.jar_application.configuration.workingDirectory.label")
                    .required()
                    .defaultPath(this.workingDirectory)
                    .validator(ProjectValidators::validateDirectoryPath)
                    .build())
                .appendComponent(FormComponent.textField("environmentVariables", "railroad.runconfig.jar_application.configuration.envVariables.label")
                    .required()
                    .text(() -> StringUtils.environmentVariablesToString(this.environmentVariables))
                    .promptText("railroad.runconfig.jar_application.configuration.envVariables.prompt")
                    .validator(textField -> !StringUtils.isValidEnvironmentVariablesString(textField.getText()) ?
                        ValidationResult.error("railroad.runconfig.jar_application.configuration.envVariables.invalid") :
                        ValidationResult.ok())
                    .build())
                .appendComponent(FormComponent.comboBox("jre", "railroad.runconfig.jar_application.configuration.jre.label", JDK.class)
                    .required()
                    .defaultValue(this::getJre)
                    .items(JDKManager::getAvailableJDKs)
                    .translate(false)
                    .buttonCell(new DetectedJdkListPane.JdkCell())
                    .cellFactory($ -> new DetectedJdkListPane.JdkCell())
                    .keyFunction(jre -> jre != null ? jre.path().toString() : "")
                    .valueOfFunction(jdkPath -> JDKManager.getAvailableJDKs()
                        .stream()
                        .filter(jre -> jre.path().toString().equals(jdkPath))
                        .findFirst()
                        .orElse(null))
                    .build()))
            .build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.jarPath = Path.of(formData.get("jarPath", String.class));
        this.vmOptions = StringUtils.stringToStringArray(formData.get("vmOptions", String.class), " ");
        this.programArguments = StringUtils.stringToStringArray(formData.get("programArguments", String.class), " ");
        this.workingDirectory = Path.of(formData.get("workingDirectory", String.class));
        this.environmentVariables = StringUtils.stringToEnvironmentVariables(formData.get("environmentVariables", String.class));
        this.jre = formData.get("jre", JDK.class);
    }
}
