package dev.railroadide.railroad.ide.diagnostics.ui;

import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettingsState;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings pane for Java inspection rule overrides.
 */
public final class JavaInspectionRuleSettingsPane extends RRVBox {
    private final List<TagRow> tagRows = new ArrayList<>();
    private final List<RuleRow> ruleRows = new ArrayList<>();

    public JavaInspectionRuleSettingsPane(JavaInspectionRuleSettingsState initialState) {
        setSpacing(16);
        setPadding(new Insets(0));
        getStyleClass().remove("background-2");

        Label title = new Label("Java Inspection Rules");
        title.getStyleClass().add("section-label");

        Label subtitle = new Label("Override individual rule enablement and severity, or set defaults by tag.");
        subtitle.getStyleClass().add("section-description-label");
        subtitle.setWrapText(true);

        getChildren().addAll(title, subtitle, buildTagsSection(), buildRulesSection());
        setState(initialState);
    }

    public JavaInspectionRuleSettingsState getState() {
        Map<String, Boolean> ruleEnabled = new LinkedHashMap<>();
        Map<String, Boolean> tagEnabled = new LinkedHashMap<>();
        Map<String, SemanticDiagnostic.Severity> severity = new LinkedHashMap<>();

        for (TagRow row : tagRows) {
            EnabledOverride selected = row.enabledOverride.getValue();
            Boolean value = selected == null ? null : selected.selectedValue();
            if (value != null)
                tagEnabled.put(row.tag, value);
        }

        for (RuleRow row : ruleRows) {
            EnabledOverride enabledSelection = row.enabledOverride.getValue();
            Boolean enabled = enabledSelection == null ? null : enabledSelection.selectedValue();
            if (enabled != null)
                ruleEnabled.put(row.rule.rule().id(), enabled);

            SeverityOverride severitySelection = row.severityOverride.getValue();
            SemanticDiagnostic.Severity severityValue = severitySelection == null ? null : severitySelection.selectedValue();
            if (severityValue != null)
                severity.put(row.rule.rule().id(), severityValue);
        }

        return new JavaInspectionRuleSettingsState(ruleEnabled, tagEnabled, severity);
    }

    public void setState(JavaInspectionRuleSettingsState state) {
        Map<String, Boolean> ruleEnabled = state.ruleEnabledOverrides();
        Map<String, Boolean> tagEnabled = state.tagEnabledOverrides();
        Map<String, SemanticDiagnostic.Severity> severity = state.severityOverrides();

        for (TagRow row : tagRows)
            row.enabledOverride.setValue(EnabledOverride.from(tagEnabled.get(row.tag)));

        for (RuleRow row : ruleRows) {
            row.enabledOverride.setValue(EnabledOverride.from(ruleEnabled.get(row.rule.rule().id())));
            row.severityOverride.setValue(SeverityOverride.from(severity.get(row.rule.rule().id())));
        }
    }

    private VBox buildTagsSection() {
        RRVBox section = new RRVBox(8);
        section.getStyleClass().remove("background-2");

        Label header = new Label("Tag Overrides");
        header.getStyleClass().add("section-label");
        section.getChildren().add(header);

        for (String tag : collectTags()) {
            TagRow row = new TagRow(tag);
            tagRows.add(row);
            section.getChildren().add(row.container);
        }

        if (tagRows.isEmpty()) {
            Label empty = new Label("No rule tags are currently registered.");
            empty.getStyleClass().add("section-description-label");
            section.getChildren().add(empty);
        }

        return section;
    }

    private VBox buildRulesSection() {
        RRVBox section = new RRVBox(8);
        section.getStyleClass().remove("background-2");

        Label header = new Label("Rule Overrides");
        header.getStyleClass().add("section-label");

        RRVBox rows = new RRVBox(10);
        rows.getStyleClass().remove("background-2");
        for (RuleDescriptor rule : collectRules()) {
            RuleRow row = new RuleRow(rule);
            ruleRows.add(row);
            rows.getChildren().add(row.container);
        }

        ScrollPane scrollPane = new ScrollPane(rows);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(520);

        section.getChildren().addAll(header, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return section;
    }

    private static List<String> collectTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (RuleDescriptor rule : collectRules())
            tags.addAll(rule.tags());
        return tags.stream().sorted().toList();
    }

