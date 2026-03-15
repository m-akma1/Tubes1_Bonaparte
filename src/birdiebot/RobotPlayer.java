package birdiebot;

import battlecode.common.*;

public class RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                Util.roundNum = rc.getRoundNum();
                Util.myLoc    = rc.getLocation();
                System.out.println("Round " + Util.roundNum + " - Starting turn for " + rc.getType() + " at " + Util.myLoc);

                switch (rc.getType()) {
                    case SOLDIER:  Soldier.runSoldier(rc);  break;
                    case MOPPER:   Mopper.runMopper(rc);   break;
                    case SPLASHER: Splasher.runSplasher(rc); break;
                    default:
                        if (rc.getType().isTowerType()) Tower.runTower(rc);
                        break;
                }
            } catch (GameActionException e) {

                System.out.println("Exception in round " + Util.roundNum + ": " + e.getMessage());
            } catch (Exception e) {

                System.out.println("Unexpected exception in round " + Util.roundNum + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("Finishing turn " + Util.roundNum);
                Clock.yield();
            }
        }
    }
}
