package lailatulCoder.util;

import battlecode.common.MapLocation;

public class Objective {
    public enum Type {
        BUILD,
        REFILL,
        EXPLORE,
        DEFEND,
        FIGHT
    }

    public Type type;
    public MapLocation target;

    public Objective(Type type, MapLocation target) {
        this.type = type;
        this.target = target;
    }
}
