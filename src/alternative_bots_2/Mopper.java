package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

class Mopper extends Robot {
    // private static final double PAINT_CAPACITY = 300;
    // private static final double HEALTH = 150;

    private Queue<MapLocation> tileTargets = new Queue<>(8);
    private Queue<MapLocation> robotTargets = new Queue<>(8);
    private static int[] mopSwing = {0, 0, 0, 0}; // NORTH, EAST, SOUTH, WEST

    public Mopper(RobotController rc, int seed) throws IllegalArgumentException {
        super(rc, seed);
    }

    void updateRobotTargets() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo info : nearbyRobots) {
            if (info.getTeam() != rc.getTeam()) {
                UnitType type = info.getType();
                switch (type) {
                    case SOLDIER, MOPPER, SPLASHER: // Only target enemy robots
                        MapLocation loc = info.getLocation();
                        if (!rc.canAttack(loc)) continue;

                        // Update mopSwing for directional preference
                        mopSwing = new int[]{0, 0, 0, 0}; // Reset swings
                        Direction dir = Util.cardinal(currentPosition, loc);
                        switch (dir) {
                            case NORTH: mopSwing[0]++; break;
                            case EAST: mopSwing[1]++; break;
                            case SOUTH: mopSwing[2]++; break;
                            case WEST: mopSwing[3]++; break;
                            default: break;
                        }
                        
                        // Enqueue the target
                        try {
                            robotTargets.enqueue(loc);
                        } catch (Exception e) {
                            break;
                        }
                        break;
                    default: break;
                }
            } else {
                UnitType type = info.getType();
                int minDistance = Integer.MAX_VALUE;
                switch (type) {
                    case SOLDIER: // Only cares about soldiers
                        int distance = Util.manhattanDistance(currentPosition, info.getLocation());
                        if (distance < minDistance) {
                            minDistance = distance;
                            lastAlly = info.getLocation();
                        }
                        break;
                    default: break;
                }
            }
        }
    }
    
    void updateTileTargets() throws GameActionException {
        MapInfo[] sensed = rc.senseNearbyMapInfos();
        for (MapInfo info : sensed) {
            if (info.getPaint() == PaintType.ENEMY_PRIMARY || info.getPaint() == PaintType.ENEMY_SECONDARY) {
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

        // Prioritize mop swing if possible
        int dirIndex = 0, maxSwing = mopSwing[0];
        for (int i = 0; i < mopSwing.length; i++) {
            if (mopSwing[i] > maxSwing) {
                maxSwing = mopSwing[i];
                dirIndex = i;
            }
        }

        // Mop swing
        if (maxSwing > 1) {
            Direction preferredDir = switch (dirIndex) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                case 3 -> Direction.WEST;
                default -> null;
            };
            if (preferredDir != null) {
                if (rc.canMopSwing(preferredDir)) {
                    rc.mopSwing(preferredDir);
                    return; // Mop swing once per turn
                }
            }
        }

        // Normal attack
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
                robotTargets = new Queue<>(8);
                tileTargets = new Queue<>(8);
                return;
            }
        }

        // Refill nearby allies if possible
        if (lastAlly != null && rc.canTransferPaint(lastAlly, 1)) {
            int paint = Math.min(rc.getPaint(), 50);
            rc.transferPaint(currentPosition, paint);
            return; // Refill once per turn
        }
    }
}
