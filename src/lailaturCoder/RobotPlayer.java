package lailaturCoder;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class RobotPlayer {
    private static Robot robot = null;
    private static Tower tower = null;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                UnitType unit = rc.getType();
                switch (unit) {
                    // TODO: Implement child class (extended from Robot) for each type of robot and call the play method here
                    case SOLDIER:
                        robot = (robot == null) ? new Robot(rc) : robot;
                        robot.play();
                        break;
                    case MOPPER:
                        robot = (robot == null) ? new Robot(rc) : robot;
                        robot.play();
                        break;
                    case SPLASHER:
                        robot = (robot == null) ? new Robot(rc) : robot;
                        robot.play();
                        break;
                    default:
                        tower = (tower == null) ? new Tower(rc) : tower;
                        tower.play();
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println("Bytecode Used: " + Clock.getBytecodeNum() + " | Left: " + Clock.getBytecodesLeft() + " | Type: " + rc.getType());
                Clock.yield();
            }
        }
    }
}
