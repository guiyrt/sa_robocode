package Team.Helpers;

import java.io.Serializable;

/**
 * Class implementation to represent Cartesian Coordinates
 */
public class Location implements Serializable {
    private static final double DEFAULT_DISTANCE_TOLERANCE = Math.pow(1, -5); // Default tolerance value to determine if two Locations are the same

    private final Double x; // X coordinate
    private final Double y; // Y coordinate

    /**
     * Constructor given X and Y coordinates
     * @param x X coordinate
     * @param y Y coordinate
     */
    public Location(Double x, Double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Gets X coordinate
     * @return X coordinate
     */
    public double getX() {
        return this.x;
    }

    /**
     * Gets Y coordinate
     * @return Y coordinate
     */
    public double getY() {
        return this.y;
    }

    /**
     * Calculates distance to another location
     * @param location Location to measure distance to
     * @return Distance to location
     */
    public Double distanceTo(Location location) {
        return Math.sqrt(Math.pow(this.getX() - location.getX(), 2) + Math.pow(this.getY() - location.getY(), 2));
    }

    /**
     * Determines if two locations are the same, with default tolerance
     * @param location location to test
     * @return True if with distance between two points is under the default tolerance value
     */
    public boolean sameAs(Location location) {
        return this.nearTo(location, DEFAULT_DISTANCE_TOLERANCE);
    }

    /**
     * Determines if two locations are closer than a given tolerance distance
     * @param location location to test
     * @param tolerance tolerance value
     * @return True if with distance between two points is under the given tolerance value
     */
    public boolean nearTo(Location location, double tolerance) {
        return Math.abs(distanceTo(location)) < tolerance;
    }

    /**
     * Checks if two Location instances represent the same Cartesian Coordinates
     * @param location input location
     * @return True if they represent exactly the same point, false otherwise
     */
    public boolean equals(Location location) {
        return x.equals(location.getX()) && y.equals(location.getY());
    }
}