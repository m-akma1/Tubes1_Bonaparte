package alternative_bots_1;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

/**
 * Greedy strategy:
 *   - Soldiers  : greedily paint the nearest unpainted tile; upgrade towers opportunistically
 *   - Splashers : greedily target the densest cluster of unpainted tiles (AoE value)
 *   - Moppers   : greedily remove the nearest enemy-painted tile, support refueling
 *   - Tower     : greedily spawn based on resource ratio
 */
public class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            Util.turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:  Soldier.runSoldier(rc);  break;
                    case MOPPER:   Mopper.runMopper(rc);   break;
                    case SPLASHER: Splasher.runSplasher(rc); break;
                    default:       Tower.runTower(rc);    break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
