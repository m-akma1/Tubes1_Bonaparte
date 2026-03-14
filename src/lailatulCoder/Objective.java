package lailatulCoder;

import battlecode.common.MapLocation;

class Objective {
    public enum Type {
        BUILDING,
        EXPLORING,
        RETREATING,
        FIGHTING,
        DEFENDING
    }

    MapLocation target;
    Type objective;

    public Objective(MapLocation target, Type objective) {
        this.target = target;
        this.objective = objective;
    }

    public Objective(int byteMessage) {
        byteMessage >>>= 22; // Remove unused bits
        int objectiveInt = byteMessage & 0b111; byteMessage >>>= 3;
        int y = byteMessage & 0b111111; byteMessage >>>= 6;
        int x = byteMessage & 0b111111;

        this.target = new MapLocation(x, y);
        this.objective = objectiveFromInt(objectiveInt);
    }

    public int encode() {
        int x = target.x;
        int y = target.y;
        int o = intFromObjective(objective);

        int message = 0;
        message += x; message <<= 6;
        message += y; message <<= 6;
        message += 0b111; message <<= 3; // Indictate this is an objective message
        message += o; message <<= 22;

        return message;
    }

    public MapLocation getTarget() {
        return target;
    }

    public Type getObjective() {
        return objective;
    }

    public static int intFromObjective(Type t) {
        return switch (t) {
            case BUILDING -> 1;
            case EXPLORING -> 2;
            case RETREATING -> 3;
            case FIGHTING -> 4;
            case DEFENDING -> 5;
            default -> 0;
        };
    }

    public static Type objectiveFromInt(int i) {
        return switch (i) {
            case 1 -> Type.BUILDING;
            case 2 -> Type.EXPLORING;
            case 3 -> Type.RETREATING;
            case 4 -> Type.FIGHTING;
            case 5 -> Type.DEFENDING;
            default -> null;
        };
    }
}
