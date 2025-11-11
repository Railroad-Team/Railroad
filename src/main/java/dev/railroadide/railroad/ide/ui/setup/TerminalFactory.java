package dev.railroadide.railroad.ide.ui.setup;

import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import javafx.scene.paint.Color;

import java.nio.file.Path;

/**
 * Creates configured TerminalFX instances pointing at a specific project path.
 */
public final class TerminalFactory {
    private TerminalFactory() {
    }

    public static Terminal create(Path path) {
        var terminalConfig = new TerminalConfig();
        terminalConfig.setBackgroundColor(Color.rgb(16, 16, 16));
        terminalConfig.setForegroundColor(Color.rgb(240, 240, 240));
        terminalConfig.setCursorColor(Color.rgb(255, 0, 0, 0.5));
        var terminalBuilder = new TerminalBuilder(terminalConfig);
        terminalBuilder.setTerminalPath(path);
        return terminalBuilder.newTerminal().getTerminal();
    }
}
