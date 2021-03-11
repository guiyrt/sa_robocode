package Team.Helpers;

import java.io.Serializable;

/**
 * Implementation of class to represent Vectors
 */
public class Vector implements Serializable {
    private final Double x; // X component
    private final Double y; // Y component

    /**
     * Vector instance from input x and y components
     * @param x X component
     * @param y Y component
     */
    public Vector(Double x, Double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Vector instance from p1 to p2
     * @param p1 "From" Location
     * @param p2 "To" Location
     */
    public Vector(Location p1, Location p2) {
        this.x = p2.getX() - p1.getX();
        this.y = p2.getY() - p1.getY();
    }

    /**
     * Applies the vector to a given location
     * @param location Input location
     * @return Result of vector application on input location
     */
    public Location apply(Location location) {
        return new Location(location.getX() + this.x, location.getY() + this.y);
    }

    /**
     * Calculates length of vector
     * @return Length of vector
     */
    public double length() {
        return Math.sqrt(Math.pow(this.x, 2) + Math.pow(this.y, 2));
    }

    /**
     * Transform vector to unit vector
     * @return New vector with length 1 and same orientation
     */
    public Vector normalize() {
        double norm = this.length();
        return new Vector(this.x / norm, this.y / norm);
    }

    /**
     * Multiplies the vector by a constant
     * @param scale Constant multiplier
     * @return New scaled vector
     */
    public Vector scalar(double scale) {
        return new Vector(this.x * scale, this.y * scale);
    }

    /**
     * Calculates vector with opposite orientation
     * @return New vector with opposite orientation and same length
     */
    public Vector negative() {
        return this.scalar(-1);
    }

    /**
     * Sets vector length by first normalizing and then scaling
     * @param length Desired length
     * @return New vector with desired length
     */
    public Vector setLength(double length) {
        return this.normalize().scalar(length);
    }

    /**
     * Sum of vectors
     * @param vector Vector to sum
     * @return New vector with sum of x and y components from both vectors
     */
    public Vector add(Vector vector) {
        return new Vector(this.x + vector.x, this.y + vector.y);
    }
}