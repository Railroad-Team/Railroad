package dev.railroadide.railroad.welcome.imports;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.vcs.Repository;
import dev.railroadide.core.vcs.connections.AbstractConnection;
import dev.railroadide.core.vcs.connections.VCSProfile;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.GitUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Getter
public class WelcomeImportProjectsPane extends RRHBox {
    private final RRListView<Object> sidebar = new RRListView<>();
    private final String REPO_URL_OPTION = "REPO_URL_OPTION";
    private final RRVBox rightPane = new RRVBox(18);
    private final RRListView<Repository> repositoryListView = new RRListView<>();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final RRTextField searchField = new RRTextField();
    private final RRVBox contentBox = new RRVBox(12);
    private final ObservableList<Repository> allRepositories = FXCollections.observableArrayList();
    private final LocalizedLabel loadingLabel = new LocalizedLabel("railroad.importprojects.loading");
    private final LocalizedLabel errorLabel = new LocalizedLabel("railroad.importprojects.error");
    private final LocalizedLabel emptyLabel = new LocalizedLabel("railroad.importprojects.empty");
    private final Map<VCSProfile, AbstractConnection> connectionCache = new HashMap<>();
    private boolean isLoading = false;
    private VCSProfile lastLoadedProfile = null;
    private String currentFilter = "";
    private String lastBaseDirectory = System.getProperty("user.home");

    public WelcomeImportProjectsPane() {
        sidebar.setPrefWidth(200);
        sidebar.setAnimationsEnabled(false);
        sidebar.setCellFactory(param -> new AccountListCell());
        sidebar.getItems().add(REPO_URL_OPTION);
        sidebar.getItems().addAll(Railroad.REPOSITORY_MANAGER.getProfiles());
        sidebar.setFocusTraversable(false);
        sidebar.getSelectionModel().selectFirst();
        sidebar.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRightPane(newVal));

        repositoryListView.setCellFactory(param -> new ImportProjectListCell());

        rightPane.setPadding(new Insets(18));
        rightPane.prefHeightProperty().bind(heightProperty());

