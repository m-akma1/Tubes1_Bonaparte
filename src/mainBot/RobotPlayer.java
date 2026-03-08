package mainBot;

import battlecode.common.RobotController;
import battlecode.common.GameActionException;
import battlecode.common.Clock;

public class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                switch (rc.getType()) {
                    default: 
                    break;
                }
                
                System.out.println(
                    String.format(
                        "Using %d bytecodes, %d left", 
                        Clock.getBytecodeNum(), 
                        Clock.getBytecodesLeft()
                    )
                );
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
