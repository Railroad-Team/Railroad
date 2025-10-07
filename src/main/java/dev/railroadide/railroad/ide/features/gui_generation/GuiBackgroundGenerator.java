package dev.railroadide.railroad.ide.features.gui_generation;

import dev.railroadide.railroad.Railroad;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

/**
 * A class for generating GUI backgrounds based on a reference image.
 * TODO - Allow for removal and customization of inventory textures, including custom sizing.
 *  Render title? "Do not redistribute our games or any alterations of our games or game files"
 */
public class GuiBackgroundGenerator {

    private GuiBackgroundGenerator() {
    }

    /**
     * Creates a new builder instance for generating a GUI background.
     * @return A new Builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int width;
        private int height;
        private BufferedImage referenceImage;

        /**
         * Sets the width of the GUI background to be generated.
         * @param width The desired width in pixels.
         * @return The builder instance
         */
        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the height of the GUI background to be generated.
         * @param height The desired height in pixels.
         * @return The builder instance
         */
        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        /**
         * Sets the reference image to be used for generating the GUI background.
         * @param referenceImage The reference image as a BufferedImage.
         * @return The builder instance
         */
        public Builder setReferenceImage(BufferedImage referenceImage) {
            this.referenceImage = referenceImage;
            return this;
        }

        /**
         * Generates a GuiReference object by extracting necessary components from the reference image.
         * @return A GuiReference object containing the extracted components.
         */
        private GuiReference generateReference() {
            var topSide = this.referenceImage.getSubimage(4, 0, 1, 3);
            var rightSide = this.referenceImage.getSubimage(197, 3, 3, 1);
            var leftSide = this.referenceImage.getSubimage(0, 3, 3, 1);
            var topLeft = this.referenceImage.getSubimage(0, 0, 4, 4);
            var topRight = this.referenceImage.getSubimage(197, 0, 3, 3);

            var bottomLeftOuterCorner = this.referenceImage.getSubimage(0, 134, 3, 3);
            var bottomRightOuterCorner = this.referenceImage.getSubimage(197, 134, 3, 3);
            var bottomLeftInnerCorner = this.referenceImage.getSubimage(12, 134, 3, 4);
            var bottomRightInnerCorner = this.referenceImage.getSubimage(185, 134, 3, 4);
            var bottomLeftEdge = this.referenceImage.getSubimage(3, 134, 1, 3);
            var bottomRightEdge = this.referenceImage.getSubimage(196, 134, 1, 3);

            var bottomPadding = this.referenceImage.getSubimage(15, 134, 170, 4);
            var background = this.referenceImage.getSubimage(3, 3, 194, 131);

            return new GuiReference(
                topSide,
                rightSide, leftSide,
                topLeft, topRight,
                bottomLeftOuterCorner, bottomRightOuterCorner,
                bottomLeftInnerCorner, bottomRightInnerCorner,
                bottomLeftEdge, bottomRightEdge,
                bottomPadding, background);
        }

        /**
         * Draws the provided image onto the graphics context at the specified coordinates.
         * @param g The Graphics2D context to draw on.
         * @param img The image to be drawn.
         * @param x1 The starting x-coordinate.
         * @param y1 The starting y-coordinate.
         * @param x2 The ending x-coordinate.
         * @param y2 The ending y-coordinate.
         */
        private void draw(Graphics2D g, BufferedImage img, int x1, int y1, int x2, int y2) {
            g.drawImage(img, x1, y1, x2, y2, 0, 0, img.getWidth(), img.getHeight(), null);
        }

        /**
         * Draws the top section of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         */
        private void drawTop(Graphics2D g, GuiReference ref) {
            //Top Side
            draw(g, ref.topSide(),
                ref.topLeft().getWidth(),
                0,
                this.width - ref.topRight().getWidth(),
                ref.topSide().getHeight());

            //Top Left corner
            draw(g, ref.topLeft(),
                0,
                0,
                ref.topLeft().getWidth(),
                ref.topLeft().getHeight());

            //Top Right corner
            draw(g, ref.topRight(),
                this.width - ref.topRight().getWidth(),
                0,
                this.width,
                ref.topRight().getHeight());
        }

        /**
         * Draws the sides of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         * @param expanded If the sides need to be taller due to the GUI being 176px wide
         */
        private void drawSides(Graphics2D g, GuiReference ref, boolean expanded) {
            //Left Side
            draw(g, ref.leftSide(),
                0,
                ref.topLeft().getHeight(),
                ref.leftSide().getWidth(),
                expanded ? this.height - ref.bottomPadding().getHeight() : this.height);

            //Right Side
            draw(g, ref.rightSide(),
                this.width - ref.rightSide().getWidth(),
                ref.topRight().getHeight(),
                this.width,
                expanded ? this.height - ref.bottomPadding().getHeight() : this.height);
        }

        /**
         * Draws the outer corners of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         */
        private void drawOuterCorners(Graphics2D g, GuiReference ref) {
            //Left Outer Corner
            draw(g, ref.bottomLeftOuterCorner(),
                0,
                this.height - ref.bottomLeftOuterCorner().getHeight() - 1,
                ref.bottomLeftOuterCorner().getWidth(),
                this.height - 1);

            //Right Outer Corner
            draw(g, ref.bottomRightOuterCorner(),
                this.width - ref.bottomRightOuterCorner().getWidth(),
                this.height - ref.bottomRightOuterCorner().getHeight() - 1,
                this.width,
                this.height - 1);
        }

