package main_bot;

import battlecode.common.*;

public class Splasher {
    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] mapInfos = rc.senseNearbyMapInfos();

        if (rc.isActionReady()) {
            MapLocation best = bestSplashTarget(rc, mapInfos);
            if (best != null && rc.canAttack(best)) rc.attack(best);
        }

        MapLocation enemy = nearestEnemyPaint(mapInfos);
        if (enemy != null) Util.moveToward(rc, enemy);
        else {
            MapLocation un = Util.nearestUnpainted(mapInfos);
            if (un != null) Util.moveToward(rc, un);
            else            Util.moveRandom(rc);
        }
    }

    public static MapLocation bestSplashTarget(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bs = 0;
        for (MapInfo c : mapInfos) {
            if (!rc.canAttack(c.getMapLocation())) continue;
            int score = 0;
            for (MapInfo inner : mapInfos) {
                if (c.getMapLocation().distanceSquaredTo(inner.getMapLocation()) <= 4) {
                    if (inner.getPaint() == PaintType.EMPTY) score += 2;
                    else if (inner.getPaint().isEnemy())      score += 3;
                }
            }
            if (score > bs) { bs = score; best = c.getMapLocation(); }
        }
        return best;
    }

    public static MapLocation nearestEnemyPaint(MapInfo[] mapInfos) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy()) {
                int d = Util.myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }
}