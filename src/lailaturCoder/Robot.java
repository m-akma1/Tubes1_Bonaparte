package lailaturCoder;

import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.Message;
import battlecode.common.RobotController;

class Robot {
    protected RobotController rc;
    protected int currentTurn = 0;
    protected final Random rng = new Random(2211);
    protected MapInfo[][] map = new MapInfo[60][60];
    protected Objective objective = null;
    protected Message[] inbox = new Message[100];
    protected Message[] outbox = new Message[100];

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

    public Robot(RobotController rc) {
        this.rc = rc;
    }

    public void building() {}

    public void exploring() {}

    public void retreating() {}

    public void fighting() {}

    public void defending() {}

    public void startTurn() {}

    public void endTurn() {}

    public void play() {}
}
