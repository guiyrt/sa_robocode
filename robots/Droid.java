package sa_robocode.robots;

import sa_robocode.Communication.*;
import sa_robocode.Helpers.*;
import sa_robocode.Helpers.PatternFinder;
import sa_robocode.Helpers.Circle;
import robocode.*;
import sa_robocode.Helpers.Vector;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Captain - a robot by Group 9
 */
public class Droid extends TeamRobot implements robocode.Droid {
    /**
     * Robot defining static attributes
     */
    private static final RobotType robotType = RobotType.CAPTAIN;

    /**
     * Definition of colors to paint robots
     */
    private static final Color ARMY_GREEN = Color.decode("#3A4119");
    private static final Color ARMY_DARK_GREEN = Color.decode("#2A2E12");
    private static final Color RADAR_RED = Color.decode("#CC0000");
    private static final Color COPPER_BULLET = Color.decode("#B87333");
    private static final Color BEAM_BLUE = Color.decode("#ADD8E6");

    /**
     * Definition of data structures to aid determination of robot behavior
     */
    private final Map<String, Tracker> enemiesTracking = new HashMap<>();
    private final Map<String, ScanInfo> teammatesTracking = new HashMap<>();
    private final Map<String, TeammateInfo> teamStatus = new HashMap<>();
    private final List<BulletInfo> teamBullets = new ArrayList<>();
    private final List<BulletInfo> avoidedBullets = new ArrayList<>();
    private final Set<Tracker> mostWanted = new LinkedHashSet<>();
    private String currentLeader = null;
    private boolean outOfDateBounties = true;
    private Tracker bounty = null;
    private Location target = null;
    private boolean readyToFire = false;

    /**
     * Definition of useful static values to access in methods
     */
    private static final int BULLET_ITERATIONS_PREVISION = 9;
    private static final double AVOID_BULLET_DISTANCE = 70.0;
    private static final double TEAMMATE_MIN_DISTANCE_TO_FIRE = 215.0;
    private static final double MAX_POWER_RADIUS = 120.0;
    private static final double MIN_BULLET_POWER = 0.1;
    private static final double BULLET_RANGE_DROPOFF = 50.0;
    private static final double BULLET_POWER_DROPOFF = 0.20;
    private static final int MAX_SIMULATION_ITERATIONS_ENEMY = 50;
    private static final double ROBOT_EDGES_DISTANCE_TOLERANCE = 6.0;


    /**
     * Main method with robot behavior.
     */
    public void run() {
        setColors(ARMY_GREEN, ARMY_DARK_GREEN, RADAR_RED, COPPER_BULLET, BEAM_BLUE); // Set tank colors
        updateRobotStatus(new TeammateInfo(getName(), robotType, getEnergy())); // Register in team
        currentLeader = getName();
        /*
        if (getName().contains("3")) {
            while (true) {
                setMaxVelocity(6.0);
                setAhead(1000);
                setTurnRight(1000);
                execute();
            }

        }*/
        while (true) {
            //setMaxVelocity(6.0);
            if (noRadars()) {
                Random rand = new Random();
                setAhead(10000);
                checkClerance();
                setTurnRight(30+rand.nextInt(150));
                waitFor(new TurnCompleteCondition(this));
                checkClerance();
                setTurnLeft(30+rand.nextInt(150));
                waitFor(new TurnCompleteCondition(this));
                checkClerance();
                execute();

            }
        }

    }

    public void checkClerance(){
        if(getX() < 50 || getY() <50 || getBattleFieldWidth()-getX() < 50 || getBattleFieldWidth()-getY() <50){
            out.println("quase bati");
            faceTowards(new Location(getBattleFieldWidth()/2,getBattleFieldHeight()/2));
            ahead(90);

        }
    }

    /**
     * Returns true if there are only droids oalive in the team
     */
    public boolean noRadars(){
        boolean answer = true;
        for (String name: teamStatus.keySet()) {
            if(name.contains("Captain")){
                answer = false;
            }
        }
        return answer;
    }

