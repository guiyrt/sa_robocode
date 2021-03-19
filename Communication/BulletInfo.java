package sa_robocode.Communication;

import sa_robocode.Helpers.Location;
import sa_robocode.Helpers.ArenaCalculations;
import sa_robocode.Helpers.Vector;
import robocode.Bullet;
import java.io.Serializable;

/**
 * Class implementation to store information regarding shot bullets
 */
public class BulletInfo implements Serializable {
    private final Bullet bullet;
    private final Double arenaWidth;
    private final Double arenaHeight;
    private final Long firedOnTick;
    private final Location firedFrom;

    /**
     * Constructor for BulletInfo instance
     * @param bullet Robocode Bullet instance
     * @param firedOnTick Time Tick at which the bullet was shot
     * @param firedFrom Location of the robot when the bullet was shot
     * @param arenaWidth Arena width
     * @param arenaHeight Arena height
     */
    public BulletInfo(Bullet bullet, Long firedOnTick, Location firedFrom, Double arenaWidth, Double arenaHeight) {
        this.bullet = bullet;
        this.firedOnTick = firedOnTick;
        this.firedFrom = firedFrom;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
    }

    /**
     * Gets tick on which the bullet was shot
     * @return Initial bullet tick
     */
    public Long getFiredOnTick() {
        return firedOnTick;
    }

    /**
     * Gets bullet start location
     * @return Location from which the bullet was shot
     */
    public Location getFiredFrom() {
        return firedFrom;
    }

    /**
     * Gets bullet heading angle
     * @return Bullet heading angle
     */
    public Double getFiredAngle() {
        return bullet.getHeading();
    }

    /**
     * Retrieves bullet power
     * @return Bullet power
     */
    public Double getBulletPower() {
        return bullet.getPower();
    }

    /**
     * Calculates bullet velocity, which is power dependant
     * @return Bullet velocity
     */
    public Double getBulletVelocity() {
        return bullet.getVelocity();
    }

    public Vector getBulletVector() {
        return ArenaCalculations.angleToUnitVector(getFiredAngle()).scalar(getBulletVelocity());
    }

    /**
     * Given a tick, calculates the position of the bullet in that tick
     * @param targetTick On which tick to determine the bullet position
     * @return The bullet location for a given targetTick
     */
    public Location getBulletLocation(Long targetTick) {
        // Check if bullet has hit anyone
        if (bullet.getVictim() != null) {
            return null;
        }

        Long ticksPassed = targetTick - getFiredOnTick();
        Vector bulletVector = getBulletVector().setLength(ticksPassed * getBulletVelocity());
        Location currentLocation = bulletVector.apply(getFiredFrom());

        // Check if bullet has not collided with walls
        return ArenaCalculations.isInsideArena(currentLocation, arenaWidth, arenaHeight) ? currentLocation : null;
    }
}
