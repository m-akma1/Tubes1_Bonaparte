package birdiebot;

import battlecode.common.*;

public class Tower {
    public static void runTower(RobotController rc) throws GameActionException {
        for (Message m : rc.readMessages(-1)) {
            int t = Util.getMsgType(m.getBytes());
            if (t == Util.MSG_REQUEST_MOPPER || t == Util.MSG_ENEMY_PAINT) {
                System.out.println("Received Mopper request for enemy paint at (" + 
                        Util.getMsgX(m.getBytes()) + "," + Util.getMsgY(m.getBytes()) + ")");
                Util.mopperDemand++;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int hits = 0;
        for (RobotInfo e : enemies) {
            if (rc.canAttack(e.getLocation())) {
                rc.attack(e.getLocation());
                System.out.println("Attacked enemy robot at " + e.getLocation());
                hits++;
                if (hits >= 2 || !rc.isActionReady()) break;
            }
        }

        if (rc.isActionReady()) {
            MapLocation spawn = findSpawnLoc(rc);
            
            int paint = rc.getPaint();
            int chips = rc.getChips();
            
            if (spawn == null) ;

            else if (Util.mopperDemand >= 2 && paint >= 100 && chips >= 300) {
                if (rc.canBuildRobot(UnitType.MOPPER, spawn)) {
                    Util.mopperDemand--;
                    rc.buildRobot(UnitType.MOPPER, spawn);
                    System.out.println("Spawned Mopper at " + spawn);
                }
            }

            else if (chips > 1500 && paint >= 300) {
                if (rc.canBuildRobot(UnitType.SPLASHER, spawn)) {
                    rc.buildRobot(UnitType.SPLASHER, spawn);
                    System.out.println("Spawned Splasher at " + spawn);
                }
            }

            else if (paint >= 200 && chips >= 250) {
                if (rc.canBuildRobot(UnitType.SOLDIER, spawn)) {
                    rc.buildRobot(UnitType.SOLDIER, spawn);
                    System.out.println("Spawned Soldier at " + spawn);
                }
            }
        }
        if (rc.canUpgradeTower(Util.myLoc)) {
            rc.upgradeTower(Util.myLoc);
            System.out.println("Upgraded tower at " + Util.myLoc);
        }
    }

    public static MapLocation findSpawnLoc(RobotController rc) throws GameActionException {
        for (Direction d : Direction.allDirections()) {
            if (d == Direction.CENTER) continue;
            MapLocation loc = Util.myLoc.add(d);
            if (rc.onTheMap(loc) && rc.canBuildRobot(UnitType.SOLDIER, loc)) return loc;
        }
        return null;
    }
}