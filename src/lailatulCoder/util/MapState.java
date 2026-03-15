package lailatulCoder.util;

import battlecode.common.*;

public class MapState {
    public static final int MAX_MAP_AREA = 3600;
    public static int width = 60;
    public static int[] grid = new int[MAX_MAP_AREA];

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

    public static void setWall(int cell, boolean isWall) {
        if (isWall) {
            cell |= 1;
        } else {
            cell &= ~1;
        }
    }

    public static boolean isRuin(int cell) {
        return (cell & 2) != 0;
    }

    public static void setRuin(int cell, boolean isRuin) {
        if (isRuin) {
            cell |= 2;
        } else {
            cell &= ~2;
        }
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

    public static void setPaintType(int cell, PaintType paintType) {
        cell &= ~(0x7 << 2); // Clear existing paint type
        int paintValue = switch (paintType) {
            case EMPTY -> 0;
            case ALLY_PRIMARY -> 2;
            case ALLY_SECONDARY -> 3;
            case ENEMY_PRIMARY -> 4;
            case ENEMY_SECONDARY -> 5;
        };
        cell |= (paintValue & 0x7) << 2; // Set new paint type
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

    public static void setMarkType(int cell, PaintType markType) {
        cell &= ~(0x7 << 5); // Clear existing mark type
        int markValue = switch (markType) {
            case EMPTY -> 0;
            case ALLY_PRIMARY -> 2;
            case ALLY_SECONDARY -> 3;
            case ENEMY_PRIMARY -> 4;
            case ENEMY_SECONDARY -> 5;
        };
        cell |= (markValue & 0x7) << 5; // Set new mark type
    }

    public static boolean hasRobot(int cell) {
        return (cell & 0x100) != 0;
    }

    public static void setHasRobot(int cell, boolean hasRobot) {
        if (hasRobot) {
            cell |= 0x100;
        } else {
            cell &= ~0x100;
        }
    }

    public static boolean isAlly(int cell) {
        return (cell & 0x200) != 0;
    }

    public static void setAlly(int cell, boolean isAlly) {
        if (isAlly) {
            cell |= 0x200;
        } else {
            cell &= ~0x200;
        }
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

    public static void setRobotType(int cell, UnitType robotType) {
        cell &= ~(0x3 << 9); // Clear existing robot type
        int robotValue = switch (robotType) {
            case SOLDIER -> 0;
            case SPLASHER -> 1;
            case MOPPER -> 2;
            default -> 0;
        };
        cell |= (robotValue & 0x3) << 9; // Set new robot type
    }

    public static int getPaintAmount(int cell) {
        return (cell >> 12) & 0xFF;
    }

    public static void setPaintAmount(int cell, int amount) {
        amount = Math.max(0, Math.min(255, amount)); // Clamp to 0-255
        cell &= ~(0xFF << 12); // Clear existing paint amount
        cell |= (amount & 0xFF) << 12; // Set new paint amount
    }

    public static void resetCell(int cell) {
        cell = 0;
    }

    public static void updateMap(RobotController rc) throws GameActionException {
        MapInfo[] sensed = rc.senseNearbyMapInfos();
        for (MapInfo info : sensed) {
            int index = locToIndex(info.getMapLocation());
            if (index >= 0 && index < grid.length) {
                int cell = grid[index];
                setWall(cell, info.isWall());
                setRuin(cell, info.hasRuin());
                setPaintType(cell, info.getPaint());
                setMarkType(cell, info.getMark());
                grid[index] = cell;
            }
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        for (RobotInfo robot : robots) {
            int index = locToIndex(robot.getLocation());
            if (index >= 0 && index < grid.length) {
                int cell = grid[index];
                setHasRobot(cell, true);
                setAlly(cell, robot.getTeam() == rc.getTeam());
                setRobotType(cell, robot.getType());
                grid[index] = cell;
            }
        }
    }
}