        /**
         * Draws the inner corners of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         */
        private void drawInnerCorners(Graphics2D g, GuiReference ref) {
            //Left Inner Corner
            draw(g, ref.bottomLeftInnerCorner(),
                (this.width - 176) / 2,
                this.height - ref.bottomLeftInnerCorner().getHeight(),
                (this.width - 176) / 2 + ref.bottomLeftInnerCorner().getWidth(),
                this.height);

            //Right Inner Corner
            draw(g, ref.bottomRightInnerCorner(),
                this.width - ((this.width - 176) / 2) - ref.bottomRightInnerCorner().getWidth(),
                this.height - ref.bottomRightInnerCorner().getHeight(),
                this.width - ((this.width - 176) / 2),
                this.height);
        }

        /**
         * Draws the corner edges of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         */
        private void drawCornerEdges(Graphics2D g, GuiReference ref) {
            //Left Edge
            draw(g, ref.bottomLeftEdge(),
                ref.bottomLeftOuterCorner().getWidth(),
                this.height - ref.bottomLeftEdge().getHeight() - 1,
                ((this.width - 176) / 2) + 1,
                this.height - 1);

            //Right Edge
            draw(g, ref.bottomRightEdge(),
                this.width - ((this.width - 176) / 2),
                this.height - ref.bottomRightEdge().getHeight() - 1,
                this.width - ref.bottomRightOuterCorner().getWidth(),
                this.height - 1);
        }

        /**
         * Draws the corners of the GUI background, including outer corners, inner corners and corner edges.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         * @param needsSpacing If the inner corners and corner edges are needed.
         */
        private void drawCorners(Graphics2D g, GuiReference ref, boolean needsSpacing) {
            drawOuterCorners(g, ref);
            if (needsSpacing) {
                drawInnerCorners(g, ref);
                drawCornerEdges(g, ref);
            }
        }

        /**
         * Draws the bottom padding of the GUI background.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         * @param expanded If the padding needs to be wider or not.
         */
        private void drawBottomPadding(Graphics2D g, GuiReference ref, boolean expanded) {
            //Bottom Padding
            if (expanded) {
                draw(g, ref.bottomPadding(),
                    ((this.width - 176) / 2) + ref.bottomLeftInnerCorner().getWidth(),
                    this.height - ref.bottomPadding().getHeight() - 1,
                    this.width - ((this.width - 176) / 2) - ref.bottomRightInnerCorner().getWidth(),
                    this.height);
            } else {
                draw(g, ref.bottomPadding(),
                    ref.bottomLeftOuterCorner().getWidth(),
                    this.height - ref.bottomPadding().getHeight() - 1,
                    this.width - ref.bottomRightOuterCorner().getWidth(),
                    this.height);
            }
        }

        /**
         * Draws the background of the GUI.
         * @param g The Graphics2D context to draw on.
         * @param ref The GuiReference containing the components to be drawn.
         */
        private void drawBackground(Graphics2D g, GuiReference ref) {
            draw(g, ref.background(),
                ref.leftSide().getWidth(),
                ref.topSide().getHeight(),
                this.width - ref.rightSide().getWidth(),
                this.height - ref.bottomPadding().getHeight());
        }

        /**
         * Draws the GUI background based on the provided GuiReference and the specified width and height.
         * @param reference The GuiReference containing the components to be used for drawing.
         * @return A BufferedImage representing the drawn GUI background.
         */
        private BufferedImage drawGuiBackground(GuiReference reference) {
            var result = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = result.createGraphics();

            drawTop(graphics, reference);
            drawSides(graphics, reference, this.width > 176);

            // If the inner corners and corner edges are needed.
            boolean wider = this.width > 176
                + reference.bottomLeftOuterCorner().getWidth()
                + reference.bottomRightOuterCorner().getWidth();

            if (this.width > 176)
                drawCorners(graphics, reference, wider);

            drawBottomPadding(graphics, reference, wider);
            drawBackground(graphics, reference);

            graphics.dispose();
            return result;
        }

        /**
         * Builds the GUI background image based on the specified parameters and reference image.
         * @return A WritableImage representing the generated GUI background.
         */
        public WritableImage build() {
            try {
                if (this.referenceImage == null) {
                    throw new IllegalStateException("Reference image must be set before building the GUI background.");
                }
                var textureResult = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

                URL inventoryImageUrl = Railroad.getResource("/inventory_textures/background/gui_inventory.png");
                BufferedImage inventoryImage = ImageIO.read(inventoryImageUrl);

                var guiTexture = drawGuiBackground(generateReference());

                var textureGraphics = textureResult.createGraphics();
                textureGraphics.drawImage(guiTexture, 0, 0, null);
                textureGraphics.drawImage(inventoryImage,
                    (guiTexture.getWidth() - inventoryImage.getWidth()) / 2, height,
                    null);
                textureGraphics.dispose();

                return SwingFXUtils.toFXImage(textureResult, null);
            } catch (Exception e) {
                Railroad.LOGGER.error("Failed to load inventory texture for GUI background generation! {}", e);
                return null;
            }
        }
    }
}
