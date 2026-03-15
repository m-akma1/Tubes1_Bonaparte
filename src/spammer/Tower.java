package spammer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

class Tower extends Robot {
    // private final boolean isPaintTower;

    private int soldierCount = 0;
    private int mopperCount = 0;

    private static final int[][] deltas = {
        // Radius 1
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1},

        // Radius 2
        {-2, 0}, {2, 0}, {0, -2}, {0, 2}
    };

    public Tower(RobotController rc, int seed) throws IllegalArgumentException {
        super(rc, seed);
        // isPaintTower = switch (unit) {
        //     case LEVEL_ONE_PAINT_TOWER, LEVEL_TWO_PAINT_TOWER, LEVEL_THREE_PAINT_TOWER -> true;
        //     default -> false;
        // };
    }

    @Override
    public void play() throws GameActionException {
        // If not ready, skip the turn
        if (!rc.isActionReady()) {
            return;
        }

        // Generate potential build locations and robot types
        int[] delta = deltas[rng.nextInt(deltas.length)];
        MapLocation target = currentPosition.translate(delta[0], delta[1]);
        UnitType robotType;

        if (soldierCount == 0) {
            robotType = UnitType.SOLDIER;
        } else if (mopperCount == 0) {
            robotType = UnitType.MOPPER;
        } else {
            if (soldierCount < mopperCount * 3 / 2) {
                robotType = UnitType.SOLDIER;
            } else {
                robotType = UnitType.MOPPER;
            }
        }

        // Try to build a robot if possible
        if (rc.canBuildRobot(robotType, target)) {
            rc.buildRobot(robotType, target);
            if (robotType == UnitType.SOLDIER) {
                soldierCount++;
            } else if (robotType == UnitType.MOPPER) {
                mopperCount++;
            }
            return; // Build once per turn
        } else if (rc.canBuildRobot(UnitType.SOLDIER, target)) {
            // If the random robot type can't be built, try to build a soldier as a fallback
            rc.buildRobot(UnitType.SOLDIER, target);
            soldierCount++;
            return;
        }

        // Attack nearby enemies if possible
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo info : nearbyRobots) {
            if (info.getTeam() != rc.getTeam()) {
                target = info.getLocation();
                if (rc.canAttack(target)) {
                    rc.attack(target);
                    return; // Attack once per turn
                }
            }
        }
    }
}
