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
public class JavaApplicationRunConfigurationData extends RunConfigurationData {
    private boolean buildBeforeRun;
    private JDK jdk;
    private String mainClass = "";
    private Path workingDirectory;
    private String[] classpathEntries = new String[0];
    private String[] programArguments = new String[0];
    private String[] vmOptions = new String[0];
    private Map<String, String> environmentVariables = new HashMap<>();

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.JAVA_APPLICATION;
    }

    @Override
    public Form createConfigurationForm(Project project, RunConfiguration<?> configuration) {
        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.java_application.configuration.section.title")
                .appendComponent(FormComponent.checkBox("buildBeforeRun", "railroad.runconfig.java_application.configuration.buildBeforeRun.label")
                    .selected(this.buildBeforeRun)
                    .build())
                .appendComponent(FormComponent.comboBox("jdk", "railroad.runconfig.java_application.configuration.jdk.label", JDK.class)
                    .required()
                    .defaultValue(this::getJdk)
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
                    .build())
                .appendComponent(FormComponent.textField("mainClass", "railroad.runconfig.java_application.configuration.mainClass.label")
                    .required()
                    .text(() -> this.mainClass)
                    .promptText("railroad.runconfig.java_application.configuration.mainClass.prompt")
                    .validator(ProjectValidators::validateQualifiedMainClass)
                    .build())
                .appendComponent(FormComponent.directoryChooser("workingDirectory", "railroad.runconfig.jar_application.configuration.workingDirectory.label")
                    .required()
                    .defaultPath(this.workingDirectory)
                    .validator(ProjectValidators::validateDirectoryPath)
                    .build())
                .appendComponent(FormComponent.textField("classpathEntries", "railroad.runconfig.java_application.configuration.classpathEntries.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.classpathEntries, ";"))
                    .promptText("railroad.runconfig.java_application.configuration.classpathEntries.prompt")
                    .build())
                .appendComponent(FormComponent.textField("programArguments", "railroad.runconfig.java_application.configuration.programArguments.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.programArguments, " "))
                    .promptText("railroad.runconfig.java_application.configuration.programArguments.prompt")
                    .build())
                .appendComponent(FormComponent.textField("vmOptions", "railroad.runconfig.java_application.configuration.vmOptions.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.vmOptions, " "))
                    .promptText("railroad.runconfig.java_application.configuration.vmOptions.prompt")
                    .build())
                .appendComponent(FormComponent.textField("environmentVariables", "railroad.runconfig.java_application.configuration.envVariables.label")
                    .required()
                    .text(() -> StringUtils.environmentVariablesToString(this.environmentVariables))
                    .promptText("railroad.runconfig.java_application.configuration.envVariables.prompt")
                    .validator(textField -> !StringUtils.isValidEnvironmentVariablesString(textField.getText()) ?
                        ValidationResult.error("railroad.runconfig.java_application.configuration.envVariables.invalid") :
                        ValidationResult.ok())
                    .build()))
            .build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.buildBeforeRun = formData.get("buildBeforeRun", Boolean.class);
        this.jdk = formData.get("jdk", JDK.class);
        this.mainClass = formData.get("mainClass", String.class);
        this.workingDirectory = Path.of(formData.get("workingDirectory", String.class));
        this.classpathEntries = StringUtils.stringToStringArray(formData.get("classpathEntries", String.class), ";");
        this.programArguments = StringUtils.stringToStringArray(formData.get("programArguments", String.class), " ");
        this.vmOptions = StringUtils.stringToStringArray(formData.get("vmOptions", String.class), " ");
        this.environmentVariables = StringUtils.stringToEnvironmentVariables(formData.get("environmentVariables", String.class));
    }
}
