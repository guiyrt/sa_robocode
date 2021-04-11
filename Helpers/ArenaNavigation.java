package sa_robocode.Helpers;

import java.awt.geom.Path2D;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaNavigation {
    private static final Double MIN_DISTANCE_TO_WALLS = 95.0;

    private final Map<String, Tracker> enemiesTracking;
    private final Map<String, Location> teammatesTracking;
    private final double arenaWidth;
    private final double arenaHeight;
    private final Zone zone;

    private enum Zone {
        QUADRANT_0, // BOTTOM LEFT
        QUADRANT_1, // BOTTOM RIGHT
        QUADRANT_2, // TOP RIGHT
        QUADRANT_3, // TOP LEFT
        FULL_ARENA
    }

    public ArenaNavigation(Map<String, Tracker> enemiesTracking, Map<String, Location> teammatesTracking, double arenaWidth, double arenaHeight, int number) {
        this.enemiesTracking = enemiesTracking;
        this.teammatesTracking = teammatesTracking;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.zone = Zone.values()[number % 5];
    }

    public Location adjustLocToZone(Location original) {
        Location adjusted = original.clone();

        switch (this.zone) {
            case QUADRANT_1 -> adjusted = new Location(original.getX() - arenaWidth/2, original.getY());
            case QUADRANT_2 -> adjusted = new Location(original.getX() - arenaWidth/2, original.getY() - arenaHeight/2);
            case QUADRANT_3 -> adjusted = new Location(original.getX(), original.getY() - arenaHeight/2);
        }

        return adjusted;
    }

    public List<Location> getWalls(Location location, boolean zoneAdjusted) {
        Location adjustedLocation = zoneAdjusted ? adjustLocToZone(location) : location;
        int adjustWalls = zoneAdjusted && (zone != Zone.FULL_ARENA) ? 2 : 1;

        List<Location> walls = new ArrayList<>();
        walls.add(new Location(adjustedLocation.getX(), arenaHeight/adjustWalls));
        walls.add(new Location(adjustedLocation.getX(), 0.0));
        walls.add(new Location(0.0, adjustedLocation.getY()));
        walls.add(new Location(arenaWidth/adjustWalls, adjustedLocation.getY()));

        return walls;
    }

    public Location getCenterOfZone() {
        Location zoneCenter = new Location(arenaWidth/2, arenaHeight/2);

        switch (zone) {
            case QUADRANT_0 -> zoneCenter = new Location(arenaWidth/4, arenaHeight/4);
            case QUADRANT_1 -> zoneCenter = new Location(3*arenaWidth/4, arenaHeight/4);
            case QUADRANT_2 -> zoneCenter = new Location(3*arenaWidth/4, 3*arenaHeight/4);
            case QUADRANT_3 -> zoneCenter = new Location(arenaWidth/4, 3*arenaHeight/4);
        }

        return zoneCenter;
    }

    public boolean isInsideOfZone(Location location) {
        if (zone == Zone.FULL_ARENA) return true;

        Location adjustedLocation = adjustLocToZone(location);

        return (adjustedLocation.getX() > 0) && (adjustedLocation.getX() < arenaWidth/2)
                && (adjustedLocation.getY() > 0) && (adjustedLocation.getY() < arenaHeight/2);
    }


    public Double getTargetHeading(Location robot) {
        if (!isInsideOfZone(robot)) {
            return null;
        }

        Location adjustedRobot = adjustLocToZone(robot);
        List<Location> walls = getWalls(robot, true);
        walls.sort(Comparator.comparingDouble(o -> o.distanceTo(adjustedRobot)));

        if (walls.get(0).distanceTo(adjustedRobot) < MIN_DISTANCE_TO_WALLS) {
            if (walls.get(1).distanceTo(adjustedRobot) < MIN_DISTANCE_TO_WALLS * 1.4) {
                Vector generalDirection = new Vector(walls.get(0), adjustedRobot).add(new Vector(walls.get(1), adjustedRobot));
                return ThreadLocalRandom.current().nextDouble(-45, 45) + generalDirection.arenaAngleOfVector();
            }

            else {
                Vector generalDirection = new Vector(walls.get(0), adjustedRobot);
                return ThreadLocalRandom.current().nextDouble(-90, 90) + generalDirection.arenaAngleOfVector();

            }
        }

        return null;
    }

    public boolean isHeadingTowardsZone(Location robot, double heading) {
        Location centerOfZone = getCenterOfZone();
        double offsetToWallX = arenaWidth/4;
        double offsetToWallY = arenaHeight/4;
        Path2D zoneLimits = new Path2D.Double();

        Location topRightCorner = new Location(centerOfZone.getX() + offsetToWallX, centerOfZone.getY() + offsetToWallY);
        Location topLeftCorner = new Location(centerOfZone.getX() - offsetToWallX, centerOfZone.getY() + offsetToWallY);
        Location bottomRightCorner = new Location(centerOfZone.getX() + offsetToWallX, centerOfZone.getY() - offsetToWallY);
        Location bottomLeftCorner = new Location(centerOfZone.getX() - offsetToWallX, centerOfZone.getY() - offsetToWallY);

        zoneLimits.moveTo(topRightCorner.getX(), topRightCorner.getY());
        zoneLimits.lineTo(topLeftCorner.getX(), topLeftCorner.getY());
        zoneLimits.lineTo(bottomLeftCorner.getX(), bottomLeftCorner.getY());
        zoneLimits.lineTo(bottomRightCorner.getX(), bottomRightCorner.getY());
        zoneLimits.closePath();

        Vector robotVector = ArenaCalculations.angleToUnitVector(heading).setLength(5);

        while(ArenaCalculations.isInsideArena(robot, arenaWidth, arenaHeight)) {
            if (zoneLimits.contains(robot.getX(), robot.getY())) {
                return true;
            }
            robot = robotVector.apply(robot);
        }

        return false;
    }
}