package lailatulCoder;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class RobotPlayer {
    private static Robot robot;
    private static Random rng = new Random(2211);

    public static void run(RobotController rc) throws GameActionException {
        try {
            robot = switch (rc.getType()) {
                case SOLDIER -> new Soldier(rc, rng.nextInt());
                case MOPPER -> new Mopper(rc, rng.nextInt());
                case SPLASHER -> new Splasher(rc, rng.nextInt());
                default -> new Tower(rc, rng.nextInt());
            };
        } catch (Exception e) {
            e.printStackTrace();
            rc.disintegrate();
        }

        while (true) {
            try {
                robot.play();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                String robotType = rc.getType().toString();
                int roundNum = robot.getRoundNum();
                int bytecodeUsed = Clock.getBytecodeNum();
                int bytecodeLeft = Clock.getBytecodesLeft();
                System.out.println("Robot: " + robotType + ", Round: " + roundNum + ", Bytecode Used: " + bytecodeUsed + ", Left: " + bytecodeLeft);
                Clock.yield();
            }
        }
    }
}
