package lailatulCoder;

import java.util.Random;

import battlecode.common.*;
import lailatulCoder.util.MapState;

public abstract class Robot {
    protected RobotController rc;
    protected Random rng;

    public Robot(RobotController rc, int seed) {
        this.rc = rc;
        this.rng = new Random(seed);
    }

    public void play() throws GameActionException {
        MapState.updateMap(rc);
        doSmallTactic();
        doBigStrategy();
    }

    public abstract void doSmallTactic() throws GameActionException;

    public abstract void doBigStrategy() throws GameActionException;
}
