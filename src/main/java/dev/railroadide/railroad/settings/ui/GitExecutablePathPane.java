package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.utility.DesktopUtils;
import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.ui.BrowseButton;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.vcs.git.util.GitLocator;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.nio.file.Path;
import java.util.Objects;

public class GitExecutablePathPane extends RRHBox {
    private final ObjectProperty<Path> gitExecutablePath = new SimpleObjectProperty<>();

    private final RRTextField pathField = new RRTextField();
    private final BrowseButton browseButton = new BrowseButton();
    private final RRButton downloadButton = new RRButton(
        "railroad.generic.download",
        FontAwesomeSolid.DOWNLOAD
    );

    public GitExecutablePathPane(@Nullable Path path) {
        browseButton.textFieldProperty().set(pathField);
        browseButton.browseTypeProperty().set(BrowseButton.BrowseType.FILE);
        browseButton.defaultLocationProperty().set(path != null ? path.getParent() : Path.of(System.getProperty("user.home")));
        browseButton.selectionModeProperty().set(BrowseButton.BrowseSelectionMode.SINGLE);
        browseButton.parentWindowProperty().bind(sceneProperty().flatMap(Scene::windowProperty));

        pathField.setLocalizedPlaceholder("railroad.settings.vcs.git_executable_path.placeholder");
        gitExecutablePath.addListener((observable, oldValue, newValue) ->
            pathField.setText(newValue == null ? "" : Objects.toString(newValue)));
        pathField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isBlank()) {
                setGitExecutablePath(null);
            } else {
                try {
                    setGitExecutablePath(Path.of(newText));
                } catch (Exception exception) {
                    setGitExecutablePath(null);
                }
            }
        });

        if (path == null) {
            GitLocator.findGitExecutable().ifPresent(this::setGitExecutablePath);
        }

        downloadButton.setRounded(true);
        downloadButton.setVariant(ButtonVariant.PRIMARY);
        downloadButton.setOnAction(event -> {
            String postfix = switch (OperatingSystem.CURRENT) {
                case WINDOWS -> "windows";
                case MAC -> "mac";
                case LINUX -> "linux";
                default -> "";
            };

            String url = "https://git-scm.com/install/" + postfix;
            DesktopUtils.openUrl(url);
        });
        downloadButton.setVisible(OperatingSystem.CURRENT != OperatingSystem.UNKNOWN);

        getChildren().addAll(pathField, browseButton, downloadButton);
        setSpacing(10);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        setGitExecutablePath(path);
    }

    public Path getGitExecutablePath() {
        return gitExecutablePath.get();
    }

    public ObjectProperty<Path> gitExecutablePathProperty() {
        return gitExecutablePath;
    }

    public void setGitExecutablePath(Path gitExecutablePath) {
        this.gitExecutablePath.set(gitExecutablePath);
    }
}
