package sa_robocode.Communication;

import java.io.Serializable;

/**
 * Enum implementation to declare which is the content of a Message instance
 */
public enum MessageType implements Serializable {
    BULLET_INFO,
    SCAN_INFO,
    STATUS_INFO,
    GUN_READY_INFO,
    TEAMMATE_REGISTER,
    FIRE_REQUEST,
    MOVE_REQUEST
}