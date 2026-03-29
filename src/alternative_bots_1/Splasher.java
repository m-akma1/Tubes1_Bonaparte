package alternative_bots_1;

import battlecode.common.*;

public class Splasher {

    /**
     * Splasher logic:
     *   1. Refuel if paint critically low
     *   2. Score candidate AoE centers by number of unpainted tiles in radius √2
     *      (bonus score for enemy-painted tiles that need overwriting)
     *   3. Attack the highest-scoring center if score >= 2
     *   4. Move toward the highest-density area if out of attack range
     *   5. Explore toward map center if nothing valuable nearby
     */
    public static void runSplasher(RobotController rc) throws GameActionException {
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
            rc.setIndicatorString("SPLASHER: LOW PAINT - heading to tower");

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
            return;
        }

        // --- PRIORITY 2 : Find the best AoE attack ---
        MapInfo[] allNearbyTiles   = rc.senseNearbyMapInfos();
        MapInfo[] attackCandidates = rc.senseNearbyMapInfos(myLoc, 4);

        MapLocation bestAttackCenter = null;
        int         bestAttackScore  = 0;
        MapLocation bestMoveTarget   = null;
        int         bestMoveScore    = 0;

        for (MapInfo candidate : attackCandidates) {
            MapLocation cLoc = candidate.getMapLocation();
            if (!candidate.isPassable()) continue;

            int score = 0;
            for (MapInfo tile : allNearbyTiles) {
                if (!tile.isPassable() || tile.hasRuin()) continue;
                if (tile.getPaint().isAlly())              continue;
                int tileDistSq = cLoc.distanceSquaredTo(tile.getMapLocation());
                if (tileDistSq <= 2) {
                    score++;
                    if (tile.getPaint().isEnemy()) score++;
                }
            }

            if (score > bestAttackScore) {
                bestAttackScore  = score;
                bestAttackCenter = cLoc;
            }
        }

        for (MapInfo candidate : allNearbyTiles) {
            MapLocation cLoc = candidate.getMapLocation();
            if (!candidate.isPassable()) continue;
            int score = 0;
            for (MapInfo tile : allNearbyTiles) {
                if (!tile.isPassable() || tile.hasRuin()) continue;
                if (tile.getPaint().isAlly())              continue;
                int tileDistSq = cLoc.distanceSquaredTo(tile.getMapLocation());
                if (tileDistSq <= 2) {
                    score++;
                    if (tile.getPaint().isEnemy()) score++;
                }
            }
            if (score > bestMoveScore) {
                bestMoveScore  = score;
                bestMoveTarget = cLoc;
            }
        }

        if (bestAttackCenter != null && bestAttackScore >= 2 && rc.isActionReady()) {
            if (rc.canAttack(bestAttackCenter)) {
                rc.attack(bestAttackCenter);
                rc.setIndicatorString("Splasher AoE at " + bestAttackCenter
                    + " (score=" + bestAttackScore + ")");
                if (rc.isMovementReady() && bestMoveTarget != null
                        && !bestMoveTarget.equals(bestAttackCenter)) {
                    Util.moveToward(rc, bestMoveTarget);
                }
                return;
            }
        }

        // --- PRIORITY 4: Move toward the densest area ---
        if (bestMoveTarget != null && bestMoveScore > 0 && rc.isMovementReady()) {
            Util.moveToward(rc, bestMoveTarget);
            rc.setIndicatorString("Splasher moving to dense area " + bestMoveTarget
                + " (score=" + bestMoveScore + ")");
        } else if (rc.isMovementReady()) {
            // --- PRIORITY 5: Explore ---
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            if (myLoc.distanceSquaredTo(center) > 4) {
                Util.moveToward(rc, center);
            } else {
                Util.moveToward(rc, Util.getExploreTarget(rc));
            }
            rc.setIndicatorString("Splasher exploring");
        }

        Util.paintUnderfoot(rc);
    }
}
