package lailatulCoder;

import battlecode.common.*;

public class Tower extends Robot {
    public Tower(RobotController rc, int seed) {
        super(rc, seed);
    }
    
    @Override
    public void doSmallTactic() throws GameActionException {
        // Tower micro behavior
    }

    @Override
    public void doBigStrategy() throws GameActionException {
        int roundNum = rc.getRoundNum();
        
        // Initial spawn at round 1
        if (roundNum == 1) {
            trySpawn(UnitType.SOLDIER);
        } else {
            // Modulo spawn depending on chips
            if (roundNum % 10 == 0 && rc.getChips() >= 1000) {
                trySpawn(UnitType.MOPPER);
            } else if (roundNum % 5 == 0 && rc.getChips() >= 500) {
                trySpawn(UnitType.SPLASHER);
            } else if (roundNum % 2 == 0 && rc.getChips() >= 250) {
                trySpawn(UnitType.SOLDIER);
            }
        }
    }
    
    private void trySpawn(UnitType type) throws GameActionException {
        Direction[] dirs = Direction.values();
        for (Direction dir : dirs) {
            if (rc.canBuildRobot(type, rc.getLocation().add(dir))) {
                rc.buildRobot(type, rc.getLocation().add(dir));
                return;
            }
        }
    }
}
