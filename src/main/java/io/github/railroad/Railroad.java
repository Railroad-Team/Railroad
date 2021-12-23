package io.github.railroad;

import io.github.railroad.project.Project;
import io.github.railroad.project.settings.theme.Themes;
import io.github.railroad.utility.WindowTools;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

/**
 * @author TurtyWurty
 */
public class Railroad extends Application {
	
	public static final String RAILROAD_CONFIG_FOLDER = System.getProperty("user.home") + "/.railroad/";

    private DiscordEventHandlers discordHandlers;
    private DiscordRichPresence discordRichPresence;
    private Setup setup;
    
    private static Project project;

    @Override
    public void start(final Stage primaryStage) throws Exception {
        this.setup = new Setup(Themes.DARK_THEME, "en_us");

        project = this.setup.project;
        setupDiscord(project);

        final var scene = new Scene(this.setup.mainPane);
        scene.getStylesheets().add(Railroad.class.getResource("/default.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("Railroad IDE");
        primaryStage.getIcons().add(new Image(Railroad.class.getResourceAsStream("/logo.png")));
        primaryStage.setWidth(this.setup.primaryScreenBounds.getWidth());
        primaryStage.setHeight(this.setup.primaryScreenBounds.getHeight());
        primaryStage.centerOnScreen();
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            WindowTools.displayQuitWindow(primaryStage, project.getTheme());
        });
        primaryStage.show();
        
        scene.setOnKeyPressed(this.setup::handleKeyPress);
    }
    
    public static String getResource(String name) {
    	return Railroad.class.getResource(name).getPath();
    }

    @Override
    public void stop() {
        this.setup.codeEditor.executor.shutdown();
        this.setup.liveDirs.dispose();
        DiscordRPC.discordShutdown();
    }

    /**
     * Sets up the Discord Rich Presence <br>
     */
    private void setupDiscord(Project project) {
        this.discordHandlers = new DiscordEventHandlers.Builder()
                .setReadyEventHandler(user -> System.out.println(user.username + "#" + user.discriminator))
                .build();
        DiscordRPC.discordInitialize("853387211897700394", this.discordHandlers, true);
        DiscordRPC.discordRunCallbacks();
        this.discordRichPresence = new DiscordRichPresence.Builder("Working on " + project.getProjectName())
                .setDetails("Making an amazing mod!").setBigImage("logo", "Railroad IDE")
                .setSmallImage("logo", "An IDE built for modders, made by modders.").setParty("", 0, 0)
                .setStartTimestamps(System.currentTimeMillis()).build();
        DiscordRPC.discordUpdatePresence(this.discordRichPresence);
    }
    
    public static void resetDiscordPresence() {
    	if (project != null) {
    		DiscordRichPresence newPresence = new DiscordRichPresence.Builder("Working on " + project.getProjectName())
                .setDetails("Making an amazing mod!").setBigImage("logo", "Railroad IDE")
                .setSmallImage("logo", "An IDE built for modders, made by modders.").setParty("", 0, 0)
                .setStartTimestamps(System.currentTimeMillis()).build();
    		DiscordRPC.discordUpdatePresence(newPresence);
    	}
    }
}
