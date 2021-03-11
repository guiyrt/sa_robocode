package sa_robocode.Communication;


import java.io.Serializable;

/**
 * Implementation of class to send messages between robots
 */
public class Message implements Serializable {
    private final MessageType messageType;
    private final BulletInfo bulletInfo;
    private final ScanInfo scanInfo;
    private final FireRequest fireRequest;
    private final MoveRequest moveRequest;

    /**
     * Creates instance of Message with BulletInfo
     * @param bulletInfo BulletInfo instance
     */
    public Message(BulletInfo bulletInfo) {
        this.messageType = MessageType.BULLET_INFO;
        this.bulletInfo = bulletInfo;
        this.scanInfo = null;
        this.fireRequest = null;
        this.moveRequest = null;
    }

    /**
     * Creates instance of Message with ScannedRobotEvent
     * @param scanInfo ScanInfo instance
     */
    public Message(ScanInfo scanInfo) {
        this.messageType = MessageType.SCAN_INFO;
        this.scanInfo = scanInfo;
        this.bulletInfo = null;
        this.fireRequest = null;
        this.moveRequest = null;
    }

    /**
     * Creates instance of Message with FireRequest
     * @param fireRequest FireRequest instance
     */
    public Message(FireRequest fireRequest) {
        this.messageType = MessageType.FIRE_REQUEST;
        this.fireRequest = fireRequest;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.moveRequest = null;
    }

    /**
     * Creates instance of Message with MoveRequest
     * @param moveRequest MoveRequest instance
     */
    public Message(MoveRequest moveRequest) {
        this.messageType = MessageType.MOVE_REQUEST;
        this.moveRequest = moveRequest;
        this.bulletInfo = null;
        this.scanInfo = null;
        this.fireRequest = null;
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
     * Gets information regarding fireRequest
     * Could be null, if messageType is not meant for this information transmission
     * @return FireRequest from message
     */
    public FireRequest getFireRequest() {
        return fireRequest;
    }

    /**
     * Gets information regarding moveRequest
     * Could be null, if messageType is not meant for this information transmission
     * @return MoveRequest from message
     */
    public MoveRequest getMoveRequest() {
        return moveRequest;
    }
}
