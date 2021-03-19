package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Circle implements Serializable {
    private static final double TOLERANCE = Math.pow(10, -1);

    private final Location center;
    private final double radius;

    public Circle(Location l1,  Location l2, Location l3) {
        double x12 = l1.getX() - l2.getX();
        double x13 = l1.getX() - l3.getX();
        double y12 = l1.getY() - l2.getY();
        double y13 = l1.getY() - l3.getY();
        double y31 = l3.getY() - l1.getY();
        double y21 = l2.getY() - l1.getY();
        double x31 = l3.getX() - l1.getX();
        double x21 = l2.getX() - l1.getX();

        double sx13 = (Math.pow(l1.getX(), 2) -	Math.pow(l3.getX(), 2));
        double sy13 = (Math.pow(l1.getY(), 2) - Math.pow(l3.getY(), 2));
        double sx21 = (Math.pow(l2.getX(), 2) -	Math.pow(l1.getX(), 2));
        double sy21 = (Math.pow(l2.getY(), 2) -	Math.pow(l1.getY(), 2));

        double f = ((sx13) * (x12) + (sy13) * (x12)	+ (sx21) * (x13) + (sy21) * (x13)) / (2 * ((y31) * (x12) - (y21) * (x13)));
        double g = ((sx13) * (y12) + (sy13) * (y12)	+ (sx21) * (y13) + (sy21) * (y13)) / (2 * ((x31) * (y12) - (x21) * (y13)));
        double c = -Math.pow(l1.getX(), 2) - Math.pow(l1.getY(), 2) - 2 * g * l1.getX() - 2 * f * l1.getY();

        this.radius = Math.sqrt(-g * -g + -f * -f - c);
        this.center = new Location(-g, -f);
    }

    public Circle(Location center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    public Location getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public boolean isLocationInCircle(Location location) {
        return (location.distanceTo(getCenter()) - getRadius()) < TOLERANCE;
    }

    public double circumferenceLocationToAngle(Location location) {
        Location locationInPolar = new Vector(getCenter(), location).apply(new Location(0.0, 0.0));
        return Math.toDegrees(Math.atan2(locationInPolar.getY(), locationInPolar.getX()));
    }

    public double getVelocityOrientation(Location lastKnown, Location secondLastKnown, double velocity) {
        return (secondLastKnown.getY() - getCenter().getY()) * (lastKnown.getX() - secondLastKnown.getX()) -
               (secondLastKnown.getX() - getCenter().getX()) * (lastKnown.getY() - secondLastKnown.getY()) > 0 ?
               Math.abs(velocity) :
               -Math.abs(velocity);
    }

    public Location getLocationByTick(ScanInfo lastScan, long tick, double velocity) {
        Location lastLocation = lastScan.getLocation();
        long ticksToPredict = tick - lastScan.getScannedRobotEvent().getTime();
        double currentAngle = circumferenceLocationToAngle(lastLocation);

        // Distance divided by radius is the angle travelled in radians
        double angleDelta = Math.toDegrees(velocity/getRadius());

        while (ticksToPredict != 0) {
            currentAngle += angleDelta;
            ticksToPredict--;
        }

        return ArenaCalculations.polarInfoToLocation(getCenter(), currentAngle, getRadius());
    }
}