    /**
     * Override onPaint method to paint in the battlefield
     * @param //g2d Graphics2D instance from robocode instance
     */
    /*
    public void onPaint(Graphics2D g2d) {
        if (destination != null) {
            Painter.drawLocation(g2d, Color.red, destination);
        }

        double maxTurn = Rules.MAX_TURN_RATE - (0.75 * getVelocity());
        Vector maxHeadingDiff = ArenaCalculations.angleToUnitVector(getHeading() + maxTurn).setLength(2000);
        Vector heading = ArenaCalculations.angleToUnitVector(getHeading()).setLength(2000);

        Painter.drawLine(g2d, Color.cyan, getCurrentLocation(), heading.apply(getCurrentLocation()));
        Painter.drawLine(g2d, Color.green, getCurrentLocation(), maxHeadingDiff.apply(getCurrentLocation()));

        for (ScanInfo si: teammatesTracking.values()) {
            for (int i=0; i<360; i++) {
                Painter.drawLocation(g2d, Color.MAGENTA, ArenaCalculations.polarInfoToLocation(si.getLocation(), (double) i, 100.0));
            }
        }

        for (Tracker l: enemiesTracking.values()) {
            for (int i=0; i<360; i++) {
                Painter.drawLocation(g2d, Color.PINK, ArenaCalculations.polarInfoToLocation(l.getLastKnownLocation(), (double) i, 60.0));
            }
        }

        for (int i=0; i<360; i++) {
            Painter.drawLocation(g2d, Color.yellow, ArenaCalculations.polarInfoToLocation(getCurrentLocation(), (double) i, 250.0));
        }
    }*/

    public void orderBounties() {
        // Clear previous most wanted
        mostWanted.clear();

        // Try to find patterns for each enemy
        enemiesTracking.values().forEach(Tracker::findPatterns);

        // First priority, stopped robots
        List<Tracker> ducks = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.DUCK)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        mostWanted.addAll(ducks);

