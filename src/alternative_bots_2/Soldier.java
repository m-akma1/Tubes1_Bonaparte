package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

class Soldier extends Robot {
    // private static final double PAINT_CAPACITY = 200;
    // private static final double HEALTH = 250;

    private Queue<MapLocation> tileTargets = new Queue<>(8);
    private Queue<MapLocation> robotTargets = new Queue<>(8);

    public Soldier(RobotController rc, int seed) throws IllegalArgumentException {
        super(rc, seed);
    }

    void updateRobotTargets() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo info : nearbyRobots) {
            if (info.getTeam() != rc.getTeam()) {
                UnitType type = info.getType();
                switch (type) {
                    case SOLDIER, MOPPER, SPLASHER: break;
                    default: // Only attack towers
                        MapLocation loc = info.getLocation();
                        if (!rc.canAttack(loc)) continue;
                        try {
                            robotTargets.enqueue(loc);
                        } catch (Exception e) {
                            break;
                        }
                        break;
                }
            } else {
                UnitType type = info.getType();
                int minDistance = Integer.MAX_VALUE;
                switch (type) {
                    case SOLDIER, MOPPER, SPLASHER: break;
                    default: // Update lastAlly (tower) if it's closer
                        int distance = Util.manhattanDistance(currentPosition, info.getLocation());
                        if (distance < minDistance) {
                            minDistance = distance;
                            lastAlly = info.getLocation();
                        }
                        break;
                }
            }
        }
    }
    
    void updateTileTargets() throws GameActionException {
        MapInfo[] sensed = rc.senseNearbyMapInfos();
        for (MapInfo info : sensed) {
            if (info.getPaint() == PaintType.EMPTY) {
                MapLocation loc = info.getMapLocation();
                if (!rc.canAttack(loc)) continue;

                try {
                    tileTargets.enqueue(loc);
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    void wander() throws GameActionException {
        if (rng.nextBoolean()) {
            Direction dir = directions[rng.nextInt(directions.length)];
            // Avoid go backwards if possible
            if (vector.opposite() != dir) {
                vector = dir;
            }
        }

        // If blocked, try a different direction
        int attempts = 0;
        while (!rc.canMove(vector) && attempts < directions.length) {
            vector = directions[rng.nextInt(directions.length)];
            attempts++;
        }

        if (rc.canMove(vector)) {
            rc.move(vector);
        }
    }

    @Override
    public void play() throws GameActionException {
        // If paint is empty, just suicide
        if (rc.getPaint() == 0) {
            rc.disintegrate();
            return;
        }

        // Update current position and targets
        currentPosition = rc.getLocation();
        updateRobotTargets();
        if (!tileTargets.isFull()) {
            updateTileTargets();
        }

        // Returns early if not action ready
        if (!rc.isActionReady()) {
            return;
        }

        // Randomly decide to wander instead of attacking to avoid getting stuck
        if (rng.nextInt() % 2 == 0) {
            wander();
            tileTargets = new Queue<>(8);
            robotTargets = new Queue<>(8);
            return;
        }

        // Prioritize attacking robots over tiles
        MapLocation target = null;
        try {
            if (!robotTargets.isEmpty()) {
                target = robotTargets.dequeue();
            } else {
                target = tileTargets.dequeue();
            }
            // Attack the target if possible
            if (target != null && rc.canAttack(target)) {
                rc.attack(target);
                return; // Attack once per turn
            }
        } catch (Exception e) {
            // Queue is empty, will wander instead
            if (target == null) {
                wander();
                tileTargets = new Queue<>(8);
                robotTargets = new Queue<>(8);
                return;
            }
        }

    }
}
