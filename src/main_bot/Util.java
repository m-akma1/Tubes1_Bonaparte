package main_bot;

import battlecode.common.*;

public class Util {

    // Msg
    public static final int MSG_ENEMY_PAINT    = 1;
    public static final int MSG_REQUEST_MOPPER = 2;
    public static final int MSG_RUIN_FOUND     = 3;

    // Robot paint
    public static final int SOLDIER_MAX  = 200;
    public static final int MOPPER_MAX   = 100;
    public static final int SPLASHER_MAX = 300;

    // Robot paint %
    public static final double LOW_PNT   = 0.30;
    public static final double HIGH_PNT  = 0.80;

    // Tower Ratios
    public static final int PAINT_PER_DEFENSE = 2;
    public static final int PAINT_PER_MONEY   = 3;

    // Global variables
    public static int          roundNum;
    public static MapLocation  myLoc;

    //Tower building tracking
    public static int builtMoney   = 1;
    public static int builtPaint   = 1;
    public static int builtDefense = 0;

    // Soldier state
    public static boolean      retreating     = false;
    public static MapLocation  patrolTarget   = null;
    public static int          patrolEdge     = -1; // 0=N, 1=E, 2=S, 3=W
    public static MapLocation  exploreTarget  = null;
    public static int          exploreRefresh = 0;

    // Mopper state
    public static MapLocation mopperTarget = null;
    
    // Tower state
    public static int mopperDemand = 0;

    public static MapLocation nearestUnpainted(MapInfo[] mapInfos) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint() == PaintType.EMPTY && mi.isPassable()) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    public static MapLocation nearestTower(RobotInfo[] robots) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getType().isTowerType()) {
                int d = myLoc.distanceSquaredTo(r.getLocation());
                if (d < bd) { bd = d; best = r.getLocation(); }
            }
        }
        return best;
    }


    public static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null || myLoc.equals(target)) return;
        Direction dir = myLoc.directionTo(target);
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(dir)) { rc.move(dir); return; }
            dir = dir.rotateRight();
        }
    }

    public static void moveRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction[] dirs = Direction.allDirections();
        int start = (int)(Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            Direction d = dirs[(start + i) % 8];
            if (d != Direction.CENTER && rc.canMove(d)) { rc.move(d); return; }
        }
    }

    public static int encodeMsg(int type, int x, int y) {
        return (type << 28) | (x << 22) | (y << 16);
    }

    public static int getMsgType(int msg) { return (msg >>> 28) & 0xF; }

    public static int getMsgX(int msg) { return (msg >>> 22) & 0x3F; }

    public static int getMsgY(int msg) { return (msg >>> 16) & 0x3F; }
}