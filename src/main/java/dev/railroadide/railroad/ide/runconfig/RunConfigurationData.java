package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.core.form.*;
import dev.railroadide.railroad.project.Project;
import lombok.Data;

@Data
public abstract class RunConfigurationData {
    private String name;
    private boolean allowMultipleInstances = false;
    private boolean showConsoleOnRun = true;

    public abstract RunConfigurationType<?> getType();

    public abstract Form createConfigurationForm(Project project, RunConfiguration<?> configuration);

    public abstract void applyConfigurationFormData(FormData formData);

    protected void applyBaseFormData(FormData formData) {
        this.name = formData.getString("name");
        this.allowMultipleInstances = formData.getBoolean("allowMultipleInstances");
        this.showConsoleOnRun = formData.getBoolean("showConsoleOnRun");
    }

    protected Form.Builder createBaseFormBuilder(Project project, RunConfiguration<?> configuration) {
        return Form.create()
            .disableSubmitButton()
            .disableResetButton()
            .appendSection(FormSection.create("railroad.runconfig.general.title")
                .appendComponent(FormComponent.textField("name", "railroad.runconfig.general.name.label")
                    .required()
                    .text(() -> name)
                    .promptText("railroad.runconfig.general.name.prompt")
                    .validator(textField -> {
                        String text = textField.getText();
                        if (text == null || text.isBlank())
                            return ValidationResult.error("railroad.runconfig.general.name.validation.required");

                        if (project.getRunConfigManager().isDuplicateName(text, getType(), configuration.uuid()))
                            return ValidationResult.error("railroad.runconfig.general.name.validation.duplicate");

                        return ValidationResult.ok();
                    })
                    .build()
                )
                .appendComponent(FormComponent.checkBox("allowMultipleInstances", "railroad.runconfig.general.allowMultipleInstances.label")
                    .selected(allowMultipleInstances)
                    .build()
                )
                .appendComponent(FormComponent.checkBox("showConsoleOnRun", "railroad.runconfig.general.showConsoleOnRun.label")
                    .selected(showConsoleOnRun)
                    .build()
                )
            );
    }
}
