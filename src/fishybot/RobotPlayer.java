package fishybot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * Greedy strategy:
 *   - Soldiers  : greedily paint the nearest unpainted tile; upgrade towers opportunistically
 *   - Splashers : greedily target the densest cluster of unpainted tiles (AoE value)
 *   - Moppers   : greedily remove the nearest enemy-painted tile, support refueling
 *   - Tower     : greedily spawn based on resource ratio
 */
public class RobotPlayer {

    static int turnCount = 0;
    static MapLocation savedTowerLocation = null;
    static MapLocation committedRuin = null;
    static MapLocation knownEnemyLocation = null;
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
    static final Direction[] cardinals = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    static final double PAINT_REFUEL_THRESHOLD = 0.30;
    static final int MOPPER_MIN_PAINT_RESERVE = 20;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:  runSoldier(rc);  break;
                    case MOPPER:   runMopper(rc);   break;
                    case SPLASHER: runSplasher(rc); break;
                    default:       runTower(rc);    break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // =========================================================================
    // TOWER
    // =========================================================================

    /**
     * Tower logic:
     *   1. Attack visible enemies (single-block + AoE both used each turn)
     *   2. Spawn robots based on a greedy ratio:
     *      - 1 mopper per 2 soldiers (paint cleanup support)
     *      - 1 splasher per 4 soldiers (AoE coverage)
     *      - Otherwise spawn soldiers (primary painters)
     *   3. Broadcast enemy sightings to allied towers
     */
    public static void runTower(RobotController rc) throws GameActionException {
        // --- ATTACK: Use both single-block and AoE attacks each turn ---
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo weakestEnemy = enemies[0];
            for (RobotInfo e : enemies) {
                if (e.getHealth() < weakestEnemy.getHealth()) weakestEnemy = e;
            }
            MapLocation target = weakestEnemy.getLocation();
            if (rc.canAttack(target)) {
                rc.attack(target);
            }

            MapLocation bestAoeTarget = null;
            int bestAoeCount = 0;
            for (RobotInfo e : enemies) {
                int count = 0;
                for (RobotInfo e2 : enemies) {
                    if (e.getLocation().distanceSquaredTo(e2.getLocation()) <= 9) count++;
                }
                if (count > bestAoeCount) {
                    bestAoeCount = count;
                    bestAoeTarget = e.getLocation();
                }
            }
            if (bestAoeTarget != null && rc.canAttack(bestAoeTarget)) {
                rc.attack(bestAoeTarget);
            }
        }

        // --- SPAWN: Greedy ratio-based unit production ---
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0, mopperCount = 0, splasherCount = 0;
        for (RobotInfo r : nearbyAllies) {
            switch (r.getType()) {
                case SOLDIER:  soldierCount++;  break;
                case MOPPER:   mopperCount++;   break;
                case SPLASHER: splasherCount++; break;
                default: break;
            }
        }

