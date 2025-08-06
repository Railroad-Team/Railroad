package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

public class VirtualFlow<T> extends Region {
    @Getter
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final Function<T, VirtualCell<T>> cellFactory;
    private final Deque<VirtualCell<T>> cellPool = new ArrayDeque<>();
    private final List<VirtualCell<T>> activeCells = new ArrayList<>();

    private final Pane contentPane = new Pane();

    private final DoubleProperty cellSize = new SimpleDoubleProperty(30);
    @Getter
    private Orientation orientation = Orientation.VERTICAL;

    public VirtualFlow(Function<T, VirtualCell<T>> cellFactory) {
        this.cellFactory = cellFactory;
        getChildren().add(contentPane);

        items.addListener((ListChangeListener<T>) change -> {
            while (change.next()) {
                requestLayout();
            }
        });

        cellSize.addListener((obs, oldVal, newVal) -> requestLayout());
    }

    public void setItems(ObservableList<T> items) {
        this.items.setAll(items);
    }

    public DoubleProperty cellSizeProperty() { return cellSize; }
    public double getCellSize() { return cellSize.get(); }
    public void setCellSize(double size) { this.cellSize.set(size); }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
        requestLayout();
    }

    private ScrollPane scrollPane;

    public ScrollPane makeScrollable() {
        scrollPane = new ScrollPane(this);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scrollPane;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double size = cellSize.get();

        double scrollOffset = 0;

        if (scrollPane != null) {
            double totalHeight = items.size() * size;
            double visibleHeight = scrollPane.getViewportBounds().getHeight();
            scrollOffset = scrollPane.getVvalue() * Math.max(1, totalHeight - visibleHeight);
        }

        int startIndex = (int) Math.floor(scrollOffset / size);
        int visibleCount = (int) Math.ceil((orientation == Orientation.VERTICAL ? h : w) / size) + 1;

        // Recycle
        for (VirtualCell<T> cell : activeCells) {
            cell.updateItem(null, true);
            cellPool.offerLast(cell);
        }
        activeCells.clear();

        for (int i = 0; i < visibleCount; i++) {
            int index = startIndex + i;
            if (index >= items.size()) break;

            T item = items.get(index);
            VirtualCell<T> cell = cellPool.pollFirst();
            if (cell == null) {
                cell = cellFactory.apply(null);
            }

            cell.updateItem(item, false);
            if (!contentPane.getChildren().contains(cell)) {
                contentPane.getChildren().add(cell);
            }

            double x = orientation == Orientation.HORIZONTAL ? (index * size) : 0;
            double y = orientation == Orientation.VERTICAL ? (index * size) : 0;
            double width = orientation == Orientation.HORIZONTAL ? size : w;
            double height = orientation == Orientation.VERTICAL ? size : h;

            cell.resizeRelocate(x, y, width, height);
            activeCells.add(cell);
        }

        double totalWidth = orientation == Orientation.HORIZONTAL ? items.size() * size : w;
        double totalHeight = orientation == Orientation.VERTICAL ? items.size() * size : h;
        contentPane.resizeRelocate(0, 0, totalWidth, totalHeight);
    }

    @Override
    protected double computePrefWidth(double height) {
        return orientation == Orientation.HORIZONTAL ? items.size() * cellSize.get() : 300;
    }

    @Override
    protected double computePrefHeight(double width) {
        return orientation == Orientation.VERTICAL ? items.size() * cellSize.get() : 200;
    }
}
