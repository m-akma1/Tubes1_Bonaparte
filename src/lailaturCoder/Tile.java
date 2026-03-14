package lailaturCoder;

import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;

class Tile {
    public MapInfo info;
    public int recordedAt;
    public int messagePriority;

    public Tile(MapInfo info, int recordedAt, int messagePriority) {
        this.info = info;
        this.recordedAt = recordedAt;
        this.messagePriority = messagePriority;
    }

    public Tile(int byteMessage) {
        int recordedAt = byteMessage & 0b1111111111; byteMessage >>>= 10;
        boolean isWall = boolFromInt(byteMessage & 0b1); byteMessage >>>= 1;
        boolean isRPCenter = boolFromInt(byteMessage & 0b1); byteMessage >>>= 1;
        boolean isPassable = boolFromInt(byteMessage & 0b1); byteMessage >>>= 1;
        boolean hasRuin = boolFromInt(byteMessage & 0b1); byteMessage >>>= 1;
        int paintInt = byteMessage & 0b111; byteMessage >>>= 3;
        int markInt = byteMessage & 0b111; byteMessage >>>= 3;
        int y = byteMessage & 0b111111; byteMessage >>>= 6;
        int x = byteMessage & 0b111111;
        
        MapInfo info = new MapInfo(
            new MapLocation(x, y), 
            isPassable, 
            isWall, 
            paintTypeFromInt(markInt), 
            paintTypeFromInt(paintInt), 
            hasRuin, 
            isRPCenter);

        this.info = info;
        this.recordedAt = recordedAt;
        this.messagePriority = 0;
    }

    public int encode() {
        MapLocation location = info.getMapLocation();
        int x = location.x;
        int y = location.y;
        PaintType mark = info.getMark();
        PaintType paint = info.getPaint();
        boolean hasRuin = info.hasRuin();
        boolean isPassable = info.isPassable();
        boolean isRPCenter = info.isResourcePatternCenter();
        boolean isWall = info.isWall();

        int message = 0;
        message += x; message <<= 6;
        message += y; message <<= 6;
        message += intFromPaintType(mark); message <<= 3;
        message += intFromPaintType(paint); message <<= 3;
        message += intFromBool(hasRuin); message <<= 1;
        message += intFromBool(isPassable); message <<= 1;
        message += intFromBool(isRPCenter); message <<= 1;
        message += intFromBool(isWall); message <<= 1;
        message += recordedAt;

        return message;
    }

    public MapLocation getLocation() {
        return info.getMapLocation();
    }

    public PaintType getMark() {
        return info.getMark();
    }

    public PaintType getPaint() {
        return info.getPaint();
    }

    public boolean hasRuin() {
        return info.hasRuin();
    }

    public boolean isPassable() {
        return info.isPassable();
    }

    public boolean isRPCenter() {
        return info.isResourcePatternCenter();
    }

    public boolean isWall() {
        return info.isWall();
    }

    public int getRecordedAt() {
        return recordedAt;
    }

    public int getMessagePriority() {
        return messagePriority;
    }

    public void updateAll(MapInfo newInfo, int recordedAt, int messagePriority) {
        this.info = newInfo;
        this.recordedAt = recordedAt;
        this.messagePriority = messagePriority;
    }

    public void updateMapInfo(MapInfo newInfo) {
        this.info = newInfo;
    }

    public void updateRecordedAt(int recordedAt) {
        this.recordedAt = recordedAt;
    }

    public void updateMessagePriority(int messagePriority) {
        this.messagePriority = messagePriority;
    }

    public static int intFromPaintType(PaintType p) {
        return switch (p) {
            case ALLY_PRIMARY -> 1;
            case ALLY_SECONDARY -> 2;
            case EMPTY -> 3;
            case ENEMY_PRIMARY -> 4;
            case ENEMY_SECONDARY -> 5;
            default -> 0;
        };
    }

    public static PaintType paintTypeFromInt(int i) {
        return switch (i) {
            case 1 -> PaintType.ALLY_PRIMARY;
            case 2 -> PaintType.ALLY_SECONDARY;
            case 3 -> PaintType.EMPTY;
            case 4 -> PaintType.ENEMY_PRIMARY;
            case 5 -> PaintType.ENEMY_SECONDARY;
            default -> null;
        };
    }

    public static int intFromBool(boolean b) {
        return b ? 1 : 0;
    }

    public static boolean boolFromInt(int i) {
        return i == 1;
    }
}