package lailatulCoder;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

class Tower extends Robot {
    private static final UnitType[] types = {
        UnitType.SOLDIER,
        UnitType.MOPPER,
        UnitType.SPLASHER
    };

    public Tower(RobotController rc) {
        super(rc);
    }
    
    @Override
    public void building() {}

    @Override
    public void exploring() {}

    @Override
    public void retreating() {}

    @Override
    public void fighting() {}

    @Override
    public void defending() throws GameActionException {
        MapLocation pos = rc.getLocation();
        Direction dir = directions[rng.nextInt(directions.length)];
        pos = pos.add(dir);
        UnitType type = types[rng.nextInt(types.length)];

        if (rc.canBuildRobot(type, pos)) {
            rc.buildRobot(type, pos);
        }
    }

    @Override
    public void startTurn() {}

    @Override
    public void endTurn() {}

}
