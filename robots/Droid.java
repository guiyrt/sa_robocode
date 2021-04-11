package sa_robocode.robots;

import sa_robocode.Communication.*;
import sa_robocode.Helpers.*;
import robocode.*;
import sa_robocode.Helpers.Vector;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Droid - a robot by Group 9
 */
public class Droid extends TeamRobot implements robocode.Droid {
    /**
     * Robot defining static attributes
     */
    private static final RobotType robotType = RobotType.DROID;
    private static final List<MotionType> PRIORITY_MOTIONS = Arrays.asList(MotionType.AVOIDING_BULLET, MotionType.HIT_WALL, MotionType.AVOIDING_TEAMMATE);

    /**
     * Definition of colors to paint robots
     */
    private static final Color ARMY_GREEN = Color.decode("#FFFFFF");
    private static final Color ARMY_DARK_GREEN = Color.decode("#FFFFFF");
    private static final Color RADAR_RED = Color.decode("#CC0000");
    private static final Color COPPER_BULLET = Color.decode("#B87333");
    private static final Color BEAM_BLUE = Color.decode("#ADD8E6");

    /**
     * Definition of data structures to aid determination of robot behavior
     */
    private final Map<String, Tracker> enemiesTracking = new HashMap<>();
    private final Map<String, Boolean> enemyDroids = new HashMap<>();
    private final Map<String, Location> teammatesTracking = new HashMap<>();
    private final Map<String, TeammateInfo> teamStatus = new HashMap<>();
    private final List<BulletInfo> teamBullets = new ArrayList<>();
    private final List<BulletInfo> avoidedBullets = new ArrayList<>();
    private final Set<Tracker> mostWanted = new LinkedHashSet<>();
    private ArenaNavigation gps = null;
    private MotionType motion = MotionType.READY_TO_MOVE;
    private String currentLeader = null;
    private boolean outOfDateBounties = true;
    private Tracker bounty = null;
    private Location target = null;
    private boolean readyToFire = false;
    private double lastHeading = 0;
    private double lastVelocity = 0;
    private double targetHeading = 0;
    private long ticksInStraightLine = 0;

    /**
     * Definition of useful static values to access in methods
     */
    private static final int BULLET_ITERATIONS_PREVISION = 9;
    private static final double AVOID_OBSTACLE_DISTANCE = 70.0;
    private static final double TEAMMATE_MIN_DISTANCE_TO_FIRE = 215.0;
    private static final double MAX_POWER_RADIUS = 120.0;
    private static final double MIN_BULLET_POWER = 0.1;
    private static final double BULLET_RANGE_DROPOFF = 50.0;
    private static final double BULLET_POWER_DROPOFF = 0.20;
    private static final int MAX_SIMULATION_ITERATIONS_ENEMY = 100;
    private static final double ROBOT_EDGES_DISTANCE_TOLERANCE = 6.0;
    private static final double CRITICAL_ENERGY_LEVEL = 50.0;
    private static final double MAX_ALLOWED_VELOCITY = 7.0;
    private static final double TURN_ALLOWED_VELOCITY = 5.0;
    private static final long MAX_TICKS_IN_STRAIGHT_LINE = 10;


    /**
     * Main method with robot behavior.
     */
    public void run() {
        setMaxVelocity(MAX_ALLOWED_VELOCITY);
        gps = new ArenaNavigation(teammatesTracking, teamStatus, getBattleFieldWidth(), getBattleFieldHeight(), getName());
        setColors(ARMY_GREEN, ARMY_DARK_GREEN, RADAR_RED, COPPER_BULLET, BEAM_BLUE); // Set tank colors
        updateRobotStatus(new TeammateInfo(getName(), robotType, getEnergy()), MessageType.TEAMMATE_REGISTER); // Register in team
        lastHeading = getHeading();
        lastVelocity = getVelocity();
        currentLeader = null;
    }

