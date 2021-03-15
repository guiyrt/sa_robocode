package sa_robocode.Helpers;

import java.awt.*;

/**
 * Static class implementation to declare methods used in onPaint
 */
public class Painter {
    private static final Integer LOCATION_PAINT_SIZE_PIXELS = 3; // Default size to paint Location instances

    /**
     * Draws a circle around a input location
     * @param g2d Graphics2D instance
     * @param color Color to draw
     * @param location Location of center of circle
     * @param diameter Diameter of circle to draw
     */
    public static void drawCircleAround(Graphics2D g2d, Color color, Location location, int diameter) {
        g2d.setColor(color);
        double offset = Math.sin(Math.PI/4) * (diameter/2.0);
        g2d.drawOval((int) (location.getX() - offset), (int) (location.getY() - offset), diameter, diameter);
    }

    /**
     * Draws a square on a input location
     * @param g2d Graphics2D instance
     * @param color Color to draw
     * @param location Location to paint square in
     */
    public static void drawLocation(Graphics2D g2d, Color color, Location location) {
        g2d.setColor(color);
        g2d.fillRect((int) location.getX(), (int) location.getY(), LOCATION_PAINT_SIZE_PIXELS, LOCATION_PAINT_SIZE_PIXELS);
    }

    /**
     * Draws a line between two locations
     * @param g2d Graphics2D instance
     * @param color Color to draw
     * @param start Start location of line
     * @param end End location of line
     */
    public static void drawLine(Graphics2D g2d, Color color, Location start, Location end) {
        g2d.setColor(color);
        g2d.drawLine((int) start.getX(), (int) start.getY(), (int) end.getX(), (int) end.getY());
    }
}