package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GitCommands;
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
        CommandButtons.bind(this, GitCommands.COPY_HASH,
            () -> CommandContext.withArgument(null, this, commit));
    }

    /**
     * Runs the existing copy hash workflow.
     *
     * @param commit target commit
     */
    public static void execute(GitCommit commit) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        var content = new ClipboardContent();
        content.putString(commit.hash());
        clipboard.setContent(content);
    }
}
