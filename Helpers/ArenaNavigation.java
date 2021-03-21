package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.util.*;

public class ArenaNavigation {
    private static final double DISTANCE_FROM_TEAMMATES = 100.0;
    private static final double DISTANCE_FROM_ENEMIES = 60.0;
    private static final double DISTANCE_FROM_ORIGINAL_LOCATION = 250.0;
    private static final double DISTANCE_FROM_WALLS = 100.0;
    private static final double MAX_ATTEMPTS = 200;

    private final Map<String, Tracker> enemiesTracking;
    private final Map<String, ScanInfo> teammatesTracking;
    private final double arenaWidth;
    private final double arenaHeight;

    public ArenaNavigation(Map<String, Tracker> enemiesTracking, Map<String, ScanInfo> teammatesTracking, double arenaWidth, double arenaHeight) {
        this.enemiesTracking = enemiesTracking;
        this.teammatesTracking = teammatesTracking;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
    }

    public boolean legalLocation(Location location, Location current) {
        Location northWall = new Location(location.getX(), arenaHeight);
        Location southWall = new Location(location.getX(), 0.0);
        Location eastWall = new Location(arenaWidth, location.getY());
        Location westWall = new Location(0.0, location.getY());

        // Check clearance from walls
        for (Location wall: Arrays.asList(northWall, southWall, eastWall, westWall)) {
            if (wall.distanceTo(location) < DISTANCE_FROM_WALLS) {
                return false;
            }
        }

        // Check clearance from enemies
        boolean enemies = enemiesTracking.values().stream()
                .map(tracker -> tracker.getLastKnownLocation().distanceTo(location) >= DISTANCE_FROM_ENEMIES)
                .reduce(true, (a, b) -> a && b);

        // Check clearance from teammates
        boolean team = teammatesTracking.values().stream()
                .map(si -> si.getLocation().distanceTo(location) >= DISTANCE_FROM_TEAMMATES)
                .reduce(true, (a, b) -> a && b);

        //System.out.println(enemies && team && (current.distanceTo(location) >= DISTANCE_FROM_ORIGINAL_LOCATION));

        return enemies && team && (current.distanceTo(location) >= DISTANCE_FROM_ORIGINAL_LOCATION);
    }

    public Location generateRandomLocation() {
        Random random = new Random();
        return new Location(random.nextDouble() * arenaWidth, random.nextDouble() * arenaHeight);
    }

    public Location getNextDestination(Location current) {
        Location random = generateRandomLocation();
        boolean bingo = false;

        for (int i=0; (i<MAX_ATTEMPTS) && !bingo; i++) {
            bingo = legalLocation(random, current);
            if (!bingo) {
                random = generateRandomLocation();
            }
        }

        if (!bingo) {
            System.out.println("COULD NOT FIND!");
        }
        // TODO: may not find random option

        return random;
    }
}
