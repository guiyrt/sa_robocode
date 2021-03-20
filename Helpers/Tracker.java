package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Tracker implements Serializable {
    private static final int DUCK_THRESHOLD = 5;
    private static final int CRAB_THRESHOLD = 10;
    private static final int SHARK_THRESHOLD = 6;

    private final List<ScanInfo> pings;
    private final String name;
    private TrackerType trackerType;
    public Circle circle;
    private Line line;
    private Location stopped;

    public Tracker(String name) {
        this.pings = new ArrayList<>();
        this.name = name;
        this.trackerType = TrackerType.NO_PATTERN;
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

    public boolean noPings() {
        return pings.stream().findFirst().isEmpty();
    }

    public void resetPatterns() {
        trackerType = TrackerType.NO_PATTERN;
        this.line = null;
        this.stopped = null;
        this.circle = null;
    }

    public void findPatterns() {
        resetPatterns();

        Location duck = PatternFinder.patternSittingDuck(pings, DUCK_THRESHOLD);
        Line crab = PatternFinder.patternCrab(pings, CRAB_THRESHOLD);
        Circle shark = PatternFinder.patternShark(pings, SHARK_THRESHOLD);

        if (duck != null) {
            trackerType = TrackerType.DUCK;
            stopped = duck;
        }
        else if (crab != null) {
            trackerType = TrackerType.CRAB;
            line = crab;
        }

        else if (shark != null) {
            trackerType = TrackerType.SHARK;
            circle = shark;
        }
    }

    public double getHeading(long tick) {
        double heading = 0.0;

        switch (trackerType) {
            case DUCK, NO_PATTERN -> {
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

            // Linear projection
            case NO_PATTERN -> {
                future = ArenaCalculations.angleToUnitVector(lastKnown.getScannedRobotEvent().getHeading()).
                        setLength(lastKnown.getScannedRobotEvent().getVelocity() * (tick - lastKnown.getScannedRobotEvent().getTime())).
                        apply(lastKnown.getLocation());
            }
        }

        return future;
    }
}