        getChildren().addAll(sidebar, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        updateRightPane(sidebar.getSelectionModel().getSelectedItem());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentFilter = newVal;
            if (sidebar.getSelectionModel().getSelectedItem() instanceof VCSProfile profile && connectionCache.containsKey(profile)) {
                filterRepositories(currentFilter);
            }
        });
    }

    private static void showCloneLoading(CompletableFuture<Boolean> future, Path projectDir) {
        var loadingBox = new RRVBox(18);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(40, 0, 40, 0));
        VBox.setVgrow(loadingBox, Priority.ALWAYS);

        var progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(-1);
        progressIndicator.setMaxSize(48, 48);
        progressIndicator.setMinSize(48, 48);
        loadingBox.getChildren().addAll(progressIndicator, new LocalizedLabel("railroad.importprojects.clone.loading"));

        var stage = new Stage();
        stage.setTitle(L18n.localize("railroad.importprojects.clone.title"));
        stage.setScene(new Scene(loadingBox, 300, 200));
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });

        future.thenAcceptAsync(success -> {
            Platform.runLater(stage::close);

            var project = Railroad.PROJECT_MANAGER.newProject(new Project(projectDir));
            if (SettingsHandler.getValue(Settings.SWITCH_TO_IDE_AFTER_IMPORT)) {
                Platform.runLater(() -> IDESetup.switchToIDE(project));
            }
        }).exceptionally(exception -> {
            showError(exception.getLocalizedMessage(), false);
            Platform.runLater(stage::close);
            return null;
        });

        stage.showAndWait();
    }

    private static void showError(String content) {
        showError(content, true);
    }

    private static void showError(String content, boolean translateContent) {
        Platform.runLater(() -> {
            var alert = new Alert(AlertType.ERROR);
            alert.setTitle(L18n.localize("railroad.importprojects.clone"));
            alert.setHeaderText(null);
            alert.setContentText(translateContent ? L18n.localize(content) : content);
            alert.showAndWait();
        });
    }

    private void filterRepositories(String filter) {
        if (sidebar.getSelectionModel().getSelectedItem() instanceof VCSProfile profile && connectionCache.containsKey(profile)) {
            AbstractConnection connection = connectionCache.get(profile);
            List<Repository> baseList = connection.getRepositories();
            List<Repository> filtered = new ArrayList<>();
            if (filter == null || filter.isBlank()) {
                filtered.addAll(baseList);
            } else {
                String lower = filter.toLowerCase(Locale.ROOT);
                for (Repository repo : baseList) {
                    if (repo.getRepositoryName().toLowerCase(Locale.ROOT).contains(lower) ||
                            repo.getRepositoryURL().toLowerCase(Locale.ROOT).contains(lower)) {
                        filtered.add(repo);
                    }
                }
            }

            repositoryListView.getItems().setAll(filtered);
            if (filtered.isEmpty()) {
                showEmptyState();
            }
        }
    }

    private void showEmptyState() {
        contentBox.getChildren().removeAll(repositoryListView, progressIndicator, loadingLabel, errorLabel, emptyLabel);

        emptyLabel.setAlignment(Pos.CENTER);
        emptyLabel.setPadding(new Insets(20));
        emptyLabel.getStyleClass().add("welcome-empty-state");
        contentBox.getChildren().add(emptyLabel);
    }

    private void loadRepositoriesForProfile(VCSProfile profile, boolean forceRefresh) {
        if (!forceRefresh && connectionCache.containsKey(profile)) {
            filterRepositories(currentFilter);
            searchField.setDisable(false);
            isLoading = false;
            lastLoadedProfile = profile;
            updateRightPane(profile);
            return;
        }

        isLoading = true;
        updateRightPane(profile);

        var connection = profile.createConnection();
        connection.fetchRepositories();
        connectionCache.put(profile, connection);

        connection.getRepositories().addListener((ListChangeListener<Repository>) change ->
                Platform.runLater(() -> {
                    filterRepositories(currentFilter);
                    searchField.setDisable(false);
                    isLoading = false;
                    lastLoadedProfile = profile;
                    updateRightPane(profile);
                }));
    }

    private void updateRightPane(Object selected) {
        rightPane.getChildren().clear();

        if (isLoading) {
            var loadingBox = new RRVBox(18);
            loadingBox.setAlignment(Pos.CENTER);
            loadingBox.setPadding(new Insets(40, 0, 40, 0));
            VBox.setVgrow(loadingBox, Priority.ALWAYS);
            progressIndicator.setVisible(true);
            progressIndicator.setProgress(-1);
            progressIndicator.setMaxSize(48, 48);
            progressIndicator.setMinSize(48, 48);
            loadingBox.getChildren().addAll(progressIndicator, loadingLabel);
            rightPane.getChildren().add(loadingBox);
            return;
        }

        contentBox.getChildren().clear();

        var title = new LocalizedLabel("railroad.importprojects.title");
        title.getStyleClass().add("welcome-title");

        searchField.setLocalizedPlaceholder("railroad.importprojects.search");
        searchField.setPrefHeight(36);

        var refreshButton = new RRButton("");
        var refreshIcon = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        refreshIcon.setIconSize(16);
        refreshButton.setGraphic(refreshIcon);
        refreshButton.setOnAction($ -> {
            if (selected instanceof VCSProfile profile) {
                loadRepositoriesForProfile(profile, true);
            }
        });

        var titleBox = new RRHBox(10);
        titleBox.getChildren().addAll(title, refreshButton);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(titleBox, searchField);
        contentBox.setSpacing(18);

        if (selected instanceof VCSProfile profile) {
            if (!connectionCache.containsKey(profile) || profile != lastLoadedProfile) {
                loadRepositoriesForProfile(profile, false);
                return;
            }

            filterRepositories(currentFilter);
            if (!repositoryListView.getItems().isEmpty()) {
                if (!contentBox.getChildren().contains(repositoryListView)) {
                    contentBox.getChildren().add(repositoryListView);
                    VBox.setVgrow(repositoryListView, Priority.ALWAYS);
                }
            }

            var accountBox = new RRVBox(18);
            accountBox.getChildren().add(contentBox);
            VBox.setVgrow(contentBox, Priority.ALWAYS);

            var directoryField = new RRTextField("railroad.importprojects.directory");
            directoryField.setText(lastBaseDirectory != null ? lastBaseDirectory : System.getProperty("user.home"));

            var chooseDirButton = new RRButton("");
            var folderIcon = new FontIcon(FontAwesomeSolid.FOLDER);
            folderIcon.setIconSize(16);
            chooseDirButton.setGraphic(folderIcon);

            var dirBox = new RRHBox(10);
            dirBox.getChildren().addAll(directoryField, chooseDirButton);
            HBox.setHgrow(directoryField, Priority.ALWAYS);

            var cloneButton = new RRButton("railroad.importprojects.clone");
            cloneButton.setMaxWidth(Double.MAX_VALUE);

            repositoryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldRepo, newRepo) -> {
                if (newRepo != null) {
                    String baseDir = lastBaseDirectory != null ? lastBaseDirectory : System.getProperty("user.home");
                    String repoName = newRepo.getRepositoryName();
                    repoName = repoName.contains("/") ? repoName.substring(repoName.lastIndexOf('/') + 1) : repoName;
                    directoryField.setText(Path.of(baseDir, repoName).toString());
                }
            });

            chooseDirButton.setOnAction($ -> {
                var chooser = new DirectoryChooser();
                File selectedDir = chooser.showDialog(Railroad.WINDOW_MANAGER.getPrimaryStage());
                if (selectedDir != null) {
                    lastBaseDirectory = selectedDir.getAbsolutePath();
                    Repository repo = repositoryListView.getSelectionModel().getSelectedItem();
                    if (repo != null) {
                        String repoName = repo.getRepositoryName();
                        repoName = repoName.contains("/") ? repoName.substring(repoName.lastIndexOf('/') + 1) : repoName;
                        directoryField.setText(Path.of(lastBaseDirectory, repoName).toString());
                    } else {
                        directoryField.setText(lastBaseDirectory);
                    }
                }
            });

            cloneButton.setOnAction($ -> {
                String dir = directoryField.getText();
                Repository repo = repositoryListView.getSelectionModel().getSelectedItem();
                if (repo == null || dir == null || dir.isBlank()) {
                    showError("railroad.importprojects.clone.invalid_selection");
                    return;
                }

                Path projectDir = Path.of(dir);
                if (Files.exists(projectDir) && Files.isDirectory(projectDir) && !FileUtils.isDirectoryEmpty(projectDir)) {
                    showError("railroad.importprojects.clone.directory_not_empty");
                    return;
                }

                CompletableFuture<Boolean> successFuture = profile.createConnection().cloneRepo(repo, projectDir);
                showCloneLoading(successFuture, projectDir);
            });

            accountBox.getChildren().addAll(dirBox, cloneButton);
            rightPane.getChildren().add(accountBox);
            VBox.setVgrow(accountBox, Priority.ALWAYS);
        } else if (REPO_URL_OPTION.equals(selected)) {
            var urlBox = new RRVBox(18);
            urlBox.setAlignment(Pos.CENTER);

            var urlField = new RRTextField("railroad.importprojects.repositoryurl.placeholder");
            urlField.setPrefWidth(320);

            var directoryField = new RRTextField("railroad.importprojects.directory");
            var chooseDirButton = new RRButton("");
            var folderIcon = new FontIcon(FontAwesomeSolid.FOLDER);
            folderIcon.setIconSize(16);
            chooseDirButton.setGraphic(folderIcon);
            var dirBox = new RRHBox(10);
            dirBox.getChildren().addAll(directoryField, chooseDirButton);
            HBox.setHgrow(directoryField, Priority.ALWAYS);

            var cloneButton = new RRButton("railroad.importprojects.clone");
            cloneButton.setMaxWidth(Double.MAX_VALUE);

            chooseDirButton.setOnAction($ -> {
                var chooser = new DirectoryChooser();
                File selectedDir = chooser.showDialog(Railroad.WINDOW_MANAGER.getPrimaryStage());
                if (selectedDir != null) {
                    directoryField.setText(selectedDir.getAbsolutePath());
                }
            });

            cloneButton.setOnAction($ -> {
                String url = urlField.getText();
                String dir = directoryField.getText();
                if (url == null || url.isBlank()) {
                    showError("railroad.importprojects.clone.invalid_url");
                    return;
                } else if (dir == null || dir.isBlank()) {
                    showError("railroad.importprojects.clone.invalid_directory");
                    return;
                }

                String folderName = url.substring(url.lastIndexOf('/') + 1);
                if (folderName.endsWith(".git")) {
                    folderName = folderName.substring(0, folderName.length() - 4);
                }

                Path projectDir = Path.of(dir).resolve(folderName);
                if (Files.exists(projectDir) && Files.isDirectory(projectDir) && !FileUtils.isDirectoryEmpty(projectDir)) {
                    showError("railroad.importprojects.clone.directory_not_empty");
                    return;
                }

                CompletableFuture<Boolean> successFuture = GitUtils.clone(url, projectDir);
                showCloneLoading(successFuture, projectDir);
            });

            urlBox.getChildren().addAll(urlField, dirBox, cloneButton);
            rightPane.getChildren().add(urlBox);
        }

        rightPane.requestFocus();
    }
}
