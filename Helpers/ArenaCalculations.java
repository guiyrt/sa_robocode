package sa_robocode.Helpers;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
    private static final Double ROBOT_DIMENSION = 36.0;
    private static final Double ROBOT_CENTER_TO_EDGE = Math.sqrt(Math.pow(ROBOT_DIMENSION/2, 2) + Math.pow(ROBOT_DIMENSION/2, 2));
    private static final Double SAFE_TOLERANCE = 6.0;

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

    public static List<Location> getEdgesFromCenterLocation(Location robot, Double heading, Double centerToCornerLength) {
        List<Location> edges = new ArrayList<>();
        double angleDegrees = 90 - ((heading + 45) % 90), deltaX, deltaY;

        for (int i=0; i<4; i++) {
            angleDegrees += 90;
            deltaY = Math.sin(Math.toRadians(angleDegrees)) * centerToCornerLength;
            deltaX = Math.cos(Math.toRadians(angleDegrees)) * centerToCornerLength;

            edges.add(new Location(robot.getX() + deltaX, robot.getY() + deltaY));
        }

        return edges;
    }

    public static boolean isLocationInsideRobot(Location robot, double robotHeading, Location location) {
        List<Location> robotEdges = getEdgesFromCenterLocation(robot, robotHeading, ROBOT_CENTER_TO_EDGE + SAFE_TOLERANCE);
        Path2D robotLimits = new Path2D.Double();

        Location leftEdge = ArenaCalculations.getEdge(robotEdges, false, true, true);
        Location rightEdge = ArenaCalculations.getEdge(robotEdges, false, false, false);
        Location bottomEdge = ArenaCalculations.getEdge(robotEdges, true, true, false);
        Location topEdge = ArenaCalculations.getEdge(robotEdges, true, false, true);

        robotLimits.moveTo(topEdge.getX(), topEdge.getY());
        robotLimits.lineTo(rightEdge.getX(), rightEdge.getY());
        robotLimits.lineTo(bottomEdge.getX(), bottomEdge.getY());
        robotLimits.lineTo(leftEdge.getX(), leftEdge.getY());
        robotLimits.closePath();

        return robotLimits.contains(new Point2D.Double(location.getX(), location.getY()));
    }

    // For example, to get top edge, and the one to the left if there are two edges with same Y:
    // getEdge(true, false, true), in other words, order by Y, the farthest way from Y axis, and the closest to X axis if top two edges have the same Y
    public static Location getEdge(List<Location> edges, boolean vertical, boolean closestToAxis, boolean closestToOriginIfEqual) {
        if (vertical) {
            edges.sort(Comparator.comparingDouble(Location::getY));
        }
        else {
            edges.sort(Comparator.comparingDouble(Location::getX));
        }


        Location first = closestToAxis ? edges.get(0) : edges.get(3);
        Location second = closestToAxis ? edges.get(1) : edges.get(2);

        boolean isFirstClosestToOrigin = vertical ? first.getX() < second.getX() : first.getY() < second.getY();
        boolean firstEqualsSecond = vertical ? first.getY() == second.getY() : first.getX() == second.getX();

        if (firstEqualsSecond) {
            if (closestToOriginIfEqual) {
                return (isFirstClosestToOrigin) ? first : second;
            } else {
                return (isFirstClosestToOrigin) ? second : first;
            }
        }

        return first;
    }
}