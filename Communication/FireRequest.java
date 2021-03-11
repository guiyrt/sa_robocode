package Communication;

import Helpers.Location;
import java.io.Serializable;

public class FireRequest implements Serializable  {
    private final Double power;
    private final Location target;

    public FireRequest(Double power, Location target) {
        this.power = power;
        this.target = target;
    }

    public Double getPower() {
        return power;
    }

    public Location getTarget() {
        return target;
    }
}