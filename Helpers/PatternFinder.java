package sa_robocode.Helpers;

import sa_robocode.Communication.ScanInfo;

import java.util.List;


public class PatternFinder {
    /**
     * Attempts to detect stationary robot
     * @param list List of last detected locations
     * @param threshold Minimum of locations to corroborate the line defined by last 2 locations
     * @return Location where robot is standing still, null otherwise
     */
    public static Location patternSittingDuck(List<ScanInfo> list, int threshold) {
        // List needs to have at least 2 known locations
        if (list.size() < threshold + 2) {
            return null;
        }

        // Use last known location as reference
        Location init = list.get(0).getLocation();
        int counter = 1;

        for (ScanInfo si: list.subList(1, list.size())) {
            // If past locations are the same as init, increment counter
            if (si.getLocation().sameAs(init)) {
                counter++;
            }

            // End verification when found one location that did not correspond to reference
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
        int counter = 1;

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
        int counter = 1;

        // Find circle equation
        Circle circle = new Circle(p1.getLocation(), p2.getLocation(), p3.getLocation());

        for (ScanInfo si: list.subList(3, list.size())) {
            // Check if location satisfies equation
            if (circle.isLocationInCircle(si.getLocation())) {
                counter++;
            }

            else {
                break;
            }
        }

        return counter >= threshold ? circle : null;
    }
}
