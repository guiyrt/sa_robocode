package sa_robocode.Helpers;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaNavigation {
    private static final double DISTANCE_FROM_TEAMMATES = 50.0;
    private static final double DISTANCE_FROM_ENEMIES = 50.0;
    private static final double DISTANCE_FROM_ORIGINAL_LOCATION = 100.0;
    private static final double DISTANCE_FROM_WALLS = 100.0;
    private static final double DISTANCE_FROM_CENTER = 150.0;
    private static final int MAX_ATTEMPTS = 200;
    private static final double VERIFICATION_STEP = 2.0;

    private final Map<String, Tracker> enemiesTracking;
    private final Map<String, Location> teammatesTracking;
    private final double arenaWidth;
    private final double arenaHeight;

    public ArenaNavigation(Map<String, Tracker> enemiesTracking, Map<String, Location> teammatesTracking, double arenaWidth, double arenaHeight) {
        this.enemiesTracking = enemiesTracking;
        this.teammatesTracking = teammatesTracking;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
    }

    public List<Location> getWalls(Location location) {
        List<Location> walls = new ArrayList<>();
        walls.add(new Location(location.getX(), arenaHeight));
        walls.add(new Location(location.getX(), 0.0));
        walls.add(new Location(0.0, location.getY()));
        walls.add(new Location(arenaWidth, location.getY()));

        return walls;
    }

    public Location centerOfArena() {
        return new Location(arenaWidth/2.0, arenaHeight/2.0);
    }

    public double getXMaxAllowedZone(Location location) {
        if (location.getY() > (arenaHeight/2.0)) {
            return Math.max(location.getX(), arenaWidth / 2.0);
        }

        else {
            return arenaWidth;
        }
    }

    public double getXMinAllowedZone(Location location) {
        if (location.getX() > (arenaHeight/2.0)) {
            if (location.getY() > (arenaHeight/2.0)) {
                return 0;
            }
            else {
                return arenaWidth / 2.0;
            }
        }

        else {
            if (location.getY() > (arenaHeight/2.0)) {
                return 0;
            }
            else {
                return location.getX();
            }
        }
    }

    public double getYMaxAllowedZone(Location location) {
        if (location.getX() > (arenaHeight/2.0)) {
            return arenaHeight;
        }

        else {
            return Math.max(location.getY(), (arenaHeight / 2.0));
        }
    }

    public double getYMinAllowedZone(Location location) {
        if (location.getX() > (arenaHeight/2.0)) {
            return Math.min(location.getY(), (arenaHeight / 2.0));
        }

        else {
            return 0;
        }
    }

    public boolean legalLocation(Location destination, Location current, boolean ignoreSelf) {
        Location centerLoc = centerOfArena();
        boolean center = (destination.getX() <= (centerLoc.getX() - DISTANCE_FROM_CENTER) || destination.getX() >= (centerLoc.getX() + DISTANCE_FROM_CENTER))
                && (destination.getY() <= (centerLoc.getY() - DISTANCE_FROM_CENTER) || destination.getY() >= (centerLoc.getY() + DISTANCE_FROM_CENTER));

        // Check clearance from walls
        boolean walls = getWalls(destination).stream()
                .map(l -> l.distanceTo(destination) >= DISTANCE_FROM_WALLS)
                .reduce(true, (a, b) -> a && b);

        // Check clearance from enemies
        boolean enemies = enemiesTracking.values().stream()
                .map(tracker -> tracker.getLastKnownLocation().distanceTo(destination) >= DISTANCE_FROM_ENEMIES)
                .reduce(true, (a, b) -> a && b);

        // Check clearance from teammates
        boolean team = teammatesTracking.values().stream()
                .map(l -> l.distanceTo(destination) >= DISTANCE_FROM_TEAMMATES)
                .reduce(true, (a, b) -> a && b);

        boolean obstaclesClearance = walls && enemies && team && center;

        return ignoreSelf ? obstaclesClearance : obstaclesClearance && (current.distanceTo(destination) >= DISTANCE_FROM_ORIGINAL_LOCATION);
    }

    public boolean tooCloseToWalls(Location current) {
        return getWalls(current).stream().map(wall -> wall.distanceTo(current) <= DISTANCE_FROM_WALLS/2.0).reduce(false, (a,b) -> a || b);
    }


    public Location generateRandomLocation(Location location) {
        return new Location(ThreadLocalRandom.current().nextDouble(getXMinAllowedZone(location), getXMaxAllowedZone(location)),
                ThreadLocalRandom.current().nextDouble(getYMinAllowedZone(location), getYMaxAllowedZone(location)));
    }

    public int verifyPath(Location current, Location target) {
        Vector drive = new Vector(current, target);
        double distance = drive.length();
        int infractions = 0;

        drive.setLength(VERIFICATION_STEP);
        Location simulated = drive.apply(current);

        while (new Vector(current, simulated).length() < distance) {
            if (!legalLocation(simulated, current, true)) {
                infractions++;
            }
        }

        return  infractions;
    }

    public Location getNextDestination(Location current) {
        Location selected = generateRandomLocation(current);
        boolean bingo = false;
        int leastInfractions = Integer.MAX_VALUE;

        for (int i=0; i<MAX_ATTEMPTS; i++) {
            Location random = generateRandomLocation(current);
            boolean legal = legalLocation(random, current, false);

            if (legal) {
                bingo = true;
                int infractions = verifyPath(current, random);

                if (leastInfractions == Integer.MAX_VALUE || infractions < leastInfractions) {
                    selected = random.clone();
                    leastInfractions = infractions;
                }
            }
        }

        if (!bingo) {
            // Go halfway to middle of arena
            selected = new Vector(current, centerOfArena()).scalar(0.5).apply(current);
        }

        return selected;
    }
}