        // Second priority, robots moving in circles
        List<Tracker> sharks = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.SHARK)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        mostWanted.addAll(sharks);

        // Third priority, robots moving in a straight line
        List<Tracker> crabs = enemiesTracking.values().stream().filter(tracker -> tracker.getTrackerType() == TrackerType.CRAB)
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        mostWanted.addAll(crabs);

        // Fourth priority, robot energy left
        List<Tracker> weaker = enemiesTracking.values().stream().filter(tracker -> !mostWanted.contains(tracker))
                .sorted(Comparator.comparingDouble(Tracker::getLastKnownEnergy)).collect(Collectors.toList());
        mostWanted.addAll(weaker);

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

    /**
     * Moves robot to desired location
     * @param location Destination
     */
    public void goToLocation(Location location) {
        faceTowards(location);
        ahead(location.distanceTo(getCurrentLocation()));
    }

    /**
     * Turns robot to desired destination
     * @param location Destination
     */
    public void faceTowards(Location location) {
        double angleOffset = ArenaCalculations.angleRightOffsetToLocation(getHeading(), getCurrentLocation(), location);
        turnRight(angleOffset);
    }

    public void updateRobotStatus(TeammateInfo ti) {
        sendMessageToTeam(new Message(ti));
        teamStatus.put(getName(), ti);
    }

    public boolean isRegisteredTeammate(String name) {
        return teamStatus.values().stream().map(TeammateInfo::getName).anyMatch(teammate -> teammate.equals(name));
    }

    /**
     * Requests teammate to move to location
     * @param location Desired location
     */
    public void requestMoveToLocation(Location location, String teammate) {
        MoveRequest mr = new MoveRequest(location);
        sendMessageToTeammate(teammate, new Message(mr));
    }

    /**
     * Requests teammate to fire at one location
     * @param power Desired power
     * @param location Desired location
     */
    public void requestFireToLocation(String teammate, Double power, Location location) {
        FireRequest fr = new FireRequest(power, location);
        sendMessageToTeammate(teammate, new Message(fr));
    }

    /**
     * Requests team to fire at one location
     * @param power Desired power
     * @param location Desired location
     */
    public void requestFireToLocation(Double power, Location location) {
        FireRequest fr = new FireRequest(power, location);
        sendMessageToTeam(new Message(fr));
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
        String name = si.getScannedRobotEvent().getName();

        // No use for information about itself
        if (name.equals(getName())) {
            return;
        }

        if (isRegisteredTeammate(name)) {
            teammatesTracking.put(name, si);
        }

        else {
            // Check if enemy was already detected before
            if (!enemiesTracking.containsKey(name)) {
                enemiesTracking.put(name, new Tracker(name));
            }

            // Add last tracked location to head of list
            enemiesTracking.get(name).addPing(si);
        }
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
        for(ScanInfo si: teammatesTracking.values()) {
            Location currentLocation = getCurrentLocation();
            Location bullet = getCurrentLocation();
            Vector bulletVector = new Vector(getCurrentLocation(), location).setLength(Rules.getBulletSpeed(0.1));

            // Check if teammate is closer than allowed to shoot
            if (currentLocation.distanceTo(si.getLocation()) >= TEAMMATE_MIN_DISTANCE_TO_FIRE) {
                continue;
            }

            // Check if bullet collided with teammate until bullet passes target location
            while(new Vector(bullet, location).angleWithVector(bulletVector) != 180) {
                bullet = bulletVector.apply(bullet);
                if (ArenaCalculations.isLocationInsideRobot(si.getLocation(), si.getScannedRobotEvent().getHeading(), bullet, ROBOT_EDGES_DISTANCE_TOLERANCE)) {
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

                // Most wanted needs to be recalculated
                outOfDateBounties = true;
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

            case FIRE_REQUEST -> {
                FireRequest fr = message.getFireRequest();
                faceTowards(fr.getTarget());
                fireAndBroadcast(fr.getPower());
            }

            case MOVE_REQUEST -> {
                MoveRequest mr = message.getMoveRequest();
                goToLocation(mr.getDestination());
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

    public Location getRobotLocationFromScanEvent(ScannedRobotEvent sre) {
        return ArenaCalculations.polarInfoToLocation(getCurrentLocation(), ArenaCalculations.convertAngleToPolarOrArena(getHeading() + sre.getBearing()), sre.getDistance());
    }

    /**
     * Override onHitByBullet to define behavior when hit by bullet
     * @param e Resulting HitByBulletEvent instance
     */
    public void onHitByBullet(HitByBulletEvent e) {

    }

    /**
     * Override onHitWall to define behavior when robot hits a wall
     * @param e Resulting HitWallEvent instance
     */
    public void onHitWall(HitWallEvent e) {
        out.println("parede");
        faceTowards(new Location(getBattleFieldWidth()/2,getBattleFieldHeight()/2));
        ahead(90);
    }

    /**
     * Override onBulletHit to define behavior when shot bullet hits another robot
     * @param e Resulting BulletHitEvent instance
     */
    public void onBulletHit(BulletHitEvent e) {
    }

    public void onHitRobot(HitRobotEvent e) {
        // Check if hit an enemy
        double angle = e.getBearing();
        if (!isRegisteredTeammate(e.getName())) {
            turnRight(angle);
            fireAndBroadcast(1);
            setTurnRight(25);
            back(60);
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        String name = e.getName();

        // Check if dead robot is teammate
        if(isRegisteredTeammate(name)) {
            teamStatus.remove(name);
            teammatesTracking.remove(name);

            // Might be necessary a new leader election
            if (name.equals(currentLeader)) {
                if (teamStatus.keySet().size() > 0) {
                    updateRobotStatus(new TeammateInfo(getName(), robotType, getEnergy()));
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

    public boolean simulateGunFire(Tracker tracker, Location target, long currentTick) {
        Vector motion = ArenaCalculations.angleToUnitVector(getHeading()).setLength(getVelocity());
        Location simulationLocation = getCurrentLocation();
        double distance = simulationLocation.distanceTo(target);
        double power = calculateBulletPower(simulationLocation, target);
        double bulletVelocity = Rules.getBulletSpeed(power);
        double simulationGunHeading = getGunHeading();
        long simulationTick = currentTick;
        boolean aiming = true;


        while (aiming) {
            Location nextLocation = motion.apply(simulationLocation);
            double angleToShoot = ArenaCalculations.angleFromOriginToLocation(nextLocation, target);
            double angleAdjustmentNeeded = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(simulationGunHeading, angleToShoot));

            if (Math.abs(angleAdjustmentNeeded) > Rules.GUN_TURN_RATE) {
                simulationGunHeading += angleAdjustmentNeeded > 0 ? Rules.GUN_TURN_RATE : -Rules.GUN_TURN_RATE;
            }

            else {
                aiming = false;
                simulationGunHeading += angleAdjustmentNeeded;
            }

            simulationLocation = nextLocation;
            simulationTick++;
        }

        simulationTick++; // Simulate fire

        simulationTick += (long) Math.ceil(distance / bulletVelocity);


        //////////////////////////////////////////// TODO : DEBUG
        if (ArenaCalculations.isLocationInsideRobot(tracker.getLocationByTick(simulationTick), tracker.getHeading(simulationTick), target, 0)) {
            Location predicted = tracker.getLocationByTick(simulationTick);
            System.out.println("EXPECTED LOCATION WITH PATTERN " + tracker.getTrackerType() +  " -> X:" + predicted.getX() + " Y:" + predicted.getY());
        }
        //////////////////////////////////////////// TODO : DEBUG

        return ArenaCalculations.isLocationInsideRobot(tracker.getLocationByTick(simulationTick), tracker.getHeading(simulationTick), target, 0);
    }

    public void cleanGun() {
        bounty = null;
        target = null;
        readyToFire = false;
    }

    public void onStatus(StatusEvent e) {
        Vector momentumVector = ArenaCalculations.angleToUnitVector(getHeading());

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
        if (bounty != null & target == null) {
            for (int i = 1; i < MAX_SIMULATION_ITERATIONS_ENEMY; i++) {
                Location enemy = bounty.getLocationByTick(e.getTime() + i);
                if (simulateGunFire(bounty, enemy, e.getTime())) {
                    target = enemy;
                    break;
                }
            }

            if (target == null) {
                System.out.println("NO CAN DO WITH SIMULATION!!");
                // TODO: WHAT TO DO IF FAILED SIMULATION?
            }
        }

        if (readyToFire && getGunTurnRemaining() == 0) {
            double power = calculateBulletPower(getCurrentLocation(), target);
            fireAndBroadcast(power);
            cleanGun();
        }


        // Start aiming towards target
        if (target != null) {
            Location nextLocation = momentumVector.setLength(e.getStatus().getVelocity()).apply(getCurrentLocation());
            double angleToShoot = ArenaCalculations.angleFromOriginToLocation(nextLocation, target);
            double angleAdjustmentNeeded = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getGunHeading(), angleToShoot));

            turnGunRight(angleAdjustmentNeeded);
            readyToFire = true;
        }


        // Check if collision with friendly bullet is imminent
        teamBullets.removeIf(bi -> (bi.getBulletLocation(e.getTime()) == null) || (avoidedBullets.contains(bi)));
        avoidedBullets.removeIf(bi -> !teamBullets.contains(bi));

        for (int i=1; i<=BULLET_ITERATIONS_PREVISION; i++) {
            Location nextRobotLocation = momentumVector.setLength(e.getStatus().getVelocity()*i).apply(new Location(e.getStatus().getX(), e.getStatus().getY()));

            for (BulletInfo bi : teamBullets) {
                Location nextBulletLocation = bi.getBulletLocation(e.getTime() + i);
                if (nextBulletLocation != null && ArenaCalculations.isLocationInsideRobot(nextRobotLocation, e.getStatus().getHeading(), nextBulletLocation, ROBOT_EDGES_DISTANCE_TOLERANCE)) {
                    // Robot is in a collision course, calculate in which direction to go
                    Vector bulletVector = bi.getBulletVector();
                    Vector robotVector = ArenaCalculations.angleToUnitVector(e.getStatus().getHeading());
                    Vector robotBackVector = robotVector.negative();

                    // Check if better to move forward of backwards, choose direction vector closest to bullet vector
                    boolean moveForwards = Math.abs(bulletVector.closestAngleBetweenVectors(robotVector)) < Math.abs(bulletVector.closestAngleBetweenVectors(robotBackVector));
                    double distanceToAvoidBullet = moveForwards ? AVOID_BULLET_DISTANCE : -AVOID_BULLET_DISTANCE;
                    Vector generalDirectionVector = moveForwards ? robotVector : robotBackVector;

                    // Check closest angle from direction vector to vector perpendicular with bullet vector
                    Vector clockwiseVectorToBullet = bulletVector.perpendicularClockwise();
                    Vector counterClockwiseVectorToBullet = bulletVector.perpendicularCounterClockwise();

                    double angleOptionOne = Math.abs(ArenaCalculations.shortestAngle(generalDirectionVector.closestAngleBetweenVectors(clockwiseVectorToBullet)));
                    double angleOptionTwo = Math.abs(ArenaCalculations.shortestAngle(generalDirectionVector.closestAngleBetweenVectors(counterClockwiseVectorToBullet)));
                    Vector targetDirection = angleOptionOne < angleOptionTwo ? clockwiseVectorToBullet : counterClockwiseVectorToBullet;

                    // If robot is intended to move backwards, adjust target angle with 180 degrees
                    double calculatedAngle = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(e.getStatus().getHeading(), targetDirection.arenaAngleOfVector()));
                    double angleToAvoidBullet = moveForwards ? calculatedAngle : ArenaCalculations.shortestAngle(180 + calculatedAngle);

                    setAhead(distanceToAvoidBullet);
                    setTurnRight(angleToAvoidBullet);
                    execute();

                    // Once found one possible collision, ignore future iterations
                    avoidedBullets.add(bi);
                    return;
                }
            }
        }
    }
}