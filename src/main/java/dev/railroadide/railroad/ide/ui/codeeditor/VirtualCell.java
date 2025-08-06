package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.scene.layout.Region;
import lombok.Getter;

@Getter
public abstract class VirtualCell<T> extends Region {
    private T item;
    private boolean empty;

    public void updateItem(T item, boolean empty) {
        this.item = item;
        this.empty = empty;
        setVisible(!empty);
        setManaged(!empty);
        onUpdate(item, empty);
    }

    protected abstract void onUpdate(T item, boolean empty);
}
