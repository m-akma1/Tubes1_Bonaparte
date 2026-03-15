package fishybot;

import battlecode.common.*;

public class Util {
    public static int turnCount = 0;
    public static MapLocation savedTowerLocation = null;
    public static MapLocation knownEnemyLocation = null;
    
    public static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };
    public static final Direction[] cardinals = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    public static final double PAINT_REFUEL_THRESHOLD = 0.30;
    public static final int MOPPER_MIN_PAINT_RESERVE = 20;

    public static int waypointIndex = -1;

    public static void readBroadcasts(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message msg : messages) {
            int encoded = msg.getBytes();
            if (encoded <= 0) continue; 
            int x = encoded / 100;
            int y = encoded % 100;

            if (x >= 0 && x < rc.getMapWidth() && y >= 0 && y < rc.getMapHeight()) {
                knownEnemyLocation = new MapLocation(x, y);
                break;
            }
        }
    }

    public static MapLocation getExploreTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        MapLocation[] waypoints = {
            new MapLocation(2,         2),          
            new MapLocation(w / 2,     2),          
            new MapLocation(w - 3,     2),          
            new MapLocation(w - 3,     h / 2),      
            new MapLocation(w - 3,     h - 3),      
            new MapLocation(w / 2,     h - 3),      
            new MapLocation(2,         h - 3),      
            new MapLocation(2,         h / 2),      
        };

        if (waypointIndex == -1) {
            waypointIndex = rc.getID() % waypoints.length;
        }

        MapLocation current = waypoints[waypointIndex];
        if (rc.getLocation().distanceSquaredTo(current) <= 8) {
            waypointIndex = (waypointIndex + 1) % waypoints.length;
        }

        return waypoints[waypointIndex];
    }

    public static void moveToward(RobotController rc, MapLocation dest) throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation myLoc = rc.getLocation();
        Direction dir = myLoc.directionTo(dest);
        if (rc.canMove(dir)) {
            rc.move(dir);
        } else {
            Direction left  = dir.rotateLeft();
            Direction right = dir.rotateRight();
            if      (rc.canMove(left))                rc.move(left);
            else if (rc.canMove(right))               rc.move(right);
            else if (rc.canMove(left.rotateLeft()))   rc.move(left.rotateLeft());
            else if (rc.canMove(right.rotateRight())) rc.move(right.rotateRight());
        }
    }

    public static void paintUnderfoot(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo underfoot = rc.senseMapInfo(rc.getLocation());
        if (!underfoot.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }
}
