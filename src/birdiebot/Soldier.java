package birdiebot;

import battlecode.common.*;

public class Soldier {
    public static void runSoldier(RobotController rc) throws GameActionException {
        int paint = rc.getPaint();
        double pnt = (double) paint / Util.SOLDIER_MAX;

        if (pnt < Util.LOW_PNT)  Util.retreating = true;
        if (pnt > Util.HIGH_PNT) Util.retreating = false;

        if (Util.patrolEdge < 0) Util.patrolEdge = Util.roundNum % 4;

        MapInfo[] mapInfos = rc.senseNearbyMapInfos();
        RobotInfo[] robots = rc.senseNearbyRobots();

        notifyTower(rc, mapInfos, robots);
        tryWithdraw(rc, robots);

        if (Util.retreating) {
            MapLocation t = Util.nearestTower(robots);
            if (t != null) Util.moveToward(rc, t);
            else Util.moveRandom(rc);
            return;
        }

        MapLocation ruin = nearestRuin(rc, mapInfos);
        if (ruin != null && Util.myLoc.distanceSquaredTo(ruin) <= 8) {
            buildTower(rc, ruin, mapInfos);
        }

        MapLocation target = getPatrolTarget(rc, mapInfos);
        Util.moveToward(rc, target);

        paintNearby(rc, mapInfos);
    }

    public static void notifyTower(RobotController rc, MapInfo[] mapInfos,
                             RobotInfo[] robots) throws GameActionException {
        MapLocation enemyTile = null;
        MapLocation ruinLoc   = null;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy() && enemyTile == null) enemyTile = mi.getMapLocation();
            if (mi.hasRuin()            && ruinLoc   == null) ruinLoc   = mi.getMapLocation();
        }

        for (RobotInfo r : robots) {
            if (r.getTeam() != rc.getTeam() || !r.getType().isTowerType()) continue;
            if (!rc.canSendMessage(r.getLocation())) continue;

            if (enemyTile != null) {
                rc.sendMessage(r.getLocation(),
                        Util.encodeMsg(Util.MSG_REQUEST_MOPPER, enemyTile.x, enemyTile.y));
                        System.out.println("Sent Mopper request for enemy paint at (" + enemyTile.x + "," + enemyTile.y + ")");
                }
            else if (ruinLoc != null) {
                rc.sendMessage(r.getLocation(),
                        Util.encodeMsg(Util.MSG_RUIN_FOUND, ruinLoc.x, ruinLoc.y));
                        System.out.println("Sent Ruin Found message for ruin at (" + ruinLoc.x + "," + ruinLoc.y + ")");
            }
            break;
        }
    }

    public static void tryWithdraw(RobotController rc, RobotInfo[] robots) throws GameActionException {
        if (rc.getPaint() >= (int)(Util.SOLDIER_MAX * Util.HIGH_PNT) || !rc.isActionReady()) return;
        for (RobotInfo r : robots) {
            if (r.getTeam() == rc.getTeam() && r.getType().isTowerType()) {
                int need = Util.SOLDIER_MAX - rc.getPaint();
                if (rc.canTransferPaint(r.getLocation(), -need)) {
                    rc.transferPaint(r.getLocation(), -need);
                    return;
                }
            }
        }
    }

    public static void buildTower(RobotController rc, MapLocation ruin,
                            MapInfo[] mapInfos) throws GameActionException {
        UnitType tt = chooseTowerType();

        if (rc.canMarkTowerPattern(tt, ruin)) rc.markTowerPattern(tt, ruin);

        if (rc.isActionReady()) {
            for (MapInfo mi : mapInfos) {
                MapLocation loc = mi.getMapLocation();
                if (Math.abs(loc.x - ruin.x) > 2 || Math.abs(loc.y - ruin.y) > 2) continue;
                PaintType marker = mi.getMark();
                PaintType cur    = mi.getPaint();
                boolean needsPrimary   = marker == PaintType.ALLY_PRIMARY   && cur != PaintType.ALLY_PRIMARY;
                boolean needsSecondary = marker == PaintType.ALLY_SECONDARY  && cur != PaintType.ALLY_SECONDARY;
                if ((needsPrimary || needsSecondary) && rc.canAttack(loc)) {
                    rc.attack(loc, needsSecondary);
                    break;
                }
            }
        }

        if (rc.canCompleteTowerPattern(tt, ruin)) {
            rc.completeTowerPattern(tt, ruin);
            switch (tt) {
                case LEVEL_ONE_MONEY_TOWER:   {Util.builtMoney++; System.out.println("Built Money Tower at " + ruin); break;}
                case LEVEL_ONE_DEFENSE_TOWER: {Util.builtDefense++; System.out.println("Built Defense Tower at " + ruin); break;}
                case LEVEL_ONE_PAINT_TOWER:   {Util.builtPaint++; System.out.println("Built Paint Tower at " + ruin); break;}
            }
            System.out.println("Tower numbers - Money: " + Util.builtMoney + ", Defense: " + Util.builtDefense + ", Paint: " + Util.builtPaint);
        }
    }

    public static UnitType chooseTowerType() {
        if (Util.builtDefense * Util.PAINT_PER_DEFENSE <= Util.builtPaint) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        if (Util.builtMoney * Util.PAINT_PER_MONEY <= Util.builtPaint) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    public static MapLocation getPatrolTarget(RobotController rc, MapInfo[] mapInfos) {
        MapLocation unpainted = Util.nearestUnpainted(mapInfos);
        if (unpainted != null) return unpainted;

        int w = rc.getMapWidth(), h = rc.getMapHeight();
        if (Util.patrolTarget == null || Util.myLoc.distanceSquaredTo(Util.patrolTarget) <= 2) {
            switch (Util.patrolEdge) {
                case 0: Util.patrolTarget = new MapLocation(
                            (int)(Math.random() * w), h - 1); break;
                case 1: Util.patrolTarget = new MapLocation(
                            w - 1, (int)(Math.random() * h)); break;
                case 2: Util.patrolTarget = new MapLocation(
                            (int)(Math.random() * w), 0); break;
                default: Util.patrolTarget = new MapLocation(
                            0, (int)(Math.random() * h)); break;
            }
        }
        return Util.patrolTarget;
    }

    public static MapLocation nearestRuin(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.hasRuin() && !rc.isLocationOccupied(mi.getMapLocation())) {
                int d = Util.myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    public static void paintNearby(RobotController rc, MapInfo[] mapInfos) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint() == PaintType.EMPTY && mi.isPassable() && rc.canAttack(mi.getMapLocation())) {
                int d = Util.myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        if (best != null) rc.attack(best);
    }
}