        UnitType toBuild;
        if (soldierCount > 0 && mopperCount * 2 < soldierCount) {
            toBuild = UnitType.MOPPER;
        } else if (soldierCount > 0 && splasherCount * 4 < soldierCount) {
            toBuild = UnitType.SPLASHER;
        } else {
            toBuild = UnitType.SOLDIER;
        }
        
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(toBuild, loc)) {
                rc.buildRobot(toBuild, loc);
                break;
            }
        }

        if (enemies.length > 0 && rc.canBroadcastMessage()) {
            MapLocation eLoc = enemies[0].getLocation();
            int encoded = eLoc.x * 100 + eLoc.y;
            rc.broadcastMessage(encoded);
        }
    }

    // =========================================================================
    // SOLDIER
    // =========================================================================

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
        readBroadcasts(rc);

        RobotInfo[] allAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allAllies) {
            if (ally.getType().isTowerType()) {
                savedTowerLocation = ally.location;
                break;
            }
        }

        tryUpgradeTower(rc);

        // --- PRIORITY 1: Refuel ---
        if (rc.getPaint() < maxPaint * PAINT_REFUEL_THRESHOLD) {
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

            if (!transferred && savedTowerLocation != null) {
                moveToward(rc, savedTowerLocation);
            } else if (!transferred) {
                moveToward(rc, getExploreTarget(rc));
            }

            paintUnderfoot(rc);
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
                if (rc.isMovementReady()) moveToward(rc, committedRuin);
            }

            paintUnderfoot(rc);
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
        } else if (enemies.length == 0 && knownEnemyLocation != null && rc.isMovementReady()) {
            moveToward(rc, knownEnemyLocation);
            rc.setIndicatorString("Moving to broadcast enemy at " + knownEnemyLocation);
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
                moveToward(rc, bestTarget);
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
                if (rc.isMovementReady()) moveToward(rc, center);
            } else {
                if (rc.isMovementReady()) moveToward(rc, getExploreTarget(rc));
            }
        }

        paintUnderfoot(rc);
    }

    // =========================================================================
    // MOPPER
    // =========================================================================

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
        readBroadcasts(rc);

        RobotInfo[] allAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allAllies) {
            if (ally.getType().isTowerType()) {
                savedTowerLocation = ally.location;
                break;
            }
        }

        // --- PRIORITY 1: Refuel ---
        if (rc.getPaint() < maxPaint * PAINT_REFUEL_THRESHOLD) {
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
            if (!transferred && savedTowerLocation != null) {
                moveToward(rc, savedTowerLocation);
            } else if (!transferred) {
                moveToward(rc, getExploreTarget(rc));
            }
            paintUnderfoot(rc);
            return;
        }

        // --- PRIORITY 2: Refuel a nearby soldier ---
        for (RobotInfo ally : allAllies) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            if (ally.getPaintAmount() >= 50) continue;
            int give = rc.getPaint() - MOPPER_MIN_PAINT_RESERVE;
            give = Math.min(give, 40);
            if (give > 0 && rc.canTransferPaint(ally.location, give)) {
                rc.transferPaint(ally.location, give);
                rc.setIndicatorString("Refueled soldier at " + ally.location);
                paintUnderfoot(rc);
                return;
            }
        }

        // --- PRIORITY 3: Mop-swing ---
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length >= 2 && rc.isActionReady()) {
            Direction bestSwing = null;
            int bestCount = 0;
            for (Direction d : cardinals) {
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
                paintUnderfoot(rc);
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
            if (rc.isMovementReady()) moveToward(rc, bestEnemyTile);
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
                if (rc.isMovementReady()) moveToward(rc, nearestSoldier.location);
                rc.setIndicatorString("Following soldier at " + nearestSoldier.location);
            } else {
                MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
                if (rc.isMovementReady()) {
                    if (myLoc.distanceSquaredTo(center) > 4) moveToward(rc, center);
                    else moveToward(rc, getExploreTarget(rc));
                }
                rc.setIndicatorString("Mopper exploring");
            }
        }

        paintUnderfoot(rc);
    }

    // =========================================================================
    // SPLASHER
    // =========================================================================

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
        readBroadcasts(rc);

        RobotInfo[] allAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allAllies) {
            if (ally.getType().isTowerType()) {
                savedTowerLocation = ally.location;
                break;
            }
        }

        // --- PRIORITY 1: Refuel ---
        if (rc.getPaint() < maxPaint * PAINT_REFUEL_THRESHOLD) {
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
            if (!transferred && savedTowerLocation != null) {
                moveToward(rc, savedTowerLocation);
            } else if (!transferred) {
                moveToward(rc, getExploreTarget(rc));
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
                    moveToward(rc, bestMoveTarget);
                }
                return;
            }
        }

        // --- PRIORITY 4: Move toward the densest area ---
        if (bestMoveTarget != null && bestMoveScore > 0 && rc.isMovementReady()) {
            moveToward(rc, bestMoveTarget);
            rc.setIndicatorString("Splasher moving to dense area " + bestMoveTarget
                + " (score=" + bestMoveScore + ")");
        } else if (rc.isMovementReady()) {
            // --- PRIORITY 5: Explore ---
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            if (myLoc.distanceSquaredTo(center) > 4) {
                moveToward(rc, center);
            } else {
                moveToward(rc, getExploreTarget(rc));
            }
            rc.setIndicatorString("Splasher exploring");
        }

        paintUnderfoot(rc);
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

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
                moveToward(rc, nearestUnpainted);
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
                moveToward(rc, targetLoc);
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

    /**
     * Reads all recent messages and decodes the first valid enemy location
     * broadcast by a tower (encoded as x*100 + y). Updates knownEnemyLocation.
     *
     * Called at the start of every robot turn so soldiers, moppers, and splashers
     * can navigate toward broadcast enemy positions even without direct line of sight.
     */
    private static void readBroadcasts(RobotController rc) throws GameActionException {
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

    /**
     * Exploration waypoint system.
     *
     * Instead of cycling fixed quadrant centers (which causes robots to loop
     * the same short path), each robot walks a circuit of 8 landmarks:
     *   4 corners + 4 edge midpoints, in a Z-pattern that covers the whole map.
     *
     * Each robot starts at a different waypoint index (offset by robot ID) so
     * the swarm immediately fans out. A robot only advances to the next waypoint
     * once it is within 8 tiles of the current one — guaranteeing it actually
     * visits each landmark before moving on.
     */
    static int waypointIndex = -1;

    /**
     * Returns the next exploration target for this robot, advancing the
     * waypoint when the robot is close enough to the current one.
     */
    private static MapLocation getExploreTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        MapLocation[] waypoints = {
            new MapLocation(2,         2),          // bottom-left corner
            new MapLocation(w / 2,     2),          // bottom edge mid
            new MapLocation(w - 3,     2),          // bottom-right corner
            new MapLocation(w - 3,     h / 2),      // right edge mid
            new MapLocation(w - 3,     h - 3),      // top-right corner
            new MapLocation(w / 2,     h - 3),      // top edge mid
            new MapLocation(2,         h - 3),      // top-left corner
            new MapLocation(2,         h / 2),      // left edge mid
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

    /**
     * Moves one step toward a destination, trying perpendicular directions
     * to navigate around single-tile obstacles.
     */
    private static void moveToward(RobotController rc, MapLocation dest)
            throws GameActionException {
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

    /**
     * Paints (or mops) the tile the robot is currently standing on if it is not
     * already ally-colored. For soldiers/splashers this places ally paint.
     * For moppers this removes enemy paint — both prevent passive paint drain.
     */
    private static void paintUnderfoot(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo underfoot = rc.senseMapInfo(rc.getLocation());
        if (!underfoot.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }
}