package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

public class Line {
    private static final double TOLERANCE = Math.pow(10, -5);
    private static final Location ORIGIN = new Location(0.0, 0.0);

    private final double slope;
    private final double intercept;
    private Location start;
    private Location end;

    public Line(Location l1, Location l2) {
        this.slope  = (l1.getY() - l2.getY()) / (l1.getX() - l2.getX());
        this.intercept = (l1.getY()) - (this.slope*l1.getX());

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

    public void setStart(Location start) {
        this.start = start;
    }

    public void setEnd(Location end) {
        this.end = end;
    }

    public Vector getLineVector() {
        return new Vector(getStart(), getEnd());
    }

    public boolean isLocationInLine(Location location) {
        boolean locationInLine = Math.abs(location.getY() - ((getSlope()*location.getX()) + getIntercept())) < TOLERANCE;

        if (locationInLine) {
            if (new Vector(location, getEnd()).length() > getLineVector().length()) {
                setStart(location);
            }

            else if (new Vector(getStart(), location).length() > getLineVector().length()) {
                setEnd(location);
            }

            return true;
        }

        return false;
    }

    public static double getVelocityOrientation(Location lastKnown, Location secondLastKnown, double velocity) {
        return lastKnown.distanceTo(ORIGIN) > secondLastKnown.distanceTo(ORIGIN) ? Math.abs(velocity) : -Math.abs(velocity);
    }

    public Location getLocationByTick(ScanInfo lastScan, long tick, double velocity) {
        Location lastLocation = lastScan.getLocation();
        Vector lineVector = new Vector(getStart(), getEnd()).setLength(velocity);
        long ticksToPredict = tick - lastScan.getScannedRobotEvent().getTime();

        while(ticksToPredict != 0) {
            lastLocation = lineVector.apply(lastLocation);
            ticksToPredict--;

            if (!(getLineVector().angleWithVector(new Vector(getStart(), lastLocation)) == 0)) {
                Vector correction = new Vector(lastLocation, getStart());
                lastLocation = correction.apply(getStart());
                lineVector = lineVector.negative();
            }

            else if(!(getLineVector().angleWithVector(new Vector(lastLocation, getEnd())) == 0)) {
                Vector correction = new Vector(lastLocation, getEnd());
                lastLocation = correction.apply(getEnd());
                lineVector = lineVector.negative();
            }
        }

        return lastLocation;
    }
}