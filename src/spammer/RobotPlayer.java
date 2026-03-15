package spammer;

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
                int bytecodeUsed = Clock.getBytecodeNum();
                int bytecodeLeft = Clock.getBytecodesLeft();
                if (bytecodeLeft < 1000) {
                    System.out.println("Warning: Low bytecode remaining! Used: " + bytecodeUsed + ", Left: " + bytecodeLeft);
                }
                Clock.yield();
            }
        }
    }
}
