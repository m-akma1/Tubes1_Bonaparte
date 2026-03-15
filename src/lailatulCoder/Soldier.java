package lailatulCoder;

import battlecode.common.*;

public class Soldier extends Robot {
    public Soldier(RobotController rc, int seed) {
        super(rc, seed);
    }

    @Override
    public void doSmallTactic() throws GameActionException {
        // Hit lowest HP enemies, marking patterns
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo lowestHpEnemy = null;
        for (RobotInfo enemy : enemies) {
            if (lowestHpEnemy == null || enemy.getHealth() < lowestHpEnemy.getHealth()) {
                lowestHpEnemy = enemy;
            }
        }
        
        if (lowestHpEnemy != null && rc.canAttack(lowestHpEnemy.getLocation())) {
            rc.attack(lowestHpEnemy.getLocation());
        } else {
            // mark patterns or paint
            MapLocation myLoc = rc.getLocation();
            for (Direction dir : Direction.values()) {
                MapLocation target = myLoc.add(dir);
                if (rc.canAttack(target)) {
                    rc.attack(target);
                    break;
                }
            }
        }
    }

    @Override
    public void doBigStrategy() throws GameActionException {
        // routing
        Direction dir = Direction.values()[(int)(Math.random() * 8)];
        if (rc.canMove(dir) && rc.isActionReady()) {
            rc.move(dir);
        }
    }
}
