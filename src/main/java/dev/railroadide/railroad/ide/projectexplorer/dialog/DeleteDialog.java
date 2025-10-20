package dev.railroadide.railroad.ide.projectexplorer.dialog;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.defaults.DefaultDocument;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroadpluginapi.events.FileEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteDialog {
    public static void open(Path path) {
        WindowBuilder.createDialog(
            "railroad.dialog.delete.title",
            "railroad.dialog.delete.title",
            "railroad.dialog.delete.message",
            () -> {
                try {
                    if (Files.isDirectory(path)) {
                        FileUtils.deleteFolder(path);
                    } else {
                        Files.deleteIfExists(path);
                    }

                    Railroad.EVENT_BUS.publish(new FileEvent(new DefaultDocument(path.getFileName().toString(), path), FileEvent.EventType.DELETED));
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to delete file or directory: {}", path, exception);
                }
            },
            () -> {}
        );
    }
}
