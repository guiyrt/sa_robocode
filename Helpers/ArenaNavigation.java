package sa_robocode.Helpers;

import sa_robocode.Communication.TeammateInfo;
import sa_robocode.robots.RobotType;

import java.awt.geom.Path2D;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaNavigation {
    public static final Double MIN_DISTANCE_TO_WALLS = 95.0;
    private static final Double MIN_DISTANCE_TO_TEAMMATE = 80.0;

    private final Map<String, Location> teammatesTracking;
    private final Map<String, TeammateInfo> teamStatus;
    private final double arenaWidth;
    private final double arenaHeight;
    private Zone zone;

    private enum Zone {
        QUADRANT_0, // BOTTOM LEFT
        QUADRANT_1, // BOTTOM RIGHT
        QUADRANT_2, // TOP RIGHT
        QUADRANT_3, // TOP LEFT
        FULL_ARENA
    }

    public ArenaNavigation(Map<String, Location> teammatesTracking, Map<String, TeammateInfo> teamStatus, double arenaWidth, double arenaHeight, String name) {
        this.teammatesTracking = teammatesTracking;
        this.teamStatus = teamStatus;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.zone = getZoneFromName(name);
    }

    public void updateZone(String deadTeammate) {
        boolean anyCaptains = teamStatus.values().stream().anyMatch(a -> a.getRobotType() == RobotType.CAPTAIN);

        if (anyCaptains) {
            if (zone == Zone.FULL_ARENA) {
                zone = getZoneFromName(deadTeammate);
            }
        }

        else {
            zone = Zone.FULL_ARENA;
        }
    }

    public Zone getZoneFromName(String name) {
        int number = Integer.parseInt(String.valueOf(name.charAt(name.length()-2)));
        return Zone.values()[number % 5];
    }

    public Location tooCloseToTeammate(Location robot) {
        for (Location teammate: teammatesTracking.values()) {
            if (teammate.distanceTo(robot) < MIN_DISTANCE_TO_TEAMMATE) {
                return teammate;
            }
        }

        return null;
    }

    public boolean zoneIsFullArena() {
        return zone == Zone.FULL_ARENA;
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

    public List<Location> getWallsOrderedByDistance(Location location, boolean zoneAdjusted) {
        Location adjustedLocation = zoneAdjusted ? adjustLocToZone(location) : location;
        int adjustWalls = zoneAdjusted && (zone != Zone.FULL_ARENA) ? 2 : 1;

        List<Location> walls = new ArrayList<>();
        walls.add(new Location(adjustedLocation.getX(), arenaHeight/adjustWalls));
        walls.add(new Location(adjustedLocation.getX(), 0.0));
        walls.add(new Location(0.0, adjustedLocation.getY()));
        walls.add(new Location(arenaWidth/adjustWalls, adjustedLocation.getY()));
        walls.sort(Comparator.comparingDouble(o -> o.distanceTo(adjustedLocation)));

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
        List<Location> walls = getWallsOrderedByDistance(robot, true);

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