package lailatulCoder;

import battlecode.common.*;
import lailatulCoder.util.Communication;

public class Tower extends Robot {
    private int spawnCounter = 0;

    public Tower(RobotController rc, int seed) {
        super(rc, seed);
    }
    
    @Override
    public void play() throws GameActionException {
        roundNum++;
        doSmallTactic();
        doBigStrategy();
    }

    @Override
    public void doSmallTactic() throws GameActionException {
        // Tower logic: Refill adjacent allies and attack nearby enemies
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.getPaintAmount() / ally.getType().paintCapacity < 0.5) {
                if (rc.canTransferPaint(ally.location, ally.getType().paintCapacity - ally.getPaintAmount())) {
                    rc.transferPaint(ally.location, ally.getType().paintCapacity - ally.getPaintAmount());
                }
            }
        }

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo lowestHpEnemy = null;
        for (RobotInfo enemy : nearbyEnemies) {
            if (lowestHpEnemy == null || enemy.health < lowestHpEnemy.health) {
                lowestHpEnemy = enemy;
            }
        }
        
        if (lowestHpEnemy != null) {
            if (rc.canAttack(lowestHpEnemy.location)) {
                rc.attack(lowestHpEnemy.location);
            }
            // Broadcast spotted enemies
            broadcastEnemy(lowestHpEnemy.location);
        }
    }

    private int getBuildCost(UnitType type) {
        if (type == UnitType.MOPPER) return 1000;
        if (type == UnitType.SPLASHER) return 500;
        return 250; // SOLDIER
    }

    @Override
    public void doBigStrategy() throws GameActionException {
        // Commnicaton
        Message[] messages = rc.readMessages(-1);
        int messagesSent = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        
        for (Message m : messages) {
            if (messagesSent >= 20) break;
            
            int val = m.getBytes();
            int type = Communication.decodeMessageType(val);
            
            // Re-broadcast important messages to units that might not have heard it
            if (type == Communication.MSG_MOPPER_REQUEST) {
                // Find a Mopper to send this to
                for (RobotInfo ally : allies) {
                    if (ally.type == UnitType.MOPPER && rc.canSendMessage(ally.location, val)) {
                        rc.sendMessage(ally.location, val);
                        messagesSent++;
                        if (messagesSent >= 20) break;
                    }
                }
            } else if (type == Communication.MSG_ENEMY_SPOTTED) {
                // Find a Soldier or Splasher
                for (RobotInfo ally : allies) {
                    if ((ally.type == UnitType.SOLDIER || ally.type == UnitType.SPLASHER) && rc.canSendMessage(ally.location, val)) {
                        rc.sendMessage(ally.location, val);
                        messagesSent++;
                        if (messagesSent >= 20) break;
                    }
                }
            }
        }

        // Spawning Strategy
        // Cycle: roughly 6 Soldiers, 1 Splashers, 3 Moppers in 10 spawns
        UnitType nextSpawn;
        int cycle = spawnCounter % 10;
        if (cycle % 3 == 0 && cycle != 0) {
            nextSpawn = UnitType.MOPPER;
        } else if (cycle == 7) {
            nextSpawn = UnitType.SPLASHER;
        } else {
            nextSpawn = UnitType.SOLDIER;
        }

        // Only block tower spawn if we don't have enough chips. 
        if (rc.getChips() >= getBuildCost(nextSpawn)) {
            if (trySpawn(nextSpawn)) {
                spawnCounter++; // Successfully spawned, move to the next in cycle
            }
        }
    }
    
    private boolean trySpawn(UnitType type) throws GameActionException {
        Direction[] dirs = Direction.values();
        for (Direction dir : dirs) {
            if (rc.canBuildRobot(type, rc.getLocation().add(dir))) {
                rc.buildRobot(type, rc.getLocation().add(dir));
                return true;
            }
        }
        return false;
    }
}
