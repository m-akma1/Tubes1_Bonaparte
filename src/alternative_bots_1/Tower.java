package alternative_bots_1;

import battlecode.common.*;

public class Tower {
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
        
        for (Direction dir : Util.directions) {
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
}
