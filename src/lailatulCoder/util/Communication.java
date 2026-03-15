package lailatulCoder.util;

import battlecode.common.MapLocation;

public class Communication {
    // Format: 4 bits message type | 12 bits X | 12 bits Y | 4 for future use
    public static int encodeMessage(int messageType, MapLocation loc) {
        int encoded = (messageType & 0xF) << 28;
        if (loc != null) {
            encoded |= (loc.x & 0xFFF) << 16;
            encoded |= (loc.y & 0xFFF) << 4;
        }
        return encoded;
    }

    public static int decodeMessageType(int message) {
        return (message >>> 28) & 0xF;
    }

    public static MapLocation decodeLocation(int message) {
        int x = (message >>> 16) & 0xFFF;
        int y = (message >>> 4) & 0xFFF;
        return new MapLocation(x, y);
    }
}
