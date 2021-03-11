package sa_robocode.Communication;

import sa_robocode.Helpers.Location;
import java.io.Serializable;

public class MoveRequest implements Serializable {
    private final Location destination;

    public MoveRequest(Location destination) {
        this.destination = destination;
    }

    public Location getDestination() {
        return destination;
    }
}
