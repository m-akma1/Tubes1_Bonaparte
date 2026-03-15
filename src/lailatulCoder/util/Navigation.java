package lailatulCoder.util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;

public class Navigation {
    
    private static int getTileCost(RobotController rc, MapLocation loc, boolean isExploring) throws GameActionException {
        // Use MapState to get paint type to save bytecode
        int index = MapState.locToIndex(loc);
        if (index < 0 || index >= MapState.MAX_MAP_AREA) return 1000;
        
        int cell = MapState.grid[index];
        
        // High penalty for being adjacent to allies (cramped penalty)
        if (MapState.hasRobot(cell) && MapState.isAlly(cell)) {
            return 50; 
        }

        PaintType pt = MapState.getPaintType(cell);
        if (pt == PaintType.ALLY_PRIMARY || pt == PaintType.ALLY_SECONDARY) {
            return isExploring ? 3 : 1; // Best normally, but less preferred if exploring
        } else if (pt == PaintType.EMPTY) {
            return isExploring ? 1 : 5; // Penalty for empty, unless exploring (actively seek!)
        } else {
            return 10; // High penalty for enemy paint
        }
    }

   public static Direction getNextMove(RobotController rc, MapLocation target) throws GameActionException {
        return getNextMove(rc, target, false);
   }

   public static Direction getNextMove(RobotController rc, MapLocation target, boolean isExploring) throws GameActionException {
        if (target == null) return Direction.CENTER;
        
        MapLocation current = rc.getLocation();
        if (current.equals(target)) return Direction.CENTER;

        Direction idealDir = current.directionTo(target);
        
        Direction bestDir = Direction.CENTER;
        int minCost = Integer.MAX_VALUE;

        // Check ideal direction and its rotations
        Direction[] dirsToTry = {
            idealDir,
            idealDir.rotateLeft(),
            idealDir.rotateRight(),
            idealDir.rotateLeft().rotateLeft(),
            idealDir.rotateRight().rotateRight()
        };

        for (Direction dir : dirsToTry) {
            if (rc.canMove(dir)) {
                int cost = getTileCost(rc, current.add(dir), isExploring);
                
// Add heavy deviation penalty to ensure we move towards target, making paint just a tie-breaker
                int deviationPenalty = 0;
                if (dir != idealDir) {
                    if (dir == idealDir.rotateLeft() || dir == idealDir.rotateRight())
                        deviationPenalty = 8;
                    else
                        deviationPenalty = 16;
                }
                
                if (cost + deviationPenalty < minCost) {
                    minCost = cost + deviationPenalty;
                    bestDir = dir;
                }
            }
        }

        return bestDir;
    }
}