    private static List<RuleDescriptor> collectRules() {
        List<RuleDescriptor> rules = new ArrayList<>();
        List<Map.Entry<String, JavaInspectionRuleProvider>> providers = new ArrayList<>(
                JavaInspectionRegistries.ruleProviderEntries().entrySet()
        );
        providers.sort(Map.Entry.comparingByKey());

        for (Map.Entry<String, JavaInspectionRuleProvider> entry : providers) {
            String providerId = entry.getKey();
            for (JavaInspectionRule rule : entry.getValue().rules()) {
                if (rule == null)
                    continue;
                rules.add(new RuleDescriptor(providerId, rule));
            }
        }

        rules.sort(Comparator
                .comparing(RuleDescriptor::providerId)
                .thenComparing(rule -> rule.rule().id()));
        return List.copyOf(rules);
    }

    private record RuleDescriptor(String providerId, JavaInspectionRule rule) {
        private Set<String> tags() {
            return rule.tags();
        }
    }

    private static final class TagRow {
        private final String tag;
        private final HBox container;
        private final ComboBox<EnabledOverride> enabledOverride = new ComboBox<>();

        private TagRow(String tag) {
            this.tag = tag;
            this.container = new RRHBox(12);
            container.getStyleClass().remove("background-2");

            Label name = new Label(tag);
            name.getStyleClass().add("section-label");
            HBox.setHgrow(name, Priority.ALWAYS);

            enabledOverride.getItems().addAll(EnabledOverride.values());
            enabledOverride.setValue(EnabledOverride.DEFAULT);
            enabledOverride.setPrefWidth(160);

            container.getChildren().addAll(name, spacer(), enabledOverride);
        }
    }

    private static final class RuleRow {
        private final RuleDescriptor rule;
        private final VBox container;
        private final ComboBox<EnabledOverride> enabledOverride = new ComboBox<>();
        private final ComboBox<SeverityOverride> severityOverride = new ComboBox<>();

        private RuleRow(RuleDescriptor rule) {
            this.rule = rule;
            this.container = new RRVBox(6);
            container.setPadding(new Insets(12));

            Label idLabel = new Label(rule.rule().id());
            idLabel.getStyleClass().add("section-label");

            String providerText = "Provider: " + rule.providerId();
            if (!rule.tags().isEmpty())
                providerText += " | Tags: " + String.join(", ", rule.tags());
            providerText += " | Default severity: " + rule.rule().defaultSeverity().name();

            Label metaLabel = new Label(providerText);
            metaLabel.getStyleClass().add("section-description-label");
            metaLabel.setWrapText(true);

            enabledOverride.getItems().addAll(EnabledOverride.values());
            enabledOverride.setValue(EnabledOverride.DEFAULT);
            enabledOverride.setPrefWidth(160);

            severityOverride.getItems().addAll(SeverityOverride.values());
            severityOverride.setValue(SeverityOverride.DEFAULT);
            severityOverride.setPrefWidth(160);

            RRHBox controls = new RRHBox(12);
            controls.getStyleClass().remove("background-2");
            controls.getChildren().addAll(
                    labeledBox("Enabled", enabledOverride),
                    labeledBox("Severity", severityOverride)
            );

            container.getChildren().addAll(idLabel, metaLabel, controls, new Separator());
        }
    }

    private static VBox labeledBox(String labelText, Region node) {
        Label label = new Label(labelText);
        label.getStyleClass().add("section-description-label");

        RRVBox box = new RRVBox(4, label, node);
        box.getStyleClass().remove("background-2");
        return box;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private enum EnabledOverride {
        DEFAULT("Default", null),
        ENABLED("Enabled", true),
        DISABLED("Disabled", false);

        private final String label;
        private final Boolean value;

        EnabledOverride(String label, Boolean value) {
            this.label = label;
            this.value = value;
        }

        private Boolean selectedValue() {
            return value;
        }

        private static EnabledOverride from(Boolean value) {
            if (value == null)
                return DEFAULT;
            return value ? ENABLED : DISABLED;
        }
        @Override
        public String toString() {
            return label;
        }
    }

    private enum SeverityOverride {
        DEFAULT("Default", null),
        ERROR("Error", SemanticDiagnostic.Severity.ERROR),
        WARNING("Warning", SemanticDiagnostic.Severity.WARNING),
        INFO("Info", SemanticDiagnostic.Severity.INFO);

        private final String label;
        private final SemanticDiagnostic.Severity value;

        SeverityOverride(String label, SemanticDiagnostic.Severity value) {
            this.label = label;
            this.value = value;
        }

        private SemanticDiagnostic.Severity selectedValue() {
            return value;
        }

        private static SeverityOverride from(SemanticDiagnostic.Severity value) {
            if (value == null)
                return DEFAULT;
            return switch (value) {
                case ERROR -> ERROR;
                case WARNING -> WARNING;
                case INFO -> INFO;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