    /**
     * Override onPaint method to paint in the battlefield
     * @param g2d Graphics2D instance from robocode instance
     */
    public void onPaint(Graphics2D g2d) {
        System.out.println(motion);

        if (gps != null) {
            Painter.drawLocation(g2d, Color.green, gps.adjustLocToZone(getCurrentLocation()));

            for (Location loc : gps.getWallsOrderedByDistance(getCurrentLocation(), true)) {
                Painter.drawLocation(g2d, Color.ORANGE, loc);

                Vector danger = new Vector(loc, gps.adjustLocToZone(getCurrentLocation())).setLength(95.0);
                Painter.drawLocation(g2d, Color.red, danger.apply(loc));
            }
        }
    }

    public boolean hasEnemyRadar(String name) {
        return enemyDroids.containsKey(name) && !enemyDroids.get(name);
    }

    public boolean hasCriticalEnergyLevel(Tracker t) {
        return  t.getLastKnownEnergy() <= CRITICAL_ENERGY_LEVEL;
    }

    public void prepareToMostWanted(List<Tracker> list) {
        List<Tracker> hasRadar = list.stream().filter(tracker -> hasEnemyRadar(tracker.getName())).collect(Collectors.toList());
        List<Tracker> criticalDamaged = list.stream().filter(this::hasCriticalEnergyLevel).collect(Collectors.toList());

        // First add critical damaged opponents
        mostWanted.addAll(criticalDamaged);
        // Then those with radar
        mostWanted.addAll(hasRadar);
        // And at last, the rest
        mostWanted.addAll(list);
    }

    public void orderBounties() {
        // Clear previous most wanted
        mostWanted.clear();

        // Try to find patterns for each enemy
        enemiesTracking.values().forEach(Tracker::findPatterns);

        // First priority, stopped robots
        List<Tracker> ducks = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.DUCK)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        prepareToMostWanted(ducks);

