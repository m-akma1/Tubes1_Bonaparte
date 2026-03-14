package lailaturCoder;

import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.Message;
import battlecode.common.RobotController;
import lailaturCoder.Objective.Type;

class Robot {
    protected RobotController rc;
    protected int currentTurn = 0;
    protected final Random rng = new Random(2211);
    protected Tile[][] map = new Tile[60][60];
    protected Objective objective = new Objective(null, Type.DEFENDING);

    protected Message[] inbox = new Message[100];
    protected Message[] outbox = new Message[100];

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

    public Robot(RobotController rc) {
        this.rc = rc;
    }

    public void building() {}

    public void exploring() {}

    public void retreating() {}

    public void fighting() {}

    public void defending() throws GameActionException {
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }

    public void startTurn() {
        currentTurn++;

        // Read messages
        Message[] messages = rc.readMessages(currentTurn);
    }

    public void endTurn() {}

    public void play() throws GameActionException {
        startTurn();
        switch (objective.getObjective()) {
            case Type.BUILDING:
                building();
                break;
            case Type.EXPLORING:
                exploring();
                break;
            case Type.RETREATING:
                retreating();
                break;
            case Type.FIGHTING:
                fighting();
                break;
            case Type.DEFENDING:
                defending();
                break;
            default:
                break;
        }
        endTurn();
    }
}
