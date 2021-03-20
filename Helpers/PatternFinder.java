package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.util.List;


public class PatternFinder {
    /**
     * Attempts to detect stationary robot
     * @param list List of last detected locations
     * @param threshold Minimum of locations to corroborate standing still
     * @return Location where robot is standing still, null otherwise
     */
    public static Location patternSittingDuck(List<ScanInfo> list, int threshold) {
        // List needs to have at least 2 known locations
        if (list.size() < threshold + 1) {
            return null;
        }

        // Use last known location as reference
        Location init = list.get(0).getLocation();
        long lastTick = list.get(0).getScannedRobotEvent().getTime();
        int counter = 0;

        for (ScanInfo si: list.subList(1, list.size())) {
            // If past locations are different, duck is not sitting
            if (si.getLocation().sameAs(init)) {
                if (si.getScannedRobotEvent().getTime() != lastTick) {
                    counter++;
                    lastTick = si.getScannedRobotEvent().getTime();
                }
            }
            else {
                break;
            }
        }

        // Check if counter got enough same locations
        return counter >= threshold ? init : null;
    }

    /**
     * Attempts to detect a linear moving pattern
     * @param list List of last detected locations
     * @param threshold Minimum of locations to corroborate the line defined by last 2 locations
     * @return Line defined by last 2 locations if pattern was found, null otherwise
     */
    public static Line patternCrab(List<ScanInfo> list, int threshold) {
        // List needs to have at least 3 known locations
        if (list.size() < threshold + 2) {
            return null;
        }

        ScanInfo p1 = list.get(0);
        ScanInfo p2 = list.get(1);
        int counter = 0;

        // Find m and b values to get linear equation
        Line line = new Line(p1.getLocation(), p2.getLocation());

        for (ScanInfo si: list.subList(2, list.size())) {
            // Check if location satisfies equation
            if (line.isLocationInLine(si.getLocation())) {
                counter++;
            }
            else {
                break;
            }
        }

        return counter >= threshold ? line : null;
    }

    /**
     * Attempts to detect a circular movement pattern
     * @param list List of last detected locations
     * @param threshold Minimum of locations to corroborate the circumference defined by last 3 locations
     * @return Circle defined by last 3 locations if pattern was found, null otherwise
     */
    public static Circle patternShark(List<ScanInfo> list, int threshold) {
        // List needs to have at least 4 known locations
        if (list.size() < threshold + 3) {
            return null;
        }

        ScanInfo p1 = list.get(0);
        ScanInfo p2 = list.get(1);
        ScanInfo p3 = list.get(2);

        // Find circle equation
        Circle circle = new Circle(p1.getLocation(), p2.getLocation(), p3.getLocation());

        for (ScanInfo si: list.subList(3, threshold + 3)) {
            // Check if location satisfies equation
            if (!circle.isLocationInCircle(si.getLocation())) {
                return null;
            }
        }

        return circle;
    }
}
