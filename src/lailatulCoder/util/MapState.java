package lailatulCoder.util;

import battlecode.common.*;

public class MapState {
    public static final int MAX_MAP_AREA = 3600;
    public static int width = 60;
    public static int[] grid = new int[MAX_MAP_AREA];
    
    // Track only updated indices to avoid 3600 loops
    public static int[] activeRobotIndices = new int[200];
    public static int numActiveRobots = 0;

    /**
     * Layout from LSB
     * [0] isWall
     * [1] isRuin
     * [2-4] paint type
     * [5-7] mark type
     * [8] hasRobot
     * [9] isAlly
     * [10-11] robot type
     * [12-19] paint amount (cap at 255)
     */

    public static int locToIndex(int x, int y) {
        return y * width + x;
    }
    
    public static int locToIndex(MapLocation loc) {
        return loc.y * width + loc.x;
    }
    
    public static boolean isWall(int cell) {
        return (cell & 1) != 0;
    }

    public static int setWall(int cell, boolean isWall) {
        if (isWall) {
            cell |= 1;
        } else {
            cell &= ~1;
        }
        return cell;
    }

    public static boolean isRuin(int cell) {
        return (cell & 2) != 0;
    }

    public static int setRuin(int cell, boolean isRuin) {
        if (isRuin) {
            cell |= 2;
        } else {
            cell &= ~2;
        }
        return cell;
    }

    /**
     * 000 = empty
     * 010 = ally primary
     * 011 = ally secondary
     * 100 = enemy primary
     * 101 = enemy secondary
     * @param cell
     * @return
     */
    public static PaintType getPaintType(int cell) {
        int paintType = (cell >> 2) & 0x7;
        return switch (paintType) {
            case 0 -> PaintType.EMPTY;
            case 2 -> PaintType.ALLY_PRIMARY;
            case 3 -> PaintType.ALLY_SECONDARY;
            case 4 -> PaintType.ENEMY_PRIMARY;
            case 5 -> PaintType.ENEMY_SECONDARY;
            default -> PaintType.EMPTY;
        };
    }

    public static int setPaintType(int cell, PaintType paintType) {
        cell &= ~(0x7 << 2); // Clear existing paint type
        int paintValue = switch (paintType) {
            case EMPTY -> 0;
            case ALLY_PRIMARY -> 2;
            case ALLY_SECONDARY -> 3;
            case ENEMY_PRIMARY -> 4;
            case ENEMY_SECONDARY -> 5;
        };
        cell |= (paintValue & 0x7) << 2; // Set new paint type
        return cell;
    }

    public static PaintType getMarkType(int cell) {
        int markType = (cell >> 5) & 0x7;
        return switch (markType) {
            case 0 -> PaintType.EMPTY;
            case 2 -> PaintType.ALLY_PRIMARY;
            case 3 -> PaintType.ALLY_SECONDARY;
            case 4 -> PaintType.ENEMY_PRIMARY;
            case 5 -> PaintType.ENEMY_SECONDARY;
            default -> PaintType.EMPTY;
        };

    }

    public static int setMarkType(int cell, PaintType markType) {
        cell &= ~(0x7 << 5); // Clear existing mark type
        int markValue = switch (markType) {
            case EMPTY -> 0;
            case ALLY_PRIMARY -> 2;
            case ALLY_SECONDARY -> 3;
            case ENEMY_PRIMARY -> 4;
            case ENEMY_SECONDARY -> 5;
        };
        cell |= (markValue & 0x7) << 5; // Set new mark type
        return cell;
    }

    public static boolean hasRobot(int cell) {
        return (cell & 0x100) != 0;
    }

    public static int setHasRobot(int cell, boolean hasRobot) {
        if (hasRobot) {
            cell |= 0x100;
        } else {
            cell &= ~0x100;
        }
        return cell;
    }

    public static boolean isAlly(int cell) {
        return (cell & 0x200) != 0;
    }

    public static int setAlly(int cell, boolean isAlly) {
        if (isAlly) {
            cell |= 0x200;
        } else {
            cell &= ~0x200;
        }
        return cell;
    }

    /**
     * 00 = soldier
     * 01 = splasher
     * 10 = mopper
     * 11 = tower (null)
     */
    public static UnitType getRobotType(int cell) {
        int robotType = (cell >> 9) & 0x3;
        return switch (robotType) {
            case 0 -> UnitType.SOLDIER;
            case 1 -> UnitType.SPLASHER;
            case 2 -> UnitType.MOPPER;
            default -> null;
        };
    }

    public static int setRobotType(int cell, UnitType robotType) {
        cell &= ~(0x3 << 9); // Clear existing robot type
        int robotValue = switch (robotType) {
            case SOLDIER -> 0;
            case SPLASHER -> 1;
            case MOPPER -> 2;
            default -> 0;
        };
        cell |= (robotValue & 0x3) << 9; // Set new robot type
        return cell;
    }

    public static int getPaintAmount(int cell) {
        return (cell >> 12) & 0xFF;
    }

    public static int setPaintAmount(int cell, int amount) {
        amount = Math.max(0, Math.min(255, amount)); // Clamp to 0-255
        cell &= ~(0xFF << 12); // Clear existing paint amount
        cell |= (amount & 0xFF) << 12; // Set new paint amount
        return cell;
    }

    public static void resetCell(int cell) {
        cell = 0;
    }

    public static void updateMap(RobotController rc) throws GameActionException {
        // Clear ONLY old robot positions based on last turn's count
        for (int i = 0; i < numActiveRobots; i++) {
            grid[activeRobotIndices[i]] &= ~0x100;
        }
        numActiveRobots = 0;

        MapInfo[] sensed = rc.senseNearbyMapInfos();
        for (MapInfo info : sensed) {
            int index = locToIndex(info.getMapLocation());
            if (index >= 0 && index < grid.length) {
                int cell = grid[index];
                cell = setWall(cell, info.isWall());
                cell = setRuin(cell, info.hasRuin());
                cell = setPaintType(cell, info.getPaint());
                cell = setMarkType(cell, info.getMark());
                grid[index] = cell;
            }
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        for (RobotInfo robot : robots) {
            int index = locToIndex(robot.getLocation());
            if (index >= 0 && index < grid.length) {
                int cell = grid[index];
                cell = setHasRobot(cell, true);
                cell = setAlly(cell, robot.getTeam() == rc.getTeam());
                cell = setRobotType(cell, robot.getType());
                grid[index] = cell;
                
                // Track this index so we can clear it effectively next turn
                if (numActiveRobots < activeRobotIndices.length) {
                    activeRobotIndices[numActiveRobots++] = index;
                }
            }
        }
    }
}
