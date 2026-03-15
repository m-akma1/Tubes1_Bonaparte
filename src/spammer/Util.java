package spammer;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

class Util {
    public static int manhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static Direction directionTo(MapLocation from, MapLocation to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;

        if (dx == 0 && dy == 0) {
            return null; // Same location
        }

        double angle = Math.atan2(dy, dx);
        double octant = 2 * Math.PI / 8;
        int index = (int) Math.round(angle / octant) % 8;

        return Robot.directions[index];
    }

    public static Direction cardinal(MapLocation from, MapLocation to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;

        if (dx == 0 && dy == 0) {
            return null; // Same location
        }

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dy > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
