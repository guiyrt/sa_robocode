package Team.Communication;

import robocode.ScannedRobotEvent;
import Team.Helpers.Location;
import java.io.Serializable;

/**
 * Class implementation to inform teammate about robot scan
 */
public class ScanInfo implements Serializable {
    private final Location location;
    private final ScannedRobotEvent scannedRobotEvent;

    /**
     * Constructor
     * @param location Scanned robot location
     * @param scannedRobotEvent ScannedRobotEvent instance
     */
    public ScanInfo(Location location, ScannedRobotEvent scannedRobotEvent) {
        this.location = location;
        this.scannedRobotEvent = scannedRobotEvent;
    }

    /**
     * Get location
     * @return Last known location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Get scannedRobotEvent
     * @return ScannedRobotEvent instance
     */
    public ScannedRobotEvent getScannedRobotEvent() {
        return scannedRobotEvent;
    }
}
