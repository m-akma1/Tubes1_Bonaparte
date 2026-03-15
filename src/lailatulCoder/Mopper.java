package lailatulCoder;

import battlecode.common.*;
import lailatulCoder.util.Communication;
import lailatulCoder.util.MapState;
import lailatulCoder.util.Navigation;
import lailatulCoder.util.Objective;
import lailatulCoder.util.Stack;

public class Mopper extends Robot {
    private Stack<Objective> objectives;

    public Mopper(RobotController rc, int seed) {
        super(rc, seed);
        objectives = new Stack<>(20);
        objectives.push(new Objective(Objective.Type.EXPLORE, null));
    }

    @Override
    public void doSmallTactic() throws GameActionException {
        if (!rc.isActionReady()) return;

        // Check if low on paint -> retreat
        if (rc.getPaint() < rc.getType().paintCapacity * 0.35) {
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

        // Hit lowest HP enemies
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
            return;
        } 
        
        // Mop logic: Use MapState to find adjacent clustered enemy paint and swing there
        MapLocation myLoc = rc.getLocation();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.CENTER) continue;
            MapLocation target = myLoc.add(dir);
            if (rc.canSenseLocation(target)) {
                int cell = MapState.grid[MapState.locToIndex(target)];
                PaintType pt = MapState.getPaintType(cell);
                // Swing at enemy paint
                if (pt == PaintType.ENEMY_PRIMARY || pt == PaintType.ENEMY_SECONDARY) {
                    if (rc.canMopSwing(dir)) {
                        rc.mopSwing(dir);
                        break;
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
            if (type == Communication.MSG_MOPPER_REQUEST) {
                MapLocation targetLoc = Communication.decodeLocation(val);
                if (objectives.isEmpty() || (objectives.peek().type != Objective.Type.MOP_RUIN && objectives.peek().type != Objective.Type.REFILL)) {
                    objectives.push(new Objective(Objective.Type.MOP_RUIN, targetLoc));
                }
            } else if (type == Communication.MSG_ENEMY_SPOTTED) {
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
                MapLocation exploreTarget = new MapLocation(
                    Math.max(0, Math.min(MapState.width - 1, rc.getLocation().x + (int)((rng.nextDouble() * 20) - 10))),
                    Math.max(0, Math.min(MapState.width - 1, rc.getLocation().y + (int)((rng.nextDouble() * 20) - 10)))
                );
                moveDir = Navigation.getNextMove(rc, exploreTarget, true);
                break;
            case DEFEND:
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
                    int distToTower = rc.getLocation().distanceSquaredTo(nearestTower);
                    if (distToTower < 9) {
                        moveDir = Navigation.getNextMove(rc, rc.getLocation().add(nearestTower.directionTo(rc.getLocation())));
                    } else if (distToTower > 16) {
                        moveDir = Navigation.getNextMove(rc, nearestTower);
                    } else {
                        moveDir = Navigation.getNextMove(rc, rc.getLocation().add(nearestTower.directionTo(rc.getLocation()).rotateLeft().rotateLeft()));
                    }
                } else {
                    moveDir = Direction.values()[(int)(rng.nextDouble() * 8)];
                }
                break;
            case FIGHT:
            case REFILL:
            case MOP_RUIN:
                if (current.target != null) {
                    moveDir = Navigation.getNextMove(rc, current.target, false);
                }
                break;
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
            case FIGHT:
                return obj.target != null && rc.canSenseLocation(obj.target) && rc.getLocation().distanceSquaredTo(obj.target) <= 2;
            case MOP_RUIN:
                // Complete if ruin has no enemy paint, or if target is no longer a ruin (maybe fully built? or we just cleared the adjacent enemy paint)
                if (obj.target == null || !rc.canSenseLocation(obj.target)) return false;
                try {
                    int index = MapState.locToIndex(obj.target);
                    if (index >= 0 && index < 3600) {
                        int cell = MapState.grid[index];
                        PaintType ruinPaint = MapState.getPaintType(cell);
                        // Also check adjacent cells ideally, but for now we consider complete if the ruin itself is clean
                        // and no adjacent enemies logic prevents us
                        if (ruinPaint != PaintType.ENEMY_PRIMARY && ruinPaint != PaintType.ENEMY_SECONDARY) {
                            return true;
                        }
                    }
                } catch (Exception e) {}
                // Fallback to checking distance (if we reached it, we'll try to mop it anyway)
                return rc.getLocation().distanceSquaredTo(obj.target) <= 2 && !rc.canMopSwing(rc.getLocation().directionTo(obj.target));
            case EXPLORE:
            case DEFEND:
            default:
                return false;
        }
    }
}
