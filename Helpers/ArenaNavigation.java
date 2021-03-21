package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.util.*;

public class ArenaNavigation {
    private static final double DISTANCE_FROM_TEAMMATES = 60.0;
    private static final double DISTANCE_FROM_ENEMIES = 60.0;
    private static final double DISTANCE_FROM_ORIGINAL_LOCATION = 100.0;
    private static final double DISTANCE_FROM_WALLS = 100.0;
    private static final int MAX_ATTEMPTS = 200;
    private static final double VERIFICATION_STEP = 2.0;
    private static final double DISTANCE_FROM_CENTER = 100.0;

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


    public boolean legalLocation(Location location, Location current, boolean ignoreSelf) {
        // Check clearance from walls
        boolean walls = getWalls(location).stream()
                .map(l -> l.distanceTo(location) >= DISTANCE_FROM_WALLS)
                .reduce(true, (a, b) -> a && b);

        // Check clearance from enemies
        boolean enemies = enemiesTracking.values().stream()
                .map(tracker -> tracker.getLastKnownLocation().distanceTo(location) >= DISTANCE_FROM_ENEMIES)
                .reduce(true, (a, b) -> a && b);

        // Check clearance from teammates
        boolean team = teammatesTracking.values().stream()
                .map(l -> l.distanceTo(location) >= DISTANCE_FROM_TEAMMATES)
                .reduce(true, (a, b) -> a && b);

        boolean center = current.distanceTo(centerOfArena()) > DISTANCE_FROM_CENTER;

        boolean obstaclesClearance = walls && enemies && team && center;

        return ignoreSelf ? obstaclesClearance : obstaclesClearance && (current.distanceTo(location) >= DISTANCE_FROM_ORIGINAL_LOCATION);
    }

    public Location generateRandomLocation() {
        Random random = new Random();
        return new Location(random.nextDouble() * arenaWidth, random.nextDouble() * arenaHeight);
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
        Location selected = generateRandomLocation();
        boolean bingo = false;
        int leastInfractions = Integer.MAX_VALUE;

        for (int i=0; i<MAX_ATTEMPTS; i++) {
            Location random = generateRandomLocation();
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
            System.out.println("COULD NOT FIND!");
        }
        // TODO: may not find random option

        return selected;
    }
}
