package sa_robocode.Helpers;

import robocode.Rules;
import sa_robocode.Communication.ScanInfo;

import java.io.Serializable;

public class Projection implements Serializable {
    private final double headingDiff;
    private final double heading;
    private final double acceleration;
    private final double velocity;
    private final ScanInfo lastPing;

    public Projection(ScanInfo recent, ScanInfo old) {
        // Adjust to missing data points
        // Optimal situation is consecutive scans, which result in no adjustment
        double adjust = recent.getScannedRobotEvent().getTime() - old.getScannedRobotEvent().getTime();

        this.headingDiff = (ArenaCalculations.shortestAngle(recent.getScannedRobotEvent().getHeading() - old.getScannedRobotEvent().getHeading())) / adjust;
        this.acceleration = (recent.getScannedRobotEvent().getVelocity() - old.getScannedRobotEvent().getVelocity()) / adjust;

        this.heading = recent.getScannedRobotEvent().getHeading();
        this.velocity = recent.getScannedRobotEvent().getVelocity();
        this.lastPing = recent;
    }

    public double getHeading(long tick) {
        long ticksToPredict = tick - this.lastPing.getScannedRobotEvent().getTime();
        return (heading + (ticksToPredict * headingDiff)) % 360;
    }

    public Location getLocationByTick(long tick) {
        long ticksToPredict = tick - this.lastPing.getScannedRobotEvent().getTime();
        Location location = this.lastPing.getLocation();
        double heading = this.heading;
        double velocity = this.velocity;

        while(ticksToPredict != 0) {
            heading = (heading + headingDiff) % 360;
            velocity = acceleration > 0 ? Math.min(velocity + acceleration, Rules.MAX_VELOCITY) : Math.max(velocity + acceleration, 0);
            location = ArenaCalculations.polarInfoToLocation(location, ArenaCalculations.convertAngleToPolarOrArena(heading), velocity);
            ticksToPredict--;
        }

        return location;
    }
}