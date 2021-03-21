package sa_robocode.robots;

import sa_robocode.Communication.*;
import sa_robocode.Helpers.*;
import sa_robocode.Helpers.PatternFinder;
import sa_robocode.Helpers.Circle;
import sa_robocode.Helpers.Projection;
import sa_robocode.Helpers.Line;
import robocode.*;
import sa_robocode.Helpers.Vector;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Captain - a robot by Group 9
 */
public class Captain extends TeamRobot {
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
	private final Map<String, Boolean> enemyDroids = new HashMap<>();
	private final Map<String, Location> teammatesTracking = new HashMap<>();
	private final Map<String, TeammateInfo> teamStatus = new HashMap<>();
	private final List<BulletInfo> teamBullets = new ArrayList<>();
	private final List<BulletInfo> avoidedBullets = new ArrayList<>();
	private final Set<Tracker> mostWanted = new LinkedHashSet<>();
	private ArenaNavigation gps = null;
	private MotionType motion = MotionType.READY_TO_MOVE;
	private Location destination = null;
	private String currentLeader = null;
	private boolean outOfDateBounties = true;
	private Tracker bounty = null;
	private Location target = null;
	private boolean readyToFire = false;
	private double lastHeading = 0;
	private double lastVelocity = 0;
	private long lastSpontaneousAction = 0;
	private int makeTurnMax = 0;

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
	private static final int MAX_SIMULATION_ITERATIONS_ENEMY = 100;
	private static final double ROBOT_EDGES_DISTANCE_TOLERANCE = 6.0;
	private static final double CRITICAL_ENERGY_LEVEL = 50.0;
	private static final double PROBABILITY_OF_SPONTANEOUS_TURN = 0.10;
	private static final double PROBABILITY_OF_SPONTANEOUS_SPEED_LIMITATION = 0.2;
	private static final double PROBABILITY_OF_SPONTANEOUS_REVERSING = 0.05;
	private static final long SPONTANEOUS_ACTION_COOLDOWN = 5;


	/**
	 * Main method with robot behavior.
	 */
	public void run() {
		gps = new ArenaNavigation(enemiesTracking, teammatesTracking, getBattleFieldWidth(), getBattleFieldHeight());
		setColors(ARMY_GREEN, ARMY_DARK_GREEN, RADAR_RED, COPPER_BULLET, BEAM_BLUE); // Set tank colors
		updateRobotStatus(new TeammateInfo(getName(), robotType, getEnergy())); // Register in team
		lastHeading = getHeading();
		lastVelocity = getVelocity();
		currentLeader = getName();
	}

