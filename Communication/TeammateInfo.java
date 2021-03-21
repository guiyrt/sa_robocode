package sa_robocode.Communication;

import java.io.Serializable;
import sa_robocode.robots.RobotType;

public class TeammateInfo implements Serializable {
    public final static Integer CAPTAIN_PRIORITY = 1;
    public final static Integer NOT_SUITABLE_FOR_LEADERSHIP = 0;

    private final String name;
    private final RobotType robotType;
    private final Integer leaderPriority;
    private final Double energy;

    public TeammateInfo(String name, RobotType robotType, Double energy) {
        this.name = name;
        this.robotType = robotType;
        this.energy = energy;

        if (robotType == RobotType.CAPTAIN) {
            leaderPriority = CAPTAIN_PRIORITY;
        } else {
            // Droid goes here
            leaderPriority = NOT_SUITABLE_FOR_LEADERSHIP;
        }
    }

    public String getName() {
        return name;
    }

    public RobotType getRobotType() {
        return robotType;
    }

    public Integer getLeaderPriority() {
        return leaderPriority;
    }

    public Double getEnergy() {
        return energy;
    }
}
