package io.github.railroad.settings.ui.categories.general;

import io.github.railroad.github.GithubAccount;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRLabel;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class GithubConnectionPane extends RRHBox {
    private final ImageView profileImage = new ImageView();
    private final Label profileUsername = new RRLabel();
    private final Label profileEmail = new RRLabel();
    private final Button connectButton = new Button("Connect");
    private final Button disconnectButton = new Button("Disconnect");

    private final ObjectProperty<GithubAccount> githubAccount = new SimpleObjectProperty<>();

    public GithubConnectionPane() {
        setSpacing(10);

        this.githubAccount.set(GithubAccount.loadExisting());
        profileImage.setPreserveRatio(true);
        profileImage.setFitHeight(50);
        profileImage.setFitWidth(50);

        profileUsername.setStyle("-fx-font-weight: bold");
        profileEmail.setStyle("-fx-font-size: 10pt");

        connectButton.setPrefWidth(100);
        connectButton.setOnAction(event -> {
            var account = GithubAccount.connect();
            if (account != null) {
                githubAccount.set(account);
            }
        });

        disconnectButton.setPrefWidth(100);
        disconnectButton.setOnAction(event -> {
            githubAccount.get().disconnect();
            githubAccount.set(null);
        });

        var detailsPane = new RRVBox();
        detailsPane.setSpacing(10);
        detailsPane.getChildren().addAll(profileUsername, profileEmail);
        detailsPane.setAlignment(Pos.CENTER);

        if (githubAccount.get() == null) {
            getChildren().setAll(connectButton);
        } else {
            profileImage.setImage(new Image(githubAccount.get().profileImageUrl()));
            profileUsername.setText(githubAccount.get().username());
            profileEmail.setText(githubAccount.get().email());

            getChildren().setAll(profileImage, detailsPane, disconnectButton);
        }

        setAlignment(Pos.CENTER_LEFT);

        this.githubAccount.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                getChildren().setAll(connectButton);
                return;
            }

            profileImage.setImage(new Image(newValue.profileImageUrl()));
            profileUsername.setText(newValue.username());
            profileEmail.setText(newValue.email());

            getChildren().setAll(profileImage, detailsPane, disconnectButton);
        });
    }
}
