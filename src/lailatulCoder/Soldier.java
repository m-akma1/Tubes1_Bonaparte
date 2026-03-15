package lailatulCoder;

import battlecode.common.*;
import lailatulCoder.util.MapState;
import lailatulCoder.util.Objective;
import lailatulCoder.util.Navigation;
import lailatulCoder.util.Stack;
import lailatulCoder.util.Communication;

public class Soldier extends Robot {
    private Stack<Objective> objectives;

    public Soldier(RobotController rc, int seed) {
        super(rc, seed);
        objectives = new Stack<>(20);
        // Default objective
        objectives.push(new Objective(Objective.Type.EXPLORE, null));
    }

    private void requestMopper(MapLocation target) throws GameActionException {
        int msg = Communication.encodeMessage(Communication.MSG_MOPPER_REQUEST, target);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.MOPPER || ally.type.isTowerType()) {
                if (rc.canSendMessage(ally.location, msg)) {
                    rc.sendMessage(ally.location, msg);
                    break; // Just send to one to save bytecode
                }
            }
        }
    }

    @Override
    public void doSmallTactic() throws GameActionException {
        // Strategy states: check if low on paint
        if (rc.getPaint() < rc.getType().paintCapacity * 0.35) {
            // Note: -1 uses Unit type's vision radius automatically
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            MapLocation nearestTower = null;
            int minDist = 99999;
            for (RobotInfo r : allies) {
                if (r.type.isTowerType()) {
                    int dist = rc.getLocation().distanceSquaredTo(r.location);
                    if (dist < minDist) {
                        minDist = dist;
                        nearestTower = r.location;
                    }
                }
            }
            if (nearestTower != null) {
                if (objectives.isEmpty() || objectives.peek().type != Objective.Type.REFILL) {
                    objectives.push(new Objective(Objective.Type.REFILL, nearestTower));
                }
            }
        }

        boolean actionReady = rc.isActionReady();

        // Check for enemies
        if (actionReady) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            RobotInfo lowestHpEnemy = null;
            for (RobotInfo enemy : enemies) {
                if (lowestHpEnemy == null || enemy.health < lowestHpEnemy.health) {
                    lowestHpEnemy = enemy;
                }
            }
            
            if (lowestHpEnemy != null && rc.canAttack(lowestHpEnemy.location)) {
                rc.attack(lowestHpEnemy.location);
                broadcastEnemy(lowestHpEnemy.location);
                actionReady = false; // Flag action used
            } 
        }

        // ONE unified direction loop traversing Ruins, Marks, and Paint
        MapLocation myLoc = rc.getLocation();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.CENTER) continue;
            MapLocation target = myLoc.add(dir);
            if (!rc.canSenseLocation(target)) continue;
            
            int index = MapState.locToIndex(target);
            if (index < 0 || index >= MapState.MAX_MAP_AREA) continue;
            int cell = MapState.grid[index];
            
            // 1. Ruin & Building Logic
            if (MapState.isRuin(cell)) {
                if (!rc.canSenseRobotAtLocation(target)) {
                    if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, target)) {
                        rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, target);
                    } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, target)) {
                        rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, target);
                    }

                    if (objectives.isEmpty() || objectives.peek().type != Objective.Type.BUILD) {
                        objectives.push(new Objective(Objective.Type.BUILD, target));
                    }
                }
            }

            // 2. Painting Logic
            if (actionReady) {
                PaintType mark = MapState.getMarkType(cell);
                PaintType paint = MapState.getPaintType(cell);

                boolean isEnemyPaint = (paint == PaintType.ENEMY_PRIMARY || paint == PaintType.ENEMY_SECONDARY);        

                if (mark != PaintType.EMPTY && isEnemyPaint) {
                    requestMopper(target);
                    // Think about this again :)
                } else if (mark == PaintType.ALLY_SECONDARY && paint != PaintType.ALLY_SECONDARY) {
                    if (rc.canAttack(target)) {
                        rc.attack(target, true);
                        actionReady = false;
                    }
                } else if (mark == PaintType.ALLY_PRIMARY && paint != PaintType.ALLY_PRIMARY) {
                    if (rc.canAttack(target)) {
                        rc.attack(target, false);
                        actionReady = false;
                    }
                } else if (paint == PaintType.EMPTY) {
                    if (rc.canAttack(target)) {
                        rc.attack(target);
                        actionReady = false;
                    }
                }
            }
        }
    }

    @Override
    public void doBigStrategy() throws GameActionException {
        // Read communications
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int val = m.getBytes();
            int type = Communication.decodeMessageType(val);
            if (type == Communication.MSG_ENEMY_SPOTTED) {
                MapLocation targetLoc = Communication.decodeLocation(val);
                if (objectives.isEmpty() || (objectives.peek().type != Objective.Type.FIGHT && objectives.peek().type != Objective.Type.REFILL)) {
                    objectives.push(new Objective(Objective.Type.FIGHT, targetLoc));
                }
            }
        }

        if (!rc.isMovementReady()) return;

        while (!objectives.isEmpty() && isObjectiveComplete(objectives.peek())) {
            objectives.pop();
        }

        Objective current = null;
        if (!objectives.isEmpty()) {
            current = objectives.peek();
        }
        if (current == null) return;

        Direction moveDir = Direction.CENTER;

        switch (current.type) {
            case EXPLORE:
                // Exploration uses Navigation's heuristics to wander map efficiently
                // Picking a random point to navigate.
                MapLocation exploreTarget = new MapLocation(
                    Math.max(0, Math.min(MapState.width - 1, rc.getLocation().x + (int)((rng.nextDouble() * 20) - 10))),
                    Math.max(0, Math.min(MapState.width - 1, rc.getLocation().y + (int)((rng.nextDouble() * 20) - 10)))
                );
                moveDir = Navigation.getNextMove(rc, exploreTarget, true);
                break;
            case DEFEND:
                // Defend: circular patrol near the nearest tower in a 3-5 radius
                RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
                MapLocation nearestTower = null;
                int minDist = 99999;
                for (RobotInfo r : allies) {
                    if (r.type.isTowerType()) {
                        int dist = rc.getLocation().distanceSquaredTo(r.location);
                        if (dist < minDist) {
                            minDist = dist;
                            nearestTower = r.location;
                        }
                    }
                }
                if (nearestTower != null) {
                    // Try to stay in a somewhat circular radius (distance 9 to 16)
                    int distToTower = rc.getLocation().distanceSquaredTo(nearestTower);
                    if (distToTower < 9) {
                        // Move away
                        moveDir = Navigation.getNextMove(rc, rc.getLocation().add(nearestTower.directionTo(rc.getLocation())));
                    } else if (distToTower > 16) {
                        // Move closer
                        moveDir = Navigation.getNextMove(rc, nearestTower);
                    } else {
                        // Move tangentially
                        moveDir = Navigation.getNextMove(rc, rc.getLocation().add(nearestTower.directionTo(rc.getLocation()).rotateLeft().rotateLeft()));
                    }
                } else {
                    moveDir = Direction.values()[(int)(rng.nextDouble() * 8)];
                }
                break;
            case FIGHT:
            case BUILD:
                if (current.target != null) {
                    int dist = rc.getLocation().distanceSquaredTo(current.target);
                    if (dist <= 8) {
                        // Orbit the ruin to paint all around it
                        Direction toRuin = rc.getLocation().directionTo(current.target);
                        MapLocation orbitTarget = rc.getLocation().add(toRuin.rotateRight().rotateRight());
                        moveDir = Navigation.getNextMove(rc, orbitTarget, false);
                    } else {
                        moveDir = Navigation.getNextMove(rc, current.target, false);
                    }
                }
                break;
            case REFILL:
            default:
                break;
        }

        if (moveDir != Direction.CENTER && rc.canMove(moveDir)) {
            rc.move(moveDir);
        }
    }

    private boolean isObjectiveComplete(Objective obj) throws GameActionException {
        if (obj == null) return true;
        
        switch (obj.type) {
            case REFILL:
                return rc.getPaint() >= rc.getType().paintCapacity * 0.9;
            case BUILD:
                if (obj.target != null) {
                    if (rc.canSenseLocation(obj.target)) {
                        RobotInfo r = rc.senseRobotAtLocation(obj.target);
                        if (r != null && r.team == rc.getTeam() && r.type.isTowerType()) {
                            return true; // Built
                        }
                    }
                }
                return false;
            case EXPLORE:
            case DEFEND:
                // Endlessly pop back to default? No, just keep them if they are default
                return false;
            case FIGHT:
                if (obj.target != null && rc.canSenseLocation(obj.target) && rc.getLocation().distanceSquaredTo(obj.target) <= 2) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
}
