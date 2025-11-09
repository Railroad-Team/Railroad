package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.project.Project;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CompoundRunConfigurationData extends RunConfigurationData {
    private RunMode runMode = RunMode.PARALLEL;
    private final List<RunConfiguration<?>> configurations = new ArrayList<>();

    public void addConfiguration(RunConfiguration<?> configuration) {
        configurations.add(configuration);
    }

    public void removeConfiguration(RunConfiguration<?> configuration) {
        configurations.remove(configuration);
    }

    @Override
    public Form createConfigurationForm(Project project) {
        return createBaseFormBuilder(project)
            .appendSection(FormSection.create("railroad.runconfig.compound.configurations.title")
                .appendComponent(FormComponent.radioButtonGroup("runMode", "railroad.runconfig.compound.configuration.runMode.label", RunMode.class)
                    .required()
                    .selected(() -> this.runMode != null ? this.runMode : RunMode.PARALLEL)
                    .optionLabelProvider(RunMode::getLocalizationKey)
                    .build())
                .build())
            .build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.configurations.clear();
        RunConfiguration<?>[] submitted = formData.get("configurations", RunConfiguration[].class);
        if (submitted != null) {
            Collections.addAll(this.configurations, submitted);
        }
    }

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.COMPOUND;
    }

    @Getter
    public enum RunMode {
        PARALLEL,
        SEQUENTIAL;

        private final String localizationKey;

        RunMode() {
            this.localizationKey = "railroad.runconfig.compound.runMode." + name().toLowerCase();
        }
    }
}
