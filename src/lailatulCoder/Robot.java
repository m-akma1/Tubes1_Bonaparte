package lailatulCoder;

import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.UnitType;
import lailatulCoder.Objective.Type;

class Robot {
    protected RobotController rc;
    protected int currentTurn = 0;
    protected final Random rng = new Random(2211);
    protected Tile[][] map = new Tile[GameConstants.MAP_MAX_WIDTH][GameConstants.MAP_MAX_HEIGHT];
    protected Objective objective = new Objective(null, Type.DEFENDING);
    protected MapLocation currentPosition = null;
    protected MapLocation tower = null;
    protected UnitType unit;

    protected Message[] inbox = new Message[5];
    protected Tile[] outbox = new Tile[5];
    int sentMessages = 0;

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
        unit = rc.getType();
    }

    public void building() {}

    public void exploring() {}

    public void retreating() {}

    public void fighting() {}


    public void defending() throws GameActionException {
        // Behavior for SOLDIER, override in other units if necessary
        if (unit != UnitType.SOLDIER) return;
        if (rc.getPaint() < 10) return;

        // Check random sensed square, if not painted and can paint, try to paint it. If fails after 5 try, move randomly.
        for (int i = 0; i < 5; i++) {
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation pos = currentPosition.add(dir);
            if (!rc.onTheMap(pos)) continue;
            if (map[pos.x][pos.y] == null) {
                 if (rc.canPaint(pos)) {
                    rc.attack(pos, false);
                    return;
                 }
            }

            if (map[pos.x][pos.y].getPaint() == PaintType.EMPTY) {
                if (rc.canPaint(pos)) {
                    rc.attack(pos, false);
                    return;
                }
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }

    public void startTurn() {
        // Update turn count
        currentTurn++;

        // Sense nearby squares and update map
        currentPosition = rc.getLocation();
        MapInfo[] sensed = rc.senseNearbyMapInfos();
        for (MapInfo info : sensed) {
            MapLocation loc = info.getMapLocation();
            int x = loc.x, y = loc.y;
            if (map[x][y] == null) {
                map[x][y] = new Tile(null, currentTurn, 0);
                map[x][y].computeMessagePriority(currentTurn);
                map[x][y].updateMessagePriority(map[x][y].getMessagePriority() - 3);
            } else {
                map[x][y].updateAll(info, currentTurn, 0);
            }
        }

        // Read messages and update map and objective
        if (currentTurn % 5 == 0) {
            inbox = rc.readMessages(-1);
            for (Message message : inbox) {
                if (message == null) continue;
                int byteMessage = message.getBytes();
                Tile tile = new Tile(byteMessage);
                if (tile.getMark() == null) {
                    objective = new Objective(byteMessage);
                } else {
                    int x = tile.getLocation().x, y = tile.getLocation().y;
                    if (map[x][y] == null) {
                        map[x][y] = tile;
                    } else {
                        map[x][y].updateAll(tile.info, tile.getRecordedAt(), tile.getMessagePriority());
                    }
                }
            }
        }
    }

    public void endTurn() throws GameActionException {
        // Check if all messages from previous turns have been sent
        if (sentMessages == 5) {
            // All messages sent, reset outbox and sentMessages count
            outbox = new Tile[5];
            
            // Queue messages to send
            int idx = 0, minPrior = 100;
            for (Tile[] row : map) {
                for (Tile tile : row) {
                    if (tile == null) continue;
                    if (idx == 5) idx = 0;
                    if (tile.getMessagePriority() <= minPrior) {
                        minPrior = tile.getMessagePriority();
                        outbox[idx] = tile;
                        idx++;
                    }
                }
            }
        }

        // Send remaining messages in outbox
        int i = 0;
        for (Tile tile : outbox) {
            // Skip sent messages
            if (i < sentMessages) {
                i++;
                continue;
            }

            // Send message if possible
            i++; sentMessages++;
            if (tile == null) continue;
            if (rc.canSendMessage(tower)) {
                rc.sendMessage(tower, tile.encode());
            }
        }
    }

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
