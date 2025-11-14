package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.core.form.*;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.onboarding.ProjectValidators;
import dev.railroadide.railroad.utility.StringUtils;
import javafx.beans.binding.Bindings;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ShellScriptRunConfigurationData extends RunConfigurationData {
    private ExecuteMode executeMode = ExecuteMode.FILE;
    private Path scriptPath;
    private String scriptText;
    private String[] scriptArgs = new String[0];
    private Path workingDirectory;
    private Map<String, String> environmentVariables = new HashMap<>();
    private String interpreterPath;
    private String[] interpreterArgs = new String[0];
    private boolean executeInTerminal = true;

    public ShellScriptRunConfigurationData() {
        this.interpreterPath = switch (OperatingSystem.CURRENT) {
            case WINDOWS -> "powershell.exe";
            case MAC, LINUX -> "/bin/bash";
            default -> "";
        };
    }

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.SHELL_SCRIPT;
    }

    @Override
    public Form createConfigurationForm(Project project, RunConfiguration<?> configuration) {
        var executeModeComponent = FormComponent.radioButtonGroup("executeMode", "railroad.runconfig.shell_script.configuration.executeMode.label", ExecuteMode.class)
            .required()
            .selected(() -> this.executeMode)
            .build();

        var modeValueProperty = executeModeComponent.getComponent().valueProperty();
        var fileModeVisible = Bindings.createBooleanBinding(
            () -> executeModeComponent.getComponent().getValue() == ExecuteMode.FILE,
            modeValueProperty);
        var textModeVisible = Bindings.createBooleanBinding(
            () -> executeModeComponent.getComponent().getValue() == ExecuteMode.TEXT,
            modeValueProperty);

        var scriptArgsValue = this.scriptArgs == null ? new String[0] : this.scriptArgs;
        var interpreterArgsValue = this.interpreterArgs == null ? new String[0] : this.interpreterArgs;
        var environmentValues = this.environmentVariables == null ? Map.<String, String>of() : this.environmentVariables;

        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.shell_script.configuration.section.title")
                .appendComponent(executeModeComponent)
                .appendComponent(FormComponent.fileChooser("scriptPath", "railroad.runconfig.shell_script.configuration.scriptPath.label")
                    .required()
                    .defaultPath(this.scriptPath)
                    .validator(field -> ProjectValidators.validateFilePath(field, null))
                    .visible(fileModeVisible)
                    .build())
                .appendComponent(FormComponent.textArea("scriptText", "railroad.runconfig.shell_script.configuration.scriptText.label")
                    .required()
                    .text(() -> this.scriptText)
                    .promptText("railroad.runconfig.shell_script.configuration.scriptText.prompt")
                    .visible(textModeVisible)
                    .build())
                .appendComponent(FormComponent.textField("scriptArgs", "railroad.runconfig.shell_script.configuration.scriptArgs.label")
                    .text(() -> StringUtils.stringArrayToString(scriptArgsValue, " "))
                    .promptText("railroad.runconfig.shell_script.configuration.scriptArgs.prompt")
                    .build())
                .appendComponent(FormComponent.directoryChooser("workingDirectory", "railroad.runconfig.shell_script.configuration.workingDirectory.label")
                    .required()
                    .defaultPath(this.workingDirectory)
                    .validator(ProjectValidators::validateDirectoryPath)
                    .build())
                .appendComponent(FormComponent.textField("environmentVariables", "railroad.runconfig.shell_script.configuration.envVariables.label")
                    .text(() -> StringUtils.environmentVariablesToString(environmentValues))
                    .promptText("railroad.runconfig.shell_script.configuration.envVariables.prompt")
                    .validator(textField -> !StringUtils.isValidEnvironmentVariablesString(textField.getText()) ?
                        ValidationResult.error("railroad.runconfig.shell_script.configuration.envVariables.invalid") :
                        ValidationResult.ok())
                    .build())
                .appendComponent(FormComponent.fileChooser("interpreterPath", "railroad.runconfig.shell_script.configuration.interpreterPath.label")
                    .required()
                    .defaultPath(this.interpreterPath)
                    .validator(field -> ProjectValidators.validateFilePath(field, null))
                    .build())
                .appendComponent(FormComponent.textField("interpreterArgs", "railroad.runconfig.shell_script.configuration.interpreterArgs.label")
                    .text(() -> StringUtils.stringArrayToString(interpreterArgsValue, " "))
                    .promptText("railroad.runconfig.shell_script.configuration.interpreterArgs.prompt")
                    .build())
                .appendComponent(FormComponent.checkBox("executeInTerminal", "railroad.runconfig.shell_script.configuration.executeInTerminal.label")
                    .selected(this.executeInTerminal)
                    .build()))
            .build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.executeMode = formData.get("executeMode", ExecuteMode.class);
        this.scriptPath = Path.of(formData.get("scriptPath", String.class));
        this.scriptText = formData.get("scriptText", String.class);
        this.scriptArgs = StringUtils.stringToStringArray(formData.get("scriptArgs", String.class), " ");
        this.workingDirectory = Path.of(formData.get("workingDirectory", String.class));
        this.environmentVariables = StringUtils.stringToEnvironmentVariables(formData.get("environmentVariables", String.class));
        this.interpreterPath = formData.get("interpreterPath", String.class);
        this.interpreterArgs = StringUtils.stringToStringArray(formData.get("interpreterArgs", String.class), " ");
        this.executeInTerminal = formData.get("executeInTerminal", Boolean.class);
    }

    public enum ExecuteMode {
        FILE,
        TEXT
    }
}
