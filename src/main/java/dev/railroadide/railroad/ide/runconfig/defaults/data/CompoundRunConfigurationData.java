package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.railroad.form.Form;
import dev.railroadide.railroad.form.FormComponent;
import dev.railroadide.railroad.form.FormData;
import dev.railroadide.railroad.form.FormSection;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.ide.runconfig.ui.form.RunConfigurationPickerComponent;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores child configuration UUIDs and their parallel or sequential execution mode.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CompoundRunConfigurationData extends RunConfigurationData {
    private RunMode runMode = RunMode.PARALLEL;
    private final List<String> configurationIds = new ArrayList<>();

    /**
     * Appends a child configuration's UUID to the stored execution order; null input is ignored.
     *
     * @param configuration the child configuration to append, or {@code null}
     */
    public void addConfiguration(RunConfiguration<?> configuration) {
        if (configuration != null) {
            configurationIds.add(configuration.uuid().toString());
        }
    }

    /**
     * Removes the first stored occurrence of a child configuration's UUID; null input is ignored.
     *
     * @param configuration the child configuration to remove, or {@code null}
     */
    public void removeConfiguration(RunConfiguration<?> configuration) {
        if (configuration != null) {
            configurationIds.remove(configuration.uuid().toString());
        }
    }

    /**
     * Resolves stored UUIDs against available configurations while preserving their stored order.
     *
     * @param availableConfigurations the configurations to search, or {@code null} when unavailable
     * @return matching configurations with null entries for unresolved UUIDs, or an empty list when no choices are
     *         available
     */
    public List<RunConfiguration<?>> resolveConfigurations(List<RunConfiguration<?>> availableConfigurations) {
        if (configurationIds.isEmpty() || availableConfigurations == null || availableConfigurations.isEmpty())
            return List.of();

        List<RunConfiguration<?>> resolved = new ArrayList<>(configurationIds.size());
        for (String id : configurationIds) {
            RunConfiguration<?> match = null;
            if (id != null && !id.isBlank()) {
                try {
                    UUID uuid = UUID.fromString(id);
                    for (RunConfiguration<?> candidate : availableConfigurations) {
                        if (candidate != null && uuid.equals(candidate.uuid())) {
                            match = candidate;
                            break;
                        }
                    }
                } catch (IllegalArgumentException _) {
                    // TODO: Invalid UUID stored; keep null to present an issue during execution.
                }
            }

            resolved.add(match);
        }

        return resolved;
    }

    @Override
    public Form createConfigurationForm(Project project, RunConfiguration<?> configuration) {
        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.compound.configurations.title")
                .appendComponent(FormComponent
                    .radioButtonGroup("runMode", "railroad.runconfig.compound.configuration.runMode.label",
                        RunMode.class)
                    .required()
                    .selected(() -> this.runMode != null ? this.runMode : RunMode.PARALLEL)
                    .optionLabelProvider(RunMode::getLocalizationKey)
                    .build())
                .appendComponent(RunConfigurationPickerComponent.builder("configurations")
                    .labelKey("railroad.runconfig.compound.configuration.configurations.label")
                    .availableConfigurations(project.getRunConfigManager().getConfigurations())
                    .filter(runConfig -> runConfig != null
                        && (configuration == null || !runConfig.uuid().equals(configuration.uuid())))
                    .initialSelectionSupplier(
                        () -> resolveConfigurations(project.getRunConfigManager().getConfigurations()))
                    .build())
                .build())
            .build();
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.configurationIds.clear();
        RunConfiguration<?>[] submitted = formData.get("configurations", RunConfiguration[].class);
        if (submitted != null) {
            for (RunConfiguration<?> configuration : submitted) {
                if (configuration != null) {
                    configurationIds.add(configuration.uuid().toString());
                }
            }
        }
    }

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.COMPOUND;
    }

    /**
     * Determines when a compound configuration starts each child run operation.
     */
    @Getter
    public enum RunMode {
        /**
         * Starts child run operations together and waits for all of their futures.
         */
        PARALLEL,
        /**
         * Starts each child after the preceding child's run future completes successfully.
         */
        SEQUENTIAL;

        private final String localizationKey;

        RunMode() {
            this.localizationKey = "railroad.runconfig.compound.runMode." + name().toLowerCase();
        }
    }
}
