package sa_robocode.robots;

import sa_robocode.Communication.*;
import sa_robocode.Helpers.*;
import robocode.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;

/**
 * Captain - a robot by Group 9
 */
public class Captain extends TeamRobot {
	/**
	 * Definition of colors to paint robots.
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
	private final List<BulletInfo> teamBullets = new ArrayList<>();
	private int rank = 0;
	private boolean president = false;
	private boolean electiondone = false;
	private boolean tryingToFire = false;

	/**
	 * Main method with robot behavior.
	 */
	public void run() {
		setColors(ARMY_GREEN, ARMY_DARK_GREEN, RADAR_RED, COPPER_BULLET, BEAM_BLUE); // Set tank colors
		sendMessageToTeam(new Message(getName(), MessageType.TEAMMATE_INFO)); // Register name
		election();
		electiondone = true;
		this.setDebugProperty("Presidente", Boolean.toString(president));
		if(president) {
			while (true) {
				turnRadarLeft(360);
			}
		}
	}

	/**
	 * Override onPaint method to paint in the battlefield
	 * @param g2d Graphics2D instance from robocode instance
	 */
	public void onPaint(Graphics2D g2d) {
		for(List<ScanInfo> l: enemiesTracking.values()) {
			Painter.drawCircleAround(g2d, Color.red, l.get(0).getLocation(), 50);
		}

		for(ScanInfo si: teammatesTracking.values()) {
			if (si == null) continue;
			Painter.drawCircleAround(g2d, Color.green, si.getLocation(), 50);
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
		Location current = new Location(getX(), getY());
		double headingAngle = getHeading();
		double theta = ArenaCalculations.angleFromOriginToLocation(current, location);

		// Figure out which side requires less rotation
		double leftAngleOffset = ArenaCalculations.angleDeltaLeft(headingAngle, theta);
		double rightAngleOffset = ArenaCalculations.angleDeltaRight(headingAngle, theta);

		if (leftAngleOffset < rightAngleOffset) {
			turnLeft(leftAngleOffset);
		}
		else {
			turnRight(rightAngleOffset);
		}
	}

	public void registerTeammate(String name) {
		// In case teammate was scanned before registration
		enemiesTracking.remove(name);

		// Add teammate to tracking
		teammatesTracking.put(name, null);
	}

	public boolean isRegisteredTeammate(String name) {
		return teammatesTracking.containsKey(name);
	}

	public int get_number(String name){
		return parseInt(name.substring(29,name.length()-1));
	}

	public void election(){
		rank = get_number(getName());
		turnRadarLeft(360);
		for (String name: teammatesTracking.keySet()) {
			if (get_number(name)< rank){
				return;
			}
		}
		out.println("presidente");
		president = true;

	}

	public Location robotLocFromRadar(ScannedRobotEvent e) {
		double enemyBearing = getHeading() + e.getBearing();
		double tankX = getX() + e.getDistance() * Math.sin(Math.toRadians(enemyBearing));
		double tankY = getY() + e.getDistance() * Math.cos(Math.toRadians(enemyBearing));

		return new Location(tankX, tankY);
	}

	public Boolean inTheWay(Location start,Location destiny,Location friend ){
		if(destiny.getX() != start.getX()){
			double slope = (destiny.getY()-start.getY())/(destiny.getX()-start.getX());
			double b = start.getY() - slope*start.getX();
		}else{

		}
		return false;
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
	public void requestFireToLocation(Double power, Location location, String teammate) {
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

		// No use for information about self
		if (name.equals(getName())) {
			return;
		}

		if (isRegisteredTeammate(name)) {
			teammatesTracking.put(name, si);
			//out.println("detetei teammate" + si.getScannedRobotEvent().getName());
			if(!president){
				out.println(tryingToFire);
				if(tryingToFire) {
					out.println("detetei teammate" + si.getScannedRobotEvent().getName());
					Location old = robotLocFromRadar(si.getScannedRobotEvent());
					Location dest = new Location(old.getX() + 20, old.getY() + 20);
					requestMoveToLocation(dest, si.getScannedRobotEvent().getName());
				}
			}
		}

		else {
			// Check if enemy was already detected before
			if(president) {
				if (!enemiesTracking.containsKey(name)) {
					enemiesTracking.put(name, new ArrayList<>());
				}

				// Add last tracked location to head of list
				enemiesTracking.get(name).add(0, si);
				//out.println("inimigo detetado " +si.getScannedRobotEvent().getName());
				requestFireToLocation(0.1, robotLocFromRadar(si.getScannedRobotEvent()));
			}
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
			case SCAN_INFO -> {
				ScanInfo si = message.getScanInfo();
				processScanInfo(si);
			}

			case BULLET_INFO -> {
				BulletInfo bi = message.getBulletInfo();
				teamBullets.add(bi);
			}

			case FIRE_REQUEST -> {
				if(!president) {
					//out.println("pedido para disparar");
					FireRequest fr = message.getFireRequest();
					faceTowards(fr.getTarget());
					tryingToFire = true;
					scan();
					fireAndBroadcast(fr.getPower());
					tryingToFire = false;
				}
			}

			case MOVE_REQUEST -> {
				out.println("recebi pedido de mudança");
				MoveRequest mr = message.getMoveRequest();
				goToLocation(mr.getDestination());
			}

			case TEAMMATE_INFO -> {
				String is = message.getInformationString();
				out.println(get_number(is));
				registerTeammate(is);
			}
		}
	}

	/**
	 * Override onScannedRobot to handle robot detection
	 * @param sre Resulting ScannedRobotEvent instance
	 */
	public void onScannedRobot(ScannedRobotEvent sre) {
		Location detectedRobotLocation = ArenaCalculations.polarInfoToLocation(getCurrentLocation(), getHeading() + sre.getBearing(), sre.getDistance());
		ScanInfo si = new ScanInfo(detectedRobotLocation, sre);
		if(president) {
			sendMessageToTeam(new Message(si));
		}
		processScanInfo(si);
	}

	/**
	 * Override onHitByBullet to define behavior when hit by bullet
	 * @param e Resulting HitByBulletEvent instance
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		// Replace the next line with any behavior you would like
		back(10);
	}

	/**
	 * Override onHitWall to define behavior when robot hits a wall
	 * @param e Resulting HitWallEvent instance
	 */
	public void onHitWall(HitWallEvent e) {
		// Replace the next line with any behavior you would like
		back(20);
	}

	/**
	 * Override onBulletHit to define behavior when shot bullet hits another robot
	 * @param e Resulting BulletHitEvent instance
	 */
	public void onBulletHit(BulletHitEvent e) {
	}
}
