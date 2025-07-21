package dev.railroadide.railroad.welcome.imports;

import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.vcs.connections.VCSProfile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

public class AccountListCell extends ListCell<Object> {
    private final HBox container = new RRHBox(8);
    private final MFXFontIcon forkIcon = new MFXFontIcon();
    private final MFXFontIcon accountIcon = new MFXFontIcon();
    private final LocalizedLabel title = new LocalizedLabel("");
    private final Label subtitle = new Label();
    private final RRVBox titleContainer;

    public AccountListCell() {
        getStyleClass().add("account-list-cell");

        forkIcon.setSize(32);
        forkIcon.setDescription(FontAwesomeSolid.CODE_FORK.getDescription());
        forkIcon.getStyleClass().add("account-list-cell-icon");

        accountIcon.setSize(32);
        accountIcon.getStyleClass().add("account-list-cell-icon");

        title.getStyleClass().add("account-list-cell-title");
        subtitle.getStyleClass().add("account-list-cell-subtitle");

        this.titleContainer = new RRVBox(4);
        titleContainer.getChildren().add(title);
        titleContainer.setFillWidth(true);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        titleContainer.getStyleClass().add("account-list-cell-title-container");

        container.getChildren().addAll(forkIcon, titleContainer);
        container.setPadding(new Insets(8, 12, 8, 12));
        container.getStyleClass().add("account-list-cell-container");
        container.setAlignment(Pos.CENTER_LEFT);

        selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                container.getStyleClass().add("selected");
            } else {
                container.getStyleClass().remove("selected");
            }
        });

        setGraphic(container);
        setText(null);
    }

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            title.setText(null);
            setGraphic(null);
        } else if (item instanceof VCSProfile profile) {
            title.setKey(profile.getType().getName());
            accountIcon.setIconsProvider(profile.getType().getIconProvider());
            accountIcon.setDescription(profile.getType().getIcon());
            subtitle.setText(profile.getAlias());
            titleContainer.getChildren().setAll(title, subtitle);
            container.getChildren().set(0, accountIcon);
            setGraphic(container);
        } else if ("REPO_URL_OPTION".equals(item)) {
            title.setKey("railroad.importprojects.repositoryurl");
            titleContainer.getChildren().setAll(title);
            container.getChildren().set(0, forkIcon);
            setGraphic(container);
        }
    }
}