	/**
	 * Override onPaint method to paint in the battlefield
	 * @param g2d Graphics2D instance from robocode instance
	 */
	public void onPaint(Graphics2D g2d) {
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
		// Most wanted needs to be recalculated
		outOfDateBounties = true;

		String name = si.getScannedRobotEvent().getName();

		// No use for information about itself
		if (name.equals(getName())) {
			return;
		}

		if (isRegisteredTeammate(name)) {
			teammatesTracking.put(name, si.getLocation());
		}

		else {
			// Check if enemy was already detected before
			if (!enemiesTracking.containsKey(name)) {
				enemiesTracking.put(name, new Tracker(name));

				// Check if first scan of enemy has over 100 energy points, because droids have 120
				enemyDroids.put(name, si.getScannedRobotEvent().getEnergy() > 100);
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

			case FIRE_REQUEST -> {
				FireRequest fr = message.getFireRequest();
				faceTowards(fr.getTarget());
				fireAndBroadcast(fr.getPower());
			}

			case MOVE_REQUEST -> {
				MoveRequest mr = message.getMoveRequest();
				goToLocation(mr.getDestination());
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

	public Location getRobotLocationFromScanEvent(ScannedRobotEvent sre) {
		return ArenaCalculations.polarInfoToLocation(getCurrentLocation(), ArenaCalculations.convertAngleToPolarOrArena(getHeading() + sre.getBearing()), sre.getDistance());
	}

	/**
	 * Override onScannedRobot to handle robot detection
	 * @param sre Resulting ScannedRobotEvent instance
	 */
	public void onScannedRobot(ScannedRobotEvent sre) {
		Location detectedRobotLocation = getRobotLocationFromScanEvent(sre);
		ScanInfo si = new ScanInfo(detectedRobotLocation, sre);

		sendMessageToTeam(new Message(si));
		processScanInfo(si);
	}


	/**
	 * Override onHitWall to define behavior when robot hits a wall
	 * @param e Resulting HitWallEvent instance
	 */
	public void onHitWall(HitWallEvent e) {
		double wallAngle = (getHeading() + e.getBearing() + 360) % 360;
		double getawayAngle = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getGunHeading(), wallAngle + 90));

		setTurnRight(getawayAngle);
		setBack(60);

		motion = MotionType.HIT_WALL;
	}

	public void onHitRobot(HitRobotEvent e) {
		if (!isRegisteredTeammate(e.getName())) {
			double enemyAngle = (getHeading() + e.getBearing() + 360) % 360;
			double shootingAngle = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getGunHeading(), enemyAngle));
			setTurnGunRight(ArenaCalculations.shortestAngle(shootingAngle));

			motion = MotionType.ENEMY_COLLISION;
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
				if (teamStatus.keySet().size() > 1) {
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

		// Update teammates of current location
		sendMessageToTeam(new Message(getCurrentLocation()));

		// Keep radar spinning
		setTurnRadarLeft(Rules.RADAR_TURN_RATE);

		// Check if is avoiding bullet
		if (motion != MotionType.AVOIDING_BULLET) {
			if (motion != MotionType.AVOIDING_WALL && gps != null && gps.tooCloseToWalls(getCurrentLocation())) {
				motion = MotionType.AVOIDING_WALL;

				double angleToCenter = ArenaCalculations.angleFromOriginToLocation(getCurrentLocation(),new Location(getBattleFieldWidth()/2.0, getBattleFieldHeight()/2.0));
				double heading = getHeading();
				double buttHeading = (heading + 180) % 360;

				double angleDeltaHeadingToCenter = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(heading, angleToCenter));
				double angleDeltaButtHeadingToCenter = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(buttHeading, angleToCenter));

				if (Math.abs(angleDeltaHeadingToCenter) > Math.abs(angleDeltaButtHeadingToCenter)) {
					setTurnRight(ArenaCalculations.shortestAngle(buttHeading+180));
					setAhead(-50);
				}

				else {
					setTurnRight(angleDeltaHeadingToCenter);
					setAhead(50);
				}

			}

			if (motion == MotionType.ENEMY_COLLISION && getGunTurnRemaining() == 0) {
				fireAndBroadcast(3);
				motion = MotionType.READY_TO_MOVE;
				back(50);
			}

			if (motion == MotionType.HIT_WALL && getDistanceRemaining() == 0) {
				motion = MotionType.READY_TO_MOVE;
			}

			if (motion == MotionType.AVOIDING_WALL && getTurnRemaining() == 0 && getVelocity() == 0) {
				motion = MotionType.READY_TO_MOVE;
			}

			if (motion == MotionType.READY_TO_MOVE && gps != null) {
				destination = gps.getNextDestination(getCurrentLocation());
				motion = MotionType.MAKING_TURN;
			}

			// Keep moving to destination
			if ((motion == MotionType.GOING_TO_DESTINATION || motion == MotionType.MAKING_TURN) && (getCurrentLocation().distanceTo(destination) < 50)) {
				motion = MotionType.READY_TO_MOVE;
			}

			// Deal with spontaneous actions that introduce variable behavior
			if (motion == MotionType.SPONTANEOUS_TURN && getTurnRemaining() == 0) {
				motion = MotionType.READY_TO_MOVE;
				lastSpontaneousAction = e.getTime();
			}

			else if (motion == MotionType.SPONTANEOUS_SPEED_LIMIT && getVelocity() == lastVelocity) {
				motion = MotionType.GOING_TO_DESTINATION;
				setMaxVelocity(Rules.MAX_VELOCITY);
				lastSpontaneousAction = e.getTime();

			}

			else if (motion == MotionType.SPONTANEOUS_REVERSE && getDistanceRemaining() == 0) {
				motion = MotionType.GOING_TO_DESTINATION;
				lastSpontaneousAction = e.getTime();

			}

			if ((motion == MotionType.GOING_TO_DESTINATION) && (getTurnRemaining() == 0)) {
				setMaxVelocity(Rules.MAX_VELOCITY);
				setAhead(getCurrentLocation().distanceTo(destination));

				if (e.getTime() - lastSpontaneousAction >= SPONTANEOUS_ACTION_COOLDOWN) {
					if (ThreadLocalRandom.current().nextDouble() <= PROBABILITY_OF_SPONTANEOUS_TURN) {
						double turnAngle = ThreadLocalRandom.current().nextDouble(-90, 90);
						setTurnRight(turnAngle);
						motion = MotionType.SPONTANEOUS_TURN;

					} else if (ThreadLocalRandom.current().nextDouble() <= PROBABILITY_OF_SPONTANEOUS_SPEED_LIMITATION) {
						double speedLimit = ThreadLocalRandom.current().nextDouble(0, 5);
						setMaxVelocity(speedLimit);
						motion = MotionType.SPONTANEOUS_SPEED_LIMIT;

					} else if (ThreadLocalRandom.current().nextDouble() <= PROBABILITY_OF_SPONTANEOUS_REVERSING) {
						double reverseDistance = ThreadLocalRandom.current().nextDouble(50, 100);
						setAhead(-reverseDistance);
						motion = MotionType.SPONTANEOUS_REVERSE;

					}
				}
			}

			if (motion == MotionType.MAKING_TURN) {
				makeTurnMax++;
				if (gps.tooCloseToRobots(getCurrentLocation()) || makeTurnMax > 20) {
					back(15);
					motion = MotionType.READY_TO_MOVE;
					makeTurnMax = 0;
				}

				else {
					double nextVelocity = acceleration > 0 ? Math.min(e.getStatus().getVelocity() + acceleration, Rules.MAX_VELOCITY) : Math.max(e.getStatus().getVelocity() + acceleration, 0);
					double maxTurn = Rules.MAX_TURN_RATE - (0.75 * nextVelocity);

					double currentAngleToDestination = ArenaCalculations.angleFromOriginToLocation(getCurrentLocation(), destination);
					double angleDelta = ArenaCalculations.shortestAngle(ArenaCalculations.angleDeltaRight(getHeading(), currentAngleToDestination));

					if (Math.abs(angleDelta) > maxTurn) {
						setMaxVelocity(6.5);
						angleDelta = angleDelta > 0 ? maxTurn : -maxTurn;
					} else {
						motion = MotionType.GOING_TO_DESTINATION;
					}

					setTurnRight(angleDelta);
					setAhead(50);
				}
			}

			else {
				makeTurnMax = 0;
			}
		}

		else {
			System.out.println(Math.abs(getTurnRemaining()) + " " + Math.abs(getDistanceRemaining()));
			if ((Math.abs(getTurnRemaining()) < 1 ) && (Math.abs(getDistanceRemaining()) < 1)) {
				motion = MotionType.READY_TO_MOVE;
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
			double power = calculateBulletPower(getCurrentLocation(), target);
			fireAndBroadcast(power);
			cleanGun();
		}

		// Start aiming towards target
		if (target != null) {
			Location nextLocation = ArenaCalculations.polarInfoToLocation(getCurrentLocation(), ArenaCalculations.convertAngleToPolarOrArena(getHeading() + headingDiff), e.getStatus().getVelocity());
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
					setMaxVelocity(Rules.MAX_VELOCITY);

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

					// Once found one possible collision, ignore future iterations
					avoidedBullets.add(bi);
					return;
				}
			}
		}
	}
}