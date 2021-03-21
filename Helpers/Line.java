package sa_robocode.Helpers;

import robocode.Rules;
import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;

public class Line implements Serializable {
    private static final double TOLERANCE = Math.pow(10, -5);
    private static final Location ORIGIN = new Location(0.0, 0.0);
    private static final double REVERSE_TIME = 180 / Rules.MAX_TURN_RATE;

    private final double slope;
    private final double intercept;
    private final boolean orientationInversion;
    private final double maxVelocity;
    private Location start;
    private Location end;

    public Line(Location l1, Location l2, boolean inversion, double maxVelocity) {
        this.orientationInversion = inversion;
        this.maxVelocity = maxVelocity;

        if (Math.abs(l1.getX() - l2.getX()) < TOLERANCE) {
            this.slope = Double.POSITIVE_INFINITY;
            this.intercept = l1.getX();
        }

        else {
            this.slope = (l1.getY() - l2.getY()) / (l1.getX() - l2.getX());
            this.intercept = (l1.getY()) - (this.slope*l1.getX());
        }

        if (l1.distanceTo(ORIGIN) < l2.distanceTo(ORIGIN)) {
            this.start = l1;
            this.end = l2;
        }

        else {
            this.start = l2;
            this.end = l1;
        }
    }

    public double getSlope() {
        return slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public Location getStart() {
        return start;
    }

    public Location getEnd() {
        return end;
    }

    public boolean isOrientationInversion() {
        return orientationInversion;
    }

    public double getMaxVelocity() {
        return maxVelocity;
    }

    public void setStart(Location start) {
        this.start = start;
    }

    public void setEnd(Location end) {
        this.end = end;
    }

    public Vector getLineVector() {
        return new Vector(getStart(), getEnd());
    }

    public double getBrakingDistance(double velocity) {
        double v = velocity;
        double distance = 0;

        while (v > 0) {
            v = Math.max(v - Rules.DECELERATION, 0);
            distance += v;
        }

        return distance;
    }

    public double getHeading() {
        return new Vector(getStart(), getEnd()).arenaAngleOfVector();
    }

    public boolean isLocationInLine(Location location) {
        return Double.isInfinite(getSlope()) ?
                Math.abs(location.getX() - getStart().getX()) < TOLERANCE :
                Math.abs(location.getY() - ((getSlope()*location.getX()) + getIntercept())) < TOLERANCE;

    }

    public static double getVelocityOrientation(Location lastKnown, Location secondLastKnown, double velocity) {
        return lastKnown.distanceTo(ORIGIN) > secondLastKnown.distanceTo(ORIGIN) ? Math.abs(velocity) : -Math.abs(velocity);
    }

    public Location getLocationByTick(ScanInfo lastScan, long tick, double lastVelocity) {
        Vector drive = getLineVector();
        double direction = lastVelocity > 0 ? 1 : -1;
        Location lastLocation = lastScan.getLocation();
        double velocity = Math.abs(lastVelocity);
        long ticksToPredict = tick - lastScan.getScannedRobotEvent().getTime();

        while(ticksToPredict > 0) {
            Location orientationStop = direction > 0 ? getEnd() : getStart();

            if (lastLocation.sameAs(orientationStop)) {
                velocity = 0;
                direction = direction > 0 ? -1 : 1;

                if (isOrientationInversion()) {
                    ticksToPredict -= REVERSE_TIME;
                }
            }

            else {
                Location projected = drive.setLength(velocity * direction).apply(lastLocation);

                if (orientationStop.distanceTo(projected) > getBrakingDistance(velocity) || new Vector(projected, orientationStop).angleWithVector(new Vector(lastLocation, orientationStop)) > 1) {
                    if (velocity <= Rules.DECELERATION) {
                        velocity = lastLocation.distanceTo(orientationStop);
                    }

                    else {
                        velocity -= Rules.DECELERATION;
                    }
                }

                else {
                    velocity = Math.min(getMaxVelocity(), velocity + Rules.ACCELERATION);
                }

                lastLocation = drive.setLength(velocity).apply(lastLocation);
            }

            ticksToPredict--;
        }

        return lastLocation;
    }
}