package lailatulCoder;

import java.util.Random;

import battlecode.common.*;
import lailatulCoder.util.MapState;
import lailatulCoder.util.Communication;

public abstract class Robot {
    protected RobotController rc;
    protected Random rng;
    protected int roundNum = 0;

    public Robot(RobotController rc, int seed) {
        this.rc = rc;
        this.rng = new Random(seed);
    }

    protected void broadcastEnemy(MapLocation enemyLoc) throws GameActionException {
        if (enemyLoc == null) return;
        int msg = Communication.encodeMessage(Communication.MSG_ENEMY_SPOTTED, enemyLoc);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        
        int count = 0;
        for (RobotInfo ally : allies) {
            if (count >= 2) break; // Limit broadcasts to save bytecode
            if (rc.canSendMessage(ally.location, msg)) {
                rc.sendMessage(ally.location, msg);
                count++;
            }
        }
    }

    public int getRoundNum() {
        return roundNum;
    }

    public void play() throws GameActionException {
        roundNum++;
        MapState.updateMap(rc);
        doSmallTactic();
        doBigStrategy();
    }

    public abstract void doSmallTactic() throws GameActionException;

    public abstract void doBigStrategy() throws GameActionException;
}
