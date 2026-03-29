package alternative_bots_1;

import battlecode.common.*;

public class Mopper {

    /**
     * Mopper logic (greedy — nearest enemy paint first):
     *   1. Self-refuel from tower if own paint is critically low
     *   2. Refuel a nearby low-paint soldier (support role)
     *   3. Mop-swing toward the direction with the most enemies
     *   4. Move toward and mop the nearest enemy-painted tile
     *   5. Follow nearest soldier if nothing to clean
     *   6. Explore via quadrant cycling
     *
     * paintUnderfoot() is called at every exit point so the mopper removes
     * enemy paint from the tile it stands on, preventing the double paint-drain
     * penalty for ending a turn on enemy territory.
     */
    public static void runMopper(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int maxPaint      = rc.getType().paintCapacity;
        Util.readBroadcasts(rc);

        RobotInfo[] allAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allAllies) {
            if (ally.getType().isTowerType()) {
                Util.savedTowerLocation = ally.location;
                break;
            }
        }

        // --- PRIORITY 1: Refuel ---
        if (rc.getPaint() < maxPaint * Util.PAINT_REFUEL_THRESHOLD) {
            rc.setIndicatorString("MOPPER: LOW PAINT - refueling");
            boolean transferred = false;
            for (RobotInfo ally : allAllies) {
                if (!ally.getType().isTowerType()) continue;
                int needed = maxPaint - rc.getPaint();
                if (rc.canTransferPaint(ally.location, -needed)) {
                    rc.transferPaint(ally.location, -needed);
                    transferred = true;
                    break;
                }
            }
            if (!transferred && Util.savedTowerLocation != null) {
                Util.moveToward(rc, Util.savedTowerLocation);
            } else if (!transferred) {
                Util.moveToward(rc, Util.getExploreTarget(rc));
            }
            Util.paintUnderfoot(rc);
            return;
        }

        // --- PRIORITY 2: Refuel a nearby soldier ---
        for (RobotInfo ally : allAllies) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            if (ally.getPaintAmount() >= 50) continue;
            int give = rc.getPaint() - Util.MOPPER_MIN_PAINT_RESERVE;
            give = Math.min(give, 40);
            if (give > 0 && rc.canTransferPaint(ally.location, give)) {
                rc.transferPaint(ally.location, give);
                rc.setIndicatorString("Refueled soldier at " + ally.location);
                Util.paintUnderfoot(rc);
                return;
            }
        }

        // --- PRIORITY 3: Mop-swing ---
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length >= 2 && rc.isActionReady()) {
            Direction bestSwing = null;
            int bestCount = 0;
            for (Direction d : Util.cardinals) {
                int count = 0;
                MapLocation step1 = myLoc.add(d);
                MapLocation step2 = step1.add(d);
                for (RobotInfo e : enemies) {
                    MapLocation eLoc = e.getLocation();
                    if (eLoc.distanceSquaredTo(step1) <= 1 ||
                        eLoc.distanceSquaredTo(step2) <= 1) count++;
                }
                if (count > bestCount && rc.canMopSwing(d)) {
                    bestCount = count;
                    bestSwing = d;
                }
            }
            if (bestSwing != null) {
                rc.mopSwing(bestSwing);
                rc.setIndicatorString("Mop swing toward " + bestSwing + " (" + bestCount + " hits)");
                Util.paintUnderfoot(rc);
                return;
            }
        }

        // --- PRIORITY 4: Mop nearest enemy-painted tile ---
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestEnemyTile = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) continue;
            int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (dist < bestDist) {
                bestDist      = dist;
                bestEnemyTile = tile.getMapLocation();
            }
        }

        if (bestEnemyTile != null) {
            if (rc.isMovementReady()) Util.moveToward(rc, bestEnemyTile);
            if (rc.isActionReady() && rc.canAttack(bestEnemyTile)) {
                rc.attack(bestEnemyTile);
                rc.setIndicatorString("Mopping enemy tile at " + bestEnemyTile);
            }
        } else {
            // --- PRIORITY 5: Follow the nearest soldier ---
            RobotInfo nearestSoldier = null;
            int nearestDist = Integer.MAX_VALUE;
            for (RobotInfo ally : allAllies) {
                if (ally.getType() != UnitType.SOLDIER) continue;
                int dist = myLoc.distanceSquaredTo(ally.location);
                if (dist < nearestDist) {
                    nearestDist    = dist;
                    nearestSoldier = ally;
                }
            }
            if (nearestSoldier != null) {
                if (rc.isMovementReady()) Util.moveToward(rc, nearestSoldier.location);
                rc.setIndicatorString("Following soldier at " + nearestSoldier.location);
            } else {
                MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
                if (rc.isMovementReady()) {
                    if (myLoc.distanceSquaredTo(center) > 4) Util.moveToward(rc, center);
                    else Util.moveToward(rc, Util.getExploreTarget(rc));
                }
                rc.setIndicatorString("Mopper exploring");
            }
        }

        Util.paintUnderfoot(rc);
    }
}
