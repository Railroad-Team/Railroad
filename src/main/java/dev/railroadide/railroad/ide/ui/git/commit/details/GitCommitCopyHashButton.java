package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Copies a commit hash to the system clipboard when activated.
 */
public class GitCommitCopyHashButton extends RRButton {
    /**
     * Creates a button that copies the supplied commit hash.
     *
     * @param commit commit to display or act on
     */
    public GitCommitCopyHashButton(GitCommit commit) {
        super("railroad.git.commit.details.button.copy_hash", FontAwesomeSolid.COPY);
        setVariant(ButtonVariant.PRIMARY);
        setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            var content = new ClipboardContent();
            content.putString(commit.hash());
            clipboard.setContent(content);
        });
    }
}
