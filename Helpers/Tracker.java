package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Tracker implements Serializable {
    private static final int DUCK_THRESHOLD = 3;
    private static final int CRAB_THRESHOLD = 4;
    private static final int SHARK_THRESHOLD = 2;

    private final List<ScanInfo> pings;
    private final String name;
    private TrackerType trackerType;
    private Circle circle;
    private Line line;
    private Location stopped;

    public Tracker(String name) {
        this.pings = new ArrayList<>();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addPing(ScanInfo si) {
        pings.add(si);
    }

    public TrackerType getTrackerType() {
        return trackerType;
    }

    public double getLastKnownEnergy() {
        if (pings.size() == 0) return 0;
        return pings.get(0).getScannedRobotEvent().getEnergy();
    }

    public void findPatterns() {
        // Reset trackerType
        trackerType = null;

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

    public Location getLocationByTick(long tick) {
        ScanInfo lastKnown = pings.get(0);

        // Linear projection (assume no pattern)
        Location future = ArenaCalculations.angleToUnitVector(lastKnown.getScannedRobotEvent().getHeading()).
                setLength(lastKnown.getScannedRobotEvent().getVelocity() * (tick - lastKnown.getScannedRobotEvent().getTime())).
                apply(lastKnown.getLocation());

        // If has any pattern
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
        }

        return future;
    }
}
