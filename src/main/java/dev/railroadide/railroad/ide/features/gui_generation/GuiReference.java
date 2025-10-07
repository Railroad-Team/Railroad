package dev.railroadide.railroad.ide.features.gui_generation;

import java.awt.image.BufferedImage;

/**
 * @param topSide TODO work out if all sides/edges should be fully captured or just 3x1
 * @param rightSide
 * @param leftSide
 * @param topLeft
 * @param topRight
 * @param bottomLeftOuterCorner
 * @param bottomRightOuterCorner
 * @param bottomLeftInnerCorner
 * @param bottomRightInnerCorner
 * @param bottomLeftEdge
 * @param bottomRightEdge
 * @param bottomPadding
 * @param background
 */
public record GuiReference(BufferedImage topSide, BufferedImage rightSide, BufferedImage leftSide,
                           BufferedImage topLeft, BufferedImage topRight,
                           BufferedImage bottomLeftOuterCorner, BufferedImage bottomRightOuterCorner,
                           BufferedImage bottomLeftInnerCorner, BufferedImage bottomRightInnerCorner,
                           BufferedImage bottomLeftEdge, BufferedImage bottomRightEdge,
                           BufferedImage bottomPadding,
                           BufferedImage background) {
}