        // Second priority, robots moving in circles
        List<Tracker> sharks = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.SHARK)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        prepareToMostWanted(sharks);

        // Third priority, robots moving in a straight line
        List<Tracker> crabs = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.CRAB)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        prepareToMostWanted(crabs);

        // Fourth priority, robots with consecutive data points (data without much information gaps)
        List<Tracker> projections = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.PROJECTION)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        prepareToMostWanted(projections);

        // Fifth priority, robot energy left
        List<Tracker> weaker = enemiesTracking.values().stream().filter(tracker -> !mostWanted.contains(tracker))
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        prepareToMostWanted(weaker);

        // Bounties are now updated
        outOfDateBounties = false;
    }

    /**
     * Creates new Location instance with current robot location
     * @return Current robot coordinates wrapped in Location
     */
    public Location getCurrentLocation() {
        return new Location(getX(), getY());
    }

    public void updateRobotStatus(TeammateInfo ti, MessageType messageType) {
        sendMessageToTeam(new Message(ti, messageType));
        teamStatus.put(getName(), ti);
    }

    public boolean isRegisteredTeammate(String name) {
        return teamStatus.values().stream().map(TeammateInfo::getName).anyMatch(teammate -> teammate.equals(name));
    }

    /**
     * Fires a bullet and broadcasts information regarding bullet trajectory
     * @param power Power of bullet
     */
    public void fireAndBroadcast(double power) {
        if (getGunHeat() == 0.0) {
            Long fireTick = getTime();
            BulletInfo bulletInfo = new BulletInfo(fireBullet(power), fireTick, new Location(getX(), getY()), getBattleFieldWidth(), getBattleFieldHeight());
            sendMessageToTeam(new Message(bulletInfo));
        }
    }

    /**
     * Processes the scanned robot information, depending if is teammate or not
     * @param si ScanInfo regarding the scanned robot
     */
    public void processScanInfo(ScanInfo si) {
        // Most wanted needs to be recalculated
        outOfDateBounties = true;

        String name = si.getScannedRobotEvent().getName();

        // Check if enemy was already detected before
        if (!enemiesTracking.containsKey(name)) {
            enemiesTracking.put(name, new Tracker(name));

            // Check if first scan of enemy has over 100 energy points, because droids have 120
            enemyDroids.put(name, si.getScannedRobotEvent().getEnergy() > 100);
        }

        // Add last tracked location to head of list
        enemiesTracking.get(name).addPing(si);
    }

    public void checkHierarchy() {
        // Sort team elements by name first, so that every robot has the same starting order
        Set<TeammateInfo> nameSorted = teamStatus.values().stream()
                .sorted(Comparator.comparing(TeammateInfo::getName))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Sort team elements by last known energy level
        Set<TeammateInfo> energySorted = nameSorted.stream()
                .sorted(Comparator.comparingDouble(TeammateInfo::getEnergy).reversed())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Compare leadership abilities iterating through the set
        int bestLeaderScore = TeammateInfo.NOT_SUITABLE_FOR_LEADERSHIP;
        String leader = null;

        for (TeammateInfo ti: energySorted) {
            String name = ti.getName();
            int leaderPriority = ti.getLeaderPriority();

            // Compare with current leader
            if (leaderPriority > bestLeaderScore) {
                bestLeaderScore = leaderPriority;
                leader = name;
            }
        }

        currentLeader = leader;
    }

    public boolean amCurrentLeader() {
        return getName().equals(currentLeader);
    }

    public boolean teammatesBetweenLocation(Location location) {
        for(Location teammate: teammatesTracking.values()) {
            Location currentLocation = getCurrentLocation();
            Location bullet = getCurrentLocation();
            Vector bulletVector = new Vector(getCurrentLocation(), location).setLength(Rules.getBulletSpeed(0.1));

            // Check if teammate is closer than allowed to shoot
            if (currentLocation.distanceTo(teammate) >= TEAMMATE_MIN_DISTANCE_TO_FIRE) {
                continue;
            }

            // Check if bullet collided with teammate until bullet passes target location
            while(new Vector(bullet, location).angleWithVector(bulletVector) != 180) {
                bullet = bulletVector.apply(bullet);
                if (ArenaCalculations.isLocationInsideRobot(teammate, 0, bullet, ROBOT_EDGES_DISTANCE_TOLERANCE)) {
                    return true;
                }
            }
        }

        return false;
    }

    public double calculateBulletPower(Location robot, Location target) {
        double distance = robot.distanceTo(target);
        return distance <= MAX_POWER_RADIUS ?
                Rules.MAX_BULLET_POWER :
                Math.max(Rules.MAX_BULLET_POWER - (((distance - MAX_POWER_RADIUS) / BULLET_RANGE_DROPOFF) * BULLET_POWER_DROPOFF), MIN_BULLET_POWER);
    }

    public void setBounty(Set<Tracker> mostWanted) {
        for (Tracker tracker: mostWanted) {
            if (tracker.noPings() || teammatesBetweenLocation(tracker.getLocationByTick(getTime()))) {
                continue;
            }

            bounty = tracker;
            break;
        }
    }

    /**
     * Broadcasts message to entire team
     * @param msg Instance of Message to send to all teammates
     */
    public void sendMessageToTeam(Message msg) {
        try {
            broadcastMessage(msg);
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Sends message to specific teammate
     * @param teammate Teammate name
     * @param msg Instance of Message to send
     */
    public void sendMessageToTeammate(String teammate, Message msg) {
        try {
            sendMessage(teammate, msg);
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Override onMessageReceived to handle received messages
     * @param me MessageEvent instance
     */
    public void onMessageReceived(MessageEvent me) {
        Message message = (Message) me.getMessage();

        switch (message.getMessageType()) {
            case STATUS_INFO -> {
                TeammateInfo ti = message.getTeammateInfo();
                teamStatus.put(ti.getName(), ti);
                checkHierarchy();
            }

            case SCAN_INFO -> {
                ScanInfo si = message.getScanInfo();
                processScanInfo(si);
            }

            case BULLET_INFO -> {
                BulletInfo bi = message.getBulletInfo();
                teamBullets.add(bi);
            }

            // Only leader receives these messages
            case GUN_READY_INFO -> {

                if (amCurrentLeader()) {
                    // Check if kill order needs to be recalculated
                    if (outOfDateBounties) {
                        orderBounties();
                    }

                    sendMessageToTeammate(me.getSender(), new Message(mostWanted));
                }
            }

            case BOUNTIES_INFO -> {
                Set<Tracker> mostWanted = message.getBounties();
                setBounty(mostWanted);
            }

            case LOCATION_UPDATE -> {
                Location teammate = message.getLocation();
                teammatesTracking.put(me.getSender(), teammate);
            }

            case TEAMMATE_REGISTER -> {
                TeammateInfo ti = message.getTeammateInfo();
                teamStatus.put(ti.getName(), ti);
                checkHierarchy();

                // In case teammate was scanned before registration
                enemiesTracking.remove(ti.getName());
            }
        }
    }


    public void goPerpendicularToVectorDirection(Vector target, double heading, Vector preferredDirection) {
        Vector robotVector = ArenaCalculations.angleToUnitVector(heading);
        Vector robotBackVector = robotVector.negative();

        // Check if better to move forward of backwards, choose direction vector closest to bullet vector
        boolean moveForwards = Math.abs(target.closestAngleBetweenVectors(robotVector)) < Math.abs(target.closestAngleBetweenVectors(robotBackVector));
        double distanceToAvoidObstacle = moveForwards ? AVOID_OBSTACLE_DISTANCE : -AVOID_OBSTACLE_DISTANCE;
        Vector generalDirectionVector = moveForwards ? robotVector : robotBackVector;

        // Check closest angle from direction vector to vector perpendicular with bullet vector
        Vector clockwiseVectorToObstacle = target.perpendicularClockwise();
        Vector counterClockwiseVectorToObstacle = target.perpendicularCounterClockwise();

        double angleOptionOne = Math.abs(ArenaCalculations.shortestAngle(generalDirectionVector.closestAngleBetweenVectors(clockwiseVectorToObstacle)));
        double angleOptionTwo = Math.abs(ArenaCalculations.shortestAngle(generalDirectionVector.closestAngleBetweenVectors(counterClockwiseVectorToObstacle)));
        Vector targetDirection = preferredDirection != null ? preferredDirection : (angleOptionOne < angleOptionTwo ? clockwiseVectorToObstacle : counterClockwiseVectorToObstacle);

        // If robot is intended to move backwards, adjust target angle with 180 degrees
        double calculatedAngle = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(heading, targetDirection.arenaAngleOfVector()));
        double angleToAvoidObstacle = moveForwards ? calculatedAngle : ArenaCalculations.shortestAngle(180 + calculatedAngle);

        setAhead(distanceToAvoidObstacle);
        setTurnRight(angleToAvoidObstacle);
    }


    /**
     * Override onHitWall to define behavior when robot hits a wall
     * @param e Resulting HitWallEvent instance
     */
    public void onHitWall(HitWallEvent e) {
        motion = MotionType.HIT_WALL;
        List<Location> walls = gps.getWallsOrderedByDistance(getCurrentLocation(), false);
        goPerpendicularToVectorDirection(new Vector(walls.get(0), getCurrentLocation()), getHeading(), new Vector(walls.get(1), getCurrentLocation()));
    }

    public void onHitRobot(HitRobotEvent e) {
        if (!isRegisteredTeammate(e.getName())) {
            motion = MotionType.ENEMY_COLLISION;

            double enemyAngle = (getHeading() + e.getBearing() + 360) % 360;
            double shootingAngle = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getGunHeading(), enemyAngle));
            setTurnGunRight(ArenaCalculations.shortestAngle(shootingAngle));
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        String name = e.getName();

        // Check if dead robot is teammate
        if(isRegisteredTeammate(name)) {
            teamStatus.remove(name);
            teammatesTracking.remove(name);

            gps.updateZone(name);

            // Might be necessary a new leader election
            if (name.equals(currentLeader)) {
                if (teamStatus.keySet().size() > 1) {
                    updateRobotStatus(new TeammateInfo(getName(), robotType, getEnergy()), MessageType.STATUS_INFO);
                }

                // Out of teammates
                else {
                    checkHierarchy();
                }
            }
        }

        // If is enemy, remove from enemy tracking
        else {
            enemiesTracking.remove(name);
        }
    }

    public boolean simulateGunFire(Tracker tracker, Location target, long currentTick, double headingDiff, double acceleration) {
        Location simulationLocation = getCurrentLocation();
        double distance = simulationLocation.distanceTo(target);
        double power = calculateBulletPower(simulationLocation, target);
        double bulletVelocity = Rules.getBulletSpeed(power);
        double simulatedHeading = getHeading();
        double simulatedGunHeading = getGunHeading();
        double simulatedVelocity = getVelocity();
        long simulationTick = currentTick;
        boolean aiming = true;


        while (aiming) {
            simulatedHeading = (simulatedHeading + headingDiff) % 360;
            simulatedVelocity = acceleration > 0 ? Math.min(simulatedVelocity + acceleration, 8.0) : Math.max(simulatedVelocity + acceleration, 0);

            Location nextLocation = ArenaCalculations.polarInfoToLocation(simulationLocation, simulatedHeading + headingDiff, simulatedVelocity);
            double angleToShoot = ArenaCalculations.angleFromOriginToLocation(nextLocation, target);
            double angleAdjustmentNeeded = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(simulatedGunHeading + headingDiff, angleToShoot));

            if (Math.abs(angleAdjustmentNeeded) > Rules.GUN_TURN_RATE) {
                angleAdjustmentNeeded = angleAdjustmentNeeded > 0 ? Rules.GUN_TURN_RATE : -Rules.GUN_TURN_RATE;
            }

            else {
                aiming = false;
            }

            simulationLocation = nextLocation;
            simulatedGunHeading = (simulatedGunHeading + angleAdjustmentNeeded) % 360;
            simulationTick++;
        }

        simulationTick++; // Simulate fire

        simulationTick += (long) Math.ceil(distance / bulletVelocity);

        return ArenaCalculations.isLocationInsideRobot(tracker.getLocationByTick(simulationTick), tracker.getHeading(simulationTick), target, 0);
    }

    public void cleanGun() {
        bounty = null;
        target = null;
        readyToFire = false;
    }

    public void onStatus(StatusEvent e) {
        double headingDiff = ArenaCalculations.shortestAngle(e.getStatus().getHeading() - lastHeading);
        double acceleration = e.getStatus().getVelocity() - lastVelocity;
        Vector momentumVector = ArenaCalculations.angleToUnitVector(e.getStatus().getHeading());
        ticksInStraightLine = headingDiff == 0 ? ticksInStraightLine + 1 : 0;
        Location currentLocation = getCurrentLocation();

        // Update teammates of current location
        sendMessageToTeam(new Message(currentLocation));

        // Movement calculations
        if (PRIORITY_MOTIONS.contains(motion) && Math.abs(e.getStatus().getDistanceRemaining()) < 10) {
            if (motion == MotionType.HIT_WALL) {
                List<Location> walls = gps.getWallsOrderedByDistance(getCurrentLocation(), false);
                targetHeading = new Vector(walls.get(0), getCurrentLocation()).arenaAngleOfVector();
                motion = MotionType.TURNING;
            }
            else {
                motion = MotionType.MOVING;
            }
        }

        // Handle movement if priority task is not happening
        if (gps != null) {
            Location tooCloseTeammate = gps.tooCloseToTeammate(currentLocation);

            // Normal behavior movement
            if (!PRIORITY_MOTIONS.contains(motion)) {

                // Check if there is collision danger
                if (motion != MotionType.AVOIDING_TEAMMATE && tooCloseTeammate != null && (!gps.isInsideOfZone(currentLocation) || gps.zoneIsFullArena())) {
                    motion = MotionType.AVOIDING_TEAMMATE;
                    goPerpendicularToVectorDirection(new Vector(tooCloseTeammate, currentLocation), e.getStatus().getHeading(), null);
                }

                // Verify if collided with enemy and ready to fire
                if (motion == MotionType.ENEMY_COLLISION && e.getStatus().getGunTurnRemaining() == 0) {
                    fireAndBroadcast(Rules.MAX_BULLET_POWER);
                    motion = MotionType.READY_TO_MOVE;
                }

                // If is going in straight line for too long
                if (ticksInStraightLine > MAX_TICKS_IN_STRAIGHT_LINE && gps.isInsideOfZone(currentLocation)) {
                    motion = MotionType.RANDOM_DIVERSION;
                    ticksInStraightLine = 0;

                    // Change heading
                    targetHeading = (e.getStatus().getHeading() + 90 * (ThreadLocalRandom.current().nextDouble() > 0.5 ? 1 : -1)) % 360;
                }

                // Ready to start motion
                if (motion == MotionType.READY_TO_MOVE) {
                    if (gps.isInsideOfZone(currentLocation)) {
                        motion = MotionType.MOVING;
                        setAhead(50);
                    } else {
                        motion = MotionType.TURNING;
                        Location centerOfZone = gps.getCenterOfZone();
                        targetHeading = ArenaCalculations.angleFromOriginToLocation(currentLocation, centerOfZone);
                    }
                }

                // Check if is close to walls
                if (motion != MotionType.TURNING) {
                    Double newHeading = gps.getTargetHeading(currentLocation);
                    if (Objects.nonNull(newHeading)) {
                        motion = MotionType.TURNING;
                        targetHeading = newHeading;
                    }
                }

                // Just keep going until other action is triggered
                if (motion == MotionType.MOVING) {
                    // Check if inside zone or moving towards it
                    if (!gps.isInsideOfZone(currentLocation) && !gps.isHeadingTowardsZone(currentLocation, e.getStatus().getHeading())) {
                        motion = MotionType.TURNING;
                        Location centerOfZone = gps.getCenterOfZone();
                        targetHeading = ArenaCalculations.angleFromOriginToLocation(currentLocation, centerOfZone);
                    }
                    setAhead(50);
                }

                // Handle turning to target heading
                if (motion == MotionType.TURNING || motion == MotionType.RANDOM_DIVERSION) {
                    setMaxVelocity(TURN_ALLOWED_VELOCITY);
                    double nextVelocity = acceleration > 0 ? Math.min(e.getStatus().getVelocity() + acceleration, Rules.MAX_VELOCITY) : Math.max(e.getStatus().getVelocity() + acceleration, 0);
                    double maxTurn = Rules.MAX_TURN_RATE - (0.75 * nextVelocity);

                    double angleDelta = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getHeading(), targetHeading));

                    if (Math.abs(angleDelta) > maxTurn) {
                        angleDelta = angleDelta > 0 ? maxTurn : -maxTurn;
                    } else {
                        motion = MotionType.MOVING;
                        setMaxVelocity(MAX_ALLOWED_VELOCITY);
                    }

                    setTurnRight(angleDelta);
                    setAhead(50);
                }
            }
        }

        // Ready to acquire target
        if (getGunHeat() == 0.0 && bounty == null) {
            if (amCurrentLeader()) {
                if (outOfDateBounties) {
                    orderBounties();
                }

                setBounty(mostWanted);
            }

            else {
                // Inform leader that robot is ready to fire
                sendMessageToTeammate(currentLeader, new Message(MessageType.GUN_READY_INFO));
            }
        }

        // Simulate enemy movement to figure out where to shoot
        if (bounty != null & target == null & (motion != MotionType.AVOIDING_BULLET)) {
            // Running simulations with enemy position prediction, considering this robot's heading variation
            // AKA, considering moving in a curve, variable speed
            for (int i = 1; i < MAX_SIMULATION_ITERATIONS_ENEMY; i++) {
                Location enemy = bounty.getLocationByTick(e.getTime() + i);
                if (simulateGunFire(bounty, enemy, e.getTime(), headingDiff, acceleration)) {
                    target = enemy;
                    break;
                }
            }

            // If no target was found, re-run simulations, this time ignoring this robot's heading variation and acceleration
            // AKA, considering moving in straight line, constant speed
            if (target == null && headingDiff != 0) {
                for (int i = 1; i < MAX_SIMULATION_ITERATIONS_ENEMY; i++) {
                    Location enemy = bounty.getLocationByTick(e.getTime() + i);
                    if (simulateGunFire(bounty, enemy, e.getTime(), 0, 0)) {
                        target = enemy;
                        break;
                    }
                }
            }

            if (target == null) {
                // Get new tracker
                cleanGun();
            }
        }

        if (readyToFire && getGunTurnRemaining() == 0) {
            double power = calculateBulletPower(currentLocation, target);
            fireAndBroadcast(power);
            cleanGun();
        }

        // Start aiming towards target
        if (target != null) {
            Location nextLocation = ArenaCalculations.polarInfoToLocation(currentLocation, ArenaCalculations.convertAngleToPolarOrArena(getHeading() + headingDiff), e.getStatus().getVelocity());
            double angleToShoot = ArenaCalculations.angleFromOriginToLocation(nextLocation, target);
            double angleAdjustmentNeeded = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getGunHeading() + headingDiff, angleToShoot));

            if (Math.abs(angleAdjustmentNeeded) > Rules.GUN_TURN_RATE) {
                angleAdjustmentNeeded =  angleAdjustmentNeeded > 0 ? Rules.GUN_TURN_RATE : -Rules.GUN_TURN_RATE;
            }

            else {
                readyToFire = true;
            }

            setTurnGunRight(angleAdjustmentNeeded);
        }

        // Update last values
        lastHeading = e.getStatus().getHeading();
        lastVelocity = e.getStatus().getVelocity();

        // Check if collision with friendly bullet is imminent
        teamBullets.removeIf(bi -> (bi.bulletIsNull()) || (bi.getBulletLocation(e.getTime()) == null) || (avoidedBullets.contains(bi)));
        avoidedBullets.removeIf(bi -> !teamBullets.contains(bi));

        for (int i=1; i<=BULLET_ITERATIONS_PREVISION; i++) {
            Location nextRobotLocation = momentumVector.setLength(e.getStatus().getVelocity()*i).apply(new Location(e.getStatus().getX(), e.getStatus().getY()));

            for (BulletInfo bi : teamBullets) {
                Location nextBulletLocation = bi.getBulletLocation(e.getTime() + i);
                if (nextBulletLocation != null && ArenaCalculations.isLocationInsideRobot(nextRobotLocation, e.getStatus().getHeading(), nextBulletLocation, ROBOT_EDGES_DISTANCE_TOLERANCE)) {
                    // Robot is in a collision course, calculate in which direction to go
                    motion = MotionType.AVOIDING_BULLET;
                    goPerpendicularToVectorDirection(bi.getBulletVector(), e.getStatus().getHeading(), null);

                    // Once found one possible collision, ignore future iterations
                    avoidedBullets.add(bi);
                    return;
                }
            }
        }
    }
}