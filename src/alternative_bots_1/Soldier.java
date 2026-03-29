package alternative_bots_1;

import battlecode.common.*;

public class Soldier {

    public static MapLocation committedRuin = null;

    /**
     * Soldier logic (greedy — nearest unpainted tile):
     *   0. Upgrade: opportunistically upgrade any adjacent tower (uses chips, not paint)
     *   1. Refuel if paint is below threshold (30%) — navigate to saved tower
     *   2. Build tower at nearest valid ruin (including defense towers at 1-per-3 ratio)
     *   3. Attack nearest enemy robot if visible (+ fallback to broadcast location)
     *   4. Greedily move toward + paint the nearest unpainted tile
     *   5. Explore toward map center if everything nearby is painted
     *
     * The greedy objective is: always reduce the count of unpainted tiles
     * by selecting the nearest one (minimizes travel cost each step).
     */
    public static void runSoldier(RobotController rc) throws GameActionException {
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

        tryUpgradeTower(rc);

        // --- PRIORITY 1: Refuel ---
        if (rc.getPaint() < maxPaint * Util.PAINT_REFUEL_THRESHOLD) {
            rc.setIndicatorString("LOW PAINT - heading to tower");

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

        // --- PRIORITY 2: Build a tower ---
        if (committedRuin != null) {
            if (rc.canSenseRobotAtLocation(committedRuin)) {
                RobotInfo occupant = rc.senseRobotAtLocation(committedRuin);
                if (occupant != null && occupant.getType().isTowerType()) {
                    committedRuin = null;
                }
            }
        }

        if (committedRuin == null) {
            MapInfo found = findBestRuin(rc);
            if (found != null) {
                committedRuin = found.getMapLocation();
            }
        }

        if (committedRuin != null) {
            rc.setIndicatorString("Committed to tower at " + committedRuin);

            if (rc.canSenseLocation(committedRuin)) {
                MapInfo ruinInfo = rc.senseMapInfo(committedRuin);
                buildTowerAtRuin(rc, ruinInfo);
            } else {
                if (rc.isMovementReady()) Util.moveToward(rc, committedRuin);
            }

            Util.paintUnderfoot(rc);
            return;
        }

        // --- PRIORITY 3: Attack nearest enemy robot ---
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0 && rc.isActionReady()) {
            RobotInfo weakest = enemies[0];
            for (RobotInfo e : enemies) {
                if (e.getHealth() < weakest.getHealth()) weakest = e;
            }
            if (rc.canAttack(weakest.getLocation())) {
                rc.attack(weakest.getLocation());
                rc.setIndicatorString("Attacking enemy at " + weakest.getLocation());
            }
        } else if (enemies.length == 0 && Util.knownEnemyLocation != null && rc.isMovementReady()) {
            Util.moveToward(rc, Util.knownEnemyLocation);
            rc.setIndicatorString("Moving to broadcast enemy at " + Util.knownEnemyLocation);
        }

        // --- PRIORITY 4: Nearest unpainted tile ---
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestTarget = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.isPassable() || tile.hasRuin()) continue;
            if (tile.getPaint().isAlly()) continue;
            int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (dist < bestDist) {
                bestDist   = dist;
                bestTarget = tile.getMapLocation();
            }
        }

        if (bestTarget != null) {
            if (rc.isMovementReady()) {
                Util.moveToward(rc, bestTarget);
                myLoc = rc.getLocation();
            }
            if (rc.isActionReady() && rc.canAttack(bestTarget)) {
                rc.attack(bestTarget);
            }
            rc.setIndicatorString("Painting toward " + bestTarget);
        } else {
            // --- PRIORITY 5: Explore ---
            rc.setIndicatorString("Exploring quadrant");
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            if (myLoc.distanceSquaredTo(center) > 4) {
                if (rc.isMovementReady()) Util.moveToward(rc, center);
            } else {
                if (rc.isMovementReady()) Util.moveToward(rc, Util.getExploreTarget(rc));
            }
        }

        Util.paintUnderfoot(rc);
    }

    /**
     * Finds the nearest ruin without an existing tower AND without an ally
     * soldier already adjacent to it (prevents two soldiers committing to
     * the same ruin and blocking each other).
     */
    private static MapInfo findBestRuin(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        if (ruins.length == 0) return null;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapLocation ruinLoc : ruins) {
            if (rc.canSenseRobotAtLocation(ruinLoc)) {
                RobotInfo existing = rc.senseRobotAtLocation(ruinLoc);
                if (existing != null && existing.getType().isTowerType()) continue;
            }
            boolean alreadyClaimed = false;
            for (RobotInfo ally : allies) {
                if (ally.getType() == UnitType.SOLDIER
                        && ally.location.distanceSquaredTo(ruinLoc) <= 8) {
                    alreadyClaimed = true;
                    break;
                }
            }
            if (alreadyClaimed) continue;

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < bestDist) {
                bestDist = dist;
                bestLoc  = ruinLoc;
            }
        }

        return bestLoc != null ? rc.senseMapInfo(bestLoc) : null;
    }

    /**
     * Tower-building routine:
     *   1. Decide tower type using a greedy 3-way selection:
     *      - Build DEFENSE if we have >= 3 production towers and ratio allows (1 per 3)
     *      - Otherwise keep paint/money balanced (prefer whichever is lower in count)
     *   2. Mark the 5×5 pattern for the chosen type (fallback order if preferred fails)
     *   3. Find the nearest unpainted pattern tile and MOVE TOWARD IT (not the center)
     *   4. Attack it once in range (soldier attack distSq <= 3)
     *   5. Complete the tower once all tiles match their marks
     */
    public static void buildTowerAtRuin(RobotController rc, MapInfo ruin) throws GameActionException {
        MapLocation myLoc     = rc.getLocation();
        MapLocation targetLoc = ruin.getMapLocation();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int paintTowers = 0, moneyTowers = 0, defenseTowers = 0;

        for (RobotInfo a : allies) {
            UnitType t = a.getType();
            if (t == UnitType.LEVEL_ONE_PAINT_TOWER   ||
                t == UnitType.LEVEL_TWO_PAINT_TOWER   ||
                t == UnitType.LEVEL_THREE_PAINT_TOWER)   paintTowers++;
            if (t == UnitType.LEVEL_ONE_MONEY_TOWER   ||
                t == UnitType.LEVEL_TWO_MONEY_TOWER   ||
                t == UnitType.LEVEL_THREE_MONEY_TOWER)   moneyTowers++;
            if (t == UnitType.LEVEL_ONE_DEFENSE_TOWER ||
                t == UnitType.LEVEL_TWO_DEFENSE_TOWER ||
                t == UnitType.LEVEL_THREE_DEFENSE_TOWER) defenseTowers++;
        }

        int productionTowers = paintTowers + moneyTowers;
        UnitType preferred;
        if (productionTowers >= 3 && defenseTowers * 3 < productionTowers) {
            preferred = UnitType.LEVEL_ONE_DEFENSE_TOWER;
        } else if (paintTowers <= moneyTowers) {
            preferred = UnitType.LEVEL_ONE_PAINT_TOWER;
        } else {
            preferred = UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        UnitType[] buildOrder = {
            preferred,
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
        };

        UnitType towerType = null;
        for (UnitType candidate : buildOrder) {
            if (rc.canMarkTowerPattern(candidate, targetLoc)) {
                rc.markTowerPattern(candidate, targetLoc);
                towerType = candidate;
                break;
            }
        }
        if (towerType == null) {
            for (UnitType candidate : buildOrder) {
                if (rc.canCompleteTowerPattern(candidate, targetLoc)) {
                    towerType = candidate;
                    break;
                }
            }
        }
        if (towerType == null) towerType = preferred;

        MapLocation nearestUnpainted = null;
        int nearestDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos(targetLoc, 8)) {
            PaintType mark  = tile.getMark();
            PaintType paint = tile.getPaint();
            if (mark == PaintType.EMPTY) continue;
            if (mark == paint)           continue;
            int d = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (d < nearestDist) {
                nearestDist      = d;
                nearestUnpainted = tile.getMapLocation();
            }
        }

        if (nearestUnpainted != null) {
            if (rc.isMovementReady() && myLoc.distanceSquaredTo(nearestUnpainted) > 3) {
                Util.moveToward(rc, nearestUnpainted);
                myLoc = rc.getLocation();
            }
            if (rc.isActionReady() && rc.canAttack(nearestUnpainted)) {
                boolean useSecondary = (rc.senseMapInfo(nearestUnpainted).getMark()
                                        == PaintType.ALLY_SECONDARY);
                rc.attack(nearestUnpainted, useSecondary);
            }
            rc.setIndicatorString("Painting pattern tile " + nearestUnpainted
                + " for tower at " + targetLoc);
        } else {
            if (rc.isMovementReady() && myLoc.distanceSquaredTo(targetLoc) > 2) {
                Util.moveToward(rc, targetLoc);
            }
        }

        if (rc.canCompleteTowerPattern(towerType, targetLoc)) {
            rc.completeTowerPattern(towerType, targetLoc);
            rc.setTimelineMarker("Tower built " + towerType + " at " + targetLoc, 0, 255, 0);
            committedRuin = null;
        }
    }


    /**
     * Upgrades any allied tower within upgrade range (distSq <= 2).
     */
    private static void tryUpgradeTower(RobotController rc) throws GameActionException {
        RobotInfo[] closeAllies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : closeAllies) {
            if (!ally.getType().isTowerType()) continue;
            if (rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                rc.setIndicatorString("Upgraded tower at " + ally.location);
                break;
            }
        }
    }
}
