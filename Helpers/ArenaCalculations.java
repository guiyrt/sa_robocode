package sa_robocode.Helpers;

/**
 * Static class implementation to deal with arena angle and coordinates calculations
 */
public class ArenaCalculations {
    /**
     *  Angles in robocode arena are measure from Y axis, clockwise
     *  Angles in polar coordinates are measured from X axis, counter-clockwise
     */
    private static final Double POLAR_TO_ARENA_ANGLE_OFFSET = 90.0;
    private static final Double FULL_ROTATION = 360.0;

    /**
     * Adds a delta angle to an input angle
     * @param angle Original angle
     * @param delta Angle to add
     * @return Value of original angle plus delta angle
     */
    private static double sumToAngle(double angle, double delta) {
        return (angle + delta) % FULL_ROTATION;
    }

    /**
     * Converts a clockwise angle to a counter-clockwise angle, and vice-versa.
     * @param angle Angle in degrees
     * @return Input angle with reverse orientation.
     */
    private static double reverseAngleOrientation(double angle) {
        return FULL_ROTATION - angle;
    }

    /**
     * Converts between arena angle and polar angle
     * Arena angles: From Y axis, clockwise
     * Polar angles: From X axis, counter-clockwise
     * @param angle Angle in degrees
     * @return If input angle was an arena angle, returns polar equivalent, and the other way around.
     */
    public static double convertAngleToPolarOrArena(double angle) {
        return sumToAngle(reverseAngleOrientation(angle), POLAR_TO_ARENA_ANGLE_OFFSET);
    }

    /**
     * Given an origin and a location, calculates the arena angle from origin to location
     * @param origin origin Location
     * @param location target Location
     * @return Angle in degrees, of target location from input origin (from Y axis, clockwise)
     */
    public static double angleFromOriginToLocation(Location origin, Location location) {
        Location locationInPolar = new Vector(origin, location).apply(new Location(0.0, 0.0));
        double angleToLocation = Math.toDegrees(Math.atan2(locationInPolar.getY(), -locationInPolar.getX()));
        return sumToAngle(angleToLocation, -POLAR_TO_ARENA_ANGLE_OFFSET);
    }

    /**
     * Calculates the necessary rotation between two angles, clockwise
     * @param from Starting angle
     * @param to Desired angle
     * @return Clockwise rotation from starting angle to desired angle
     */
    public static double angleDeltaRight(double from, double to) {
        return (from > to) ? FULL_ROTATION - from + to : to - from;
    }

    /**
     * Given origin, angle and length (information of Polar Coordinates), converts to Cartesian Coordinates
     * @param origin Point of reference
     * @param angle Angle in degrees (from Y axis, clockwise)
     * @param length Arc length
     * @return Location in Cartesian Coordinates from input Polar Coordinates
     */
    public static Location polarInfoToLocation(Location origin, Double angle, Double length) {
        double polarAngle = convertAngleToPolarOrArena(angle);
        double deltaX = Math.cos(Math.toRadians(polarAngle)) * length;
        double deltaY = Math.sin(Math.toRadians(polarAngle)) * length;

        return new Location(origin.getX() + deltaX, origin.getY() + deltaY);
    }

    /**
     * Given an angle in arena degrees, calculates a unit vector with that orientation
     * @param angle Arena angle in degrees (from Y axis, clockwise)
     * @return Unit vector with same angle as input angle
     */
    public static Vector angleToUnitVector(double angle) {
        double polarAngle = convertAngleToPolarOrArena(angle);
        double componentX = Math.cos(Math.toRadians(polarAngle));
        double componentY = Math.sin(Math.toRadians(polarAngle));

        return new Vector(componentX, componentY);
    }

    /**
     * Checks if a certain Location is inside the arena playable area
     * @param location Location to validate
     * @param arenaWidth Arena width
     * @param arenaHeight Arena Height
     * @return True if location is inside arena, false otherwise.
     */
    public static boolean isInsideArena(Location location, Double arenaWidth, Double arenaHeight) {
        return (location.getX() <= arenaWidth) && (location.getY() <= arenaHeight) && (location.getX() >= 0) && (location.getY() >= 0);
    }

    public static double angleRightOffsetToLocation(double heading, Location origin, Location target) {
        double theta = ArenaCalculations.angleFromOriginToLocation(origin, target);
        double rightAngleOffset = ArenaCalculations.angleDeltaRight(heading, theta);

        return (rightAngleOffset < reverseAngleOrientation(rightAngleOffset)) ? rightAngleOffset : rightAngleOffset - FULL_ROTATION;
    }
}