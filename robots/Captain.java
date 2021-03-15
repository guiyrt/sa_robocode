package sa_robocode.robots;

import sa_robocode.Communication.*;
import sa_robocode.Helpers.*;
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
	private final Map<String, List<ScanInfo>> enemiesTracking = new HashMap<>();
	private final Map<String, ScanInfo> teammatesTracking = new HashMap<>();
	private final Map<String, TeammateInfo> teamStatus = new HashMap<>();
	private final Map<String, Boolean> gunStatus = new HashMap<>();
	private List<BulletInfo> teamBullets = new ArrayList<>();
	private String currentLeader = null;
	private Boolean gunCoolingDown = true;

	/**
	 * Main method with robot behavior.
	 */
	public void run() {
		setColors(ARMY_GREEN, ARMY_DARK_GREEN, RADAR_RED, COPPER_BULLET, BEAM_BLUE); // Set tank colors
		sendMessageToTeam(new Message(new TeammateInfo(getName(), robotType, getEnergy()))); // Register in team


		while (true) {
			if (getName().equals(currentLeader) && readyToRainFire()) {
				List<List<ScanInfo>> a = new ArrayList<>(enemiesTracking.values());


				if (a.size() > 0) {
					Location target = a.get(0).get(0).getLocation();

					requestFireToLocation(10.0, target);
					faceTowards(target);
					fireAndBroadcast(10.0);
				}
			}

			turnRadarLeft(45);
		}
	}

	/**
	 * Override onPaint method to paint in the battlefield
	 * @param g2d Graphics2D instance from robocode instance
	 */
	public void onPaint(Graphics2D g2d) {
		for(List<ScanInfo> l: enemiesTracking.values()) {
			List<Location> edges = ArenaCalculations.getEdgesFromCenterLocation(l.get(0).getLocation(), l.get(0).getScannedRobotEvent().getHeading(), 28.0);

			Location leftEdge = ArenaCalculations.getEdge(edges, false, true, true);
			Location rightEdge = ArenaCalculations.getEdge(edges, false, false, false);
			Location bottomEdge = ArenaCalculations.getEdge(edges, true, true, false);
			Location topEdge = ArenaCalculations.getEdge(edges, true, false, true);

			Painter.drawLine(g2d, Color.red, leftEdge, topEdge);
			Painter.drawLine(g2d, Color.red, topEdge, rightEdge);
			Painter.drawLine(g2d, Color.red, rightEdge, bottomEdge);
			Painter.drawLine(g2d, Color.red, bottomEdge, leftEdge);
		}

		for(ScanInfo si: teammatesTracking.values()) {
			List<Location> edges = ArenaCalculations.getEdgesFromCenterLocation(si.getLocation(), si.getScannedRobotEvent().getHeading(), 28.0);

			Location leftEdge = ArenaCalculations.getEdge(edges, false, true, true);
			Location rightEdge = ArenaCalculations.getEdge(edges, false, false, false);
			Location bottomEdge = ArenaCalculations.getEdge(edges, true, true, false);
			Location topEdge = ArenaCalculations.getEdge(edges, true, false, true);

			Painter.drawLine(g2d, Color.green, leftEdge, topEdge);
			Painter.drawLine(g2d, Color.green, topEdge, rightEdge);
			Painter.drawLine(g2d, Color.green, rightEdge, bottomEdge);
			Painter.drawLine(g2d, Color.green, bottomEdge, leftEdge);
		}
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
			Bullet bi = fireBullet(power);
			BulletInfo bulletInfo = new BulletInfo(bi, fireTick, new Location(getX(), getY()), getBattleFieldWidth(), getBattleFieldHeight());
			sendMessageToTeam(new Message(bulletInfo));
			gunCoolingDown = true;
		}
	}

	public boolean readyToRainFire() {
		System.out.println(gunStatus.values().stream().reduce(getGunHeat() == 0.0, (a, b) -> a && b));
		return gunStatus.values().stream().reduce(getGunHeat() == 0.0, (a, b) -> a && b);
	}

	/**
	 * Processes the scanned robot information, depending if is teammate or not
	 * @param si ScanInfo regarding the scanned robot
	 */
	public void processScanInfo(ScanInfo si) {
		String name = si.getScannedRobotEvent().getName();

		// No use for information about self
		if (name.equals(getName())) {
			return;
		}

		if (isRegisteredTeammate(name)) {
			teammatesTracking.put(name, si);
		}

		else {
			// Check if enemy was already detected before
			if (!enemiesTracking.containsKey(name)) {
				enemiesTracking.put(name, new ArrayList<>());
			}

			// Add last tracked location to head of list
			enemiesTracking.get(name).add(0, si);
		}
	}

	public void checkHierarchy() {
		// Add self to consideration
		teamStatus.put(getName(), new TeammateInfo(getName(), robotType, getEnergy()));

		// Sort team elements by last known energy level
		Set<TeammateInfo> energySorted = teamStatus.values().stream()
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

				// After firing, gun is cooling down
				gunStatus.put(me.getSender(), false);
			}

			case GUN_READY_INFO -> {
				gunStatus.put(me.getSender(), true);
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
		return ArenaCalculations.polarInfoToLocation(getCurrentLocation(), getHeading() + sre.getBearing(), sre.getDistance());
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

	}

	/**
	 * Override onBulletHit to define behavior when shot bullet hits another robot
	 * @param e Resulting BulletHitEvent instance
	 */
	public void onBulletHit(BulletHitEvent e) {
	}

	public void onRobotDeath(RobotDeathEvent e) {
		String name = e.getName();

		if(isRegisteredTeammate(name)) {
			teamStatus.remove(name);
			teammatesTracking.remove(name);
			gunStatus.remove(name);

			// Might be necessary leader reelection, so update information
			// If there are no teammates, just check hierarchy
			if (teamStatus.keySet().size() > 0) {
				sendMessageToTeam(new Message(new TeammateInfo(getName(), robotType, getEnergy())));
			}

			else {
				checkHierarchy();
			}
		}

		else {
			enemiesTracking.remove(name);
		}
	}

	public void onStatus(StatusEvent e) {
		// Inform leader that gun is ready to fire
		if (gunCoolingDown && getGunHeat() == 0.0) {
			sendMessageToTeammate(currentLeader, new Message(MessageType.GUN_READY_INFO));
			gunCoolingDown = false;
		}

		// Check if collision with friendly bullet is imminent
		Vector momentumVector = ArenaCalculations.angleToUnitVector(getHeading());
		teamBullets.removeIf(bi -> bi.getBulletLocation(e.getTime()) == null);

		for (int i=1; i<=6; i++) {
			Location nextRobotLocation = momentumVector.setLength(e.getStatus().getVelocity()*i).apply(new Location(e.getStatus().getX(), e.getStatus().getY()));

			for (BulletInfo bi : teamBullets) {
				Location nextBulletLocation = bi.getBulletLocation(e.getTime() + i);
				if (nextBulletLocation != null && ArenaCalculations.isLocationInsideRobot(nextRobotLocation, e.getStatus().getHeading(), nextBulletLocation)) {
					back(20.0);
				}
			}
		}
	}
}