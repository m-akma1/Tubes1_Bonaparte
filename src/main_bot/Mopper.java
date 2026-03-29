package main_bot;

import battlecode.common.*;

public class Mopper {
    public static void runMopper(RobotController rc) throws GameActionException {
        MapInfo[]   mapInfos = rc.senseNearbyMapInfos();
        RobotInfo[] robots   = rc.senseNearbyRobots();

        if (rc.isActionReady()) aggressiveSwing(rc, robots);
        if (rc.isActionReady()) mopNearestEnemy(rc, mapInfos);

        givePaintToAllies(rc, robots);

        MapLocation target = deepestEnemyTarget(rc, mapInfos);
        if (target != null) { Util.mopperTarget = target; Util.moveToward(rc, target); }
        else if (Util.mopperTarget != null && Util.roundNum % 5 != 0) Util.moveToward(rc, Util.mopperTarget);
        else { Util.mopperTarget = null; Util.moveRandom(rc); }
    }

    public static void aggressiveSwing(RobotController rc, RobotInfo[] robots)
            throws GameActionException {
        Direction[] card = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int[] counts = new int[4];
        for (RobotInfo r : robots) {
            if (r.getTeam() == rc.getTeam()) continue;
            for (int i = 0; i < 4; i++) {
                MapLocation s1 = Util.myLoc.add(card[i]);
                MapLocation s2 = s1.add(card[i]);
                if (r.getLocation().equals(s1) || r.getLocation().equals(s2)) counts[i]++;
            }
        }
        int best = -1, bc = 0;
        for (int i = 0; i < 4; i++) if (counts[i] > bc) { bc = counts[i]; best = i; }
        if (best >= 0 && bc >= 1 && rc.canMopSwing(card[best])) rc.mopSwing(card[best]);
    }

    public static void mopNearestEnemy(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy() && rc.canAttack(mi.getMapLocation())) {
                int d = Util.myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        if (best != null) rc.attack(best);
    }

    public static void givePaintToAllies(RobotController rc, RobotInfo[] robots)
            throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() <= 20) return;
        for (RobotInfo r : robots) {
            if (r.getTeam() != rc.getTeam() || r.getType() != UnitType.SOLDIER) continue;
            if (r.getPaintAmount() < Util.SOLDIER_MAX * 0.40) {
                int give = Math.min(rc.getPaint() - 15, Util.SOLDIER_MAX - r.getPaintAmount());
                if (give > 0 && rc.canTransferPaint(r.getLocation(), give)) {
                    rc.transferPaint(r.getLocation(), give);
                    return;
                }
            }
        }
    }

    public static MapLocation deepestEnemyTarget(RobotController rc, MapInfo[] mapInfos) {
        boolean teamA = (rc.getTeam() == Team.A);
        MapLocation anchor = teamA
                ? new MapLocation(0, 0)
                : new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() - 1);

        MapLocation best = null;
        int bd = -1;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy()) {
                int d = anchor.distanceSquaredTo(mi.getMapLocation());
                if (d > bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }
}