package sa_robocode.Communication;

import sa_robocode.Helpers.Location;
import sa_robocode.Helpers.Tracker;

import java.io.Serializable;
import java.util.Set;

/**
 * Implementation of class to send messages between robots
 */
public class Message implements Serializable {
    private final MessageType messageType;
    private final BulletInfo bulletInfo;
    private final ScanInfo scanInfo;
    private final TeammateInfo teammateInfo;
    private final Set<Tracker> bounties;
    private final Location location;

    /**
     * Creates instance of Message only with MessageType, no other content
     * @param messageType MessageType instance
     */
    public Message(MessageType messageType) {
        this.messageType = messageType;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.teammateInfo = null;
        this.bounties = null;
        this.location = null;
    }

    /**
     * Creates instance of Message with BulletInfo
     * @param bulletInfo BulletInfo instance
     */
    public Message(BulletInfo bulletInfo) {
        this.messageType = MessageType.BULLET_INFO;
        this.bulletInfo = bulletInfo;
        this.scanInfo = null;
        this.teammateInfo = null;
        this.bounties = null;
        this.location = null;
    }

    /**
     * Creates instance of Message with ScannedRobotEvent
     * @param scanInfo ScanInfo instance
     */
    public Message(ScanInfo scanInfo) {
        this.messageType = MessageType.SCAN_INFO;
        this.scanInfo = scanInfo;
        this.bulletInfo = null;
        this.teammateInfo = null;
        this.bounties = null;
        this.location = null;
    }


    /**
     * Creates instance of Message with a information String
     * @param teammateInfo TeammateInfo instance
     */
    public Message(TeammateInfo teammateInfo) {
        this.messageType = MessageType.TEAMMATE_REGISTER;
        this.teammateInfo = teammateInfo;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.bounties = null;
        this.location = null;
    }

    public Message(Set<Tracker> bounties) {
        this.messageType = MessageType.BOUNTIES_INFO;
        this.bounties = bounties;
        this.teammateInfo = null;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.location = null;
    }


    public Message(Location location) {
        this.messageType = MessageType.LOCATION_UPDATE;
        this.location = location;
        this.bounties = null;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.teammateInfo = null;
    }

    /**
     * Gets message type from MessageType enum
     * @return MessageType from message
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Gets information regarding BulletInfo
     * Could be null, if messageType is not meant for this information transmission
     * @return BulletInfo from message
     */
    public BulletInfo getBulletInfo() {
        return bulletInfo;
    }

    /**
     * Gets information regarding scanInfo
     * Could be null, if messageType is not meant for this information transmission
     * @return ScanInfo from message
     */
    public ScanInfo getScanInfo() {
        return scanInfo;
    }

    /**
     * Gets information regarding TeammateInfo
     * Could be null, if messageType is not meant for this information transmission
     * @return TeammateInfo from message
     */
    public TeammateInfo getTeammateInfo() {
        return teammateInfo;
    }

    /**
     * Gets information regarding informationString
     * Could be null, if messageType is not meant for this information transmission
     * @return String from message
     */
    public Location getLocation() {
        return location;
    }

    public Set<Tracker> getBounties() {
        return bounties;
    }
}
