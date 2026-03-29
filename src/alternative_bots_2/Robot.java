package alternative_bots_2;

import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

abstract class Robot {
    protected final Random rng;
    protected final int MAP_WIDTH;
    protected final int MAP_HEIGHT;
    protected static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    protected RobotController rc;
    protected int currentTurn = 0;
    protected MapLocation currentPosition;
    protected MapLocation lastAlly;
    protected UnitType unit;
    protected Direction vector;

    public Robot(RobotController rc, int seed) throws IllegalArgumentException {
        if (rc == null) {
            throw new IllegalArgumentException("RobotController cannot be null");
        }
        this.rng = new Random(seed);
        this.rc = rc;
        this.MAP_WIDTH = rc.getMapWidth();
        this.MAP_HEIGHT = rc.getMapHeight();
        this.currentPosition = rc.getLocation();
        this.unit = rc.getType();
        this.lastAlly = null;
        this.vector = directions[rng.nextInt(directions.length)];
    }

    public abstract void play() throws GameActionException;
}
