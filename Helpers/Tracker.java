package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Tracker implements Serializable {
    private static final int DUCK_THRESHOLD = 3;
    private static final int CRAB_THRESHOLD = 10;
    private static final int SHARK_THRESHOLD = 4;
    private static final int PROJECTION_MAX_MISSING_DATA_POINTS = 4;

    private final List<ScanInfo> pings;
    private final String name;
    private TrackerType trackerType;
    private Circle circle;
    private Line line;
    private Location stopped;
    public Projection projection;

    public Tracker(String name) {
        this.pings = new ArrayList<>();
        this.name = name;
        this.trackerType = TrackerType.PROJECTION;
    }

    public String getName() {
        return name;
    }

    public void addPing(ScanInfo si) {
        // Adds to pings if list is empty, or it the last ping isn't from the same tick
        if (pings.size() == 0 || pings.get(0).getScannedRobotEvent().getTime() != si.getScannedRobotEvent().getTime()) {
            pings.add(0, si);
        }
    }

    public TrackerType getTrackerType() {
        return trackerType;
    }

    public double getLastKnownEnergy() {
        if (pings.size() == 0) return 0;
        return pings.get(0).getScannedRobotEvent().getEnergy();
    }

    public Location getLastKnownLocation() {
        if (pings.size() == 0) return null;
        return pings.get(0).getLocation();
    }

    public boolean noPings() {
        return pings.stream().findFirst().isEmpty();
    }

    public void resetPatterns() {
        this.trackerType = TrackerType.LINEAR;
        this.line = null;
        this.stopped = null;
        this.circle = null;
        this.projection = null;
    }

    public void findPatterns() {
        resetPatterns();

        Location duck = PatternFinder.patternSittingDuck(pings, DUCK_THRESHOLD);
        Line crab = null;//TODO PatternFinder.patternCrab(pings, CRAB_THRESHOLD);
        Circle shark = PatternFinder.patternShark(pings, SHARK_THRESHOLD);
        Projection projection = PatternFinder.patternProjection(pings, PROJECTION_MAX_MISSING_DATA_POINTS);


        if (duck != null) {
            trackerType = TrackerType.DUCK;
            this.stopped = duck;
        }

        else if (crab != null) {
            trackerType = TrackerType.CRAB;
            this.line = crab;
        }

        else if (shark != null) {
            trackerType = TrackerType.SHARK;
            this.circle = shark;
        }

        else if (projection != null){
            trackerType = TrackerType.PROJECTION;
            this.projection = projection;
        }
    }

    public double getHeading(long tick) {
        double heading = 0.0;

        switch (trackerType) {
            case PROJECTION -> {
                heading = projection.getHeading(tick);
            }

            case DUCK, LINEAR -> {
                heading = pings.get(0).getScannedRobotEvent().getHeading();
            }

            case CRAB -> {
                heading = line.getHeading();
            }

            case SHARK -> {
                heading = circle.getHeading(pings.get(0), tick, pings.get(0).getScannedRobotEvent().getVelocity());
            }
        }

        return heading;
    }

    public Location getLocationByTick(long tick) {
        ScanInfo lastKnown = pings.get(0);
        Location future = null;

        // No projections to the past
        if (lastKnown.getScannedRobotEvent().getTime() > tick) {
            return null;
        }

        switch (trackerType) {
            case DUCK -> {
                future = stopped;
            }

            case CRAB -> {
                double orientationSensitiveVelocity =  Line.getVelocityOrientation(pings.get(0).getLocation(), pings.get(1).getLocation(), lastKnown.getScannedRobotEvent().getVelocity());
                future = line.getLocationByTick(lastKnown, tick, orientationSensitiveVelocity);
            }

            case SHARK -> {
                double orientationSensitiveVelocity = circle.getVelocityOrientation(pings.get(0).getLocation(), pings.get(1).getLocation(), lastKnown.getScannedRobotEvent().getVelocity());
                future = circle.getLocationByTick(lastKnown, tick, orientationSensitiveVelocity);
            }

            case PROJECTION -> {
                future = projection.getLocationByTick(tick);
            }

            case LINEAR -> {
                future = ArenaCalculations.angleToUnitVector(lastKnown.getScannedRobotEvent().getHeading()).
                        setLength(lastKnown.getScannedRobotEvent().getVelocity() * (tick - lastKnown.getScannedRobotEvent().getTime())).
                        apply(lastKnown.getLocation());
            }
        }

        return future;
    }
}
