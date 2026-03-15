package lailatulCoder.util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Navigation {
   public static Direction getNextMove(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return Direction.CENTER;
        
        MapLocation current = rc.getLocation();
        if (current.equals(target)) return Direction.CENTER;

        Direction idealDir = current.directionTo(target);
        
        if (rc.canMove(idealDir)) {
            return idealDir;
        }
        
        Direction dirLeft = idealDir.rotateLeft();
        if (rc.canMove(dirLeft)) return dirLeft;
        
        Direction dirRight = idealDir.rotateRight();
        if (rc.canMove(dirRight)) return dirRight;

        dirLeft = dirLeft.rotateLeft();
        if (rc.canMove(dirLeft)) return dirLeft;

        dirRight = dirRight.rotateRight();
        if (rc.canMove(dirRight)) return dirRight;

        return Direction.CENTER;
    }
}
