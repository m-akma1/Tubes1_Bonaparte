package birdiebot;

import battlecode.common.*;


public class RobotPlayer {

    // Msg
    static final int MSG_ENEMY_PAINT    = 1;
    static final int MSG_REQUEST_MOPPER = 2;
    static final int MSG_RUIN_FOUND     = 3;

    // Robot paint
    static final int SOLDIER_MAX  = 200;
    static final int MOPPER_MAX   = 100;
    static final int SPLASHER_MAX = 300;

    // Robot paint %
    static final double LOW_PNT   = 0.30;
    static final double HIGH_PNT  = 0.80;

    // Tower Ratios
    static final int PAINT_PER_DEFENSE = 2;
    static final int PAINT_PER_MONEY   = 3;

    // Global variables
    static int          roundNum;
    static MapLocation  myLoc;

    //Tower building tracking
    static int builtMoney   = 1;
    static int builtPaint   = 1;
    static int builtDefense = 0;

    // Soldier state
    static boolean      retreating     = false;
    static MapLocation  patrolTarget   = null;
    static int          patrolEdge     = -1; // 0=N, 1=E, 2=S, 3=W
    static MapLocation  exploreTarget  = null;
    static int          exploreRefresh = 0;

    static MapLocation mopperTarget = null;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                roundNum = rc.getRoundNum();
                myLoc    = rc.getLocation();
                System.out.println("Round " + roundNum + " - Starting turn for " + rc.getType() + " at " + myLoc);

                switch (rc.getType()) {
                    case SOLDIER:  runSoldier(rc);  break;
                    case MOPPER:   runMopper(rc);   break;
                    case SPLASHER: runSplasher(rc); break;
                    default:
                        if (rc.getType().isTowerType()) runTower(rc);
                        break;
                }
            } catch (GameActionException e) {

                System.out.println("Exception in round " + roundNum + ": " + e.getMessage());
            } catch (Exception e) {

                System.out.println("Unexpected exception in round " + roundNum + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("Finishing turn " + roundNum);
                Clock.yield();
            }
        }
    }

    static int mopperDemand = 0;

    static void runTower(RobotController rc) throws GameActionException {
        for (Message m : rc.readMessages(-1)) {
            int t = getMsgType(m.getBytes());
            if (t == MSG_REQUEST_MOPPER || t == MSG_ENEMY_PAINT) {
                System.out.println("Received Mopper request for enemy paint at (" + 
                        getMsgX(m.getBytes()) + "," + getMsgY(m.getBytes()) + ")");
                mopperDemand++;
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

            else if (mopperDemand >= 2 && paint >= 100 && chips >= 300) {
                if (rc.canBuildRobot(UnitType.MOPPER, spawn)) {
                    mopperDemand--;
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
        if (rc.canUpgradeTower(myLoc)) {
            rc.upgradeTower(myLoc);
            System.out.println("Upgraded tower at " + myLoc);
        }
    }

    static MapLocation findSpawnLoc(RobotController rc) throws GameActionException {
        for (Direction d : Direction.allDirections()) {
            if (d == Direction.CENTER) continue;
            MapLocation loc = myLoc.add(d);
            if (rc.onTheMap(loc) && rc.canBuildRobot(UnitType.SOLDIER, loc)) return loc;
        }
        return null;
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        int paint = rc.getPaint();
        double pnt = (double) paint / SOLDIER_MAX;

        if (pnt < LOW_PNT)  retreating = true;
        if (pnt > HIGH_PNT) retreating = false;

        if (patrolEdge < 0) patrolEdge = roundNum % 4;

        MapInfo[] mapInfos = rc.senseNearbyMapInfos();
        RobotInfo[] robots = rc.senseNearbyRobots();

        notifyTower(rc, mapInfos, robots);
        tryWithdraw(rc, robots);

        if (retreating) {
            MapLocation t = nearestTower(robots);
            if (t != null) moveToward(rc, t);
            else moveRandom(rc);
            return;
        }

        MapLocation ruin = nearestRuin(rc, mapInfos);
        if (ruin != null && myLoc.distanceSquaredTo(ruin) <= 8) {
            buildTower(rc, ruin, mapInfos);
        }

        MapLocation target = getPatrolTarget(rc, mapInfos);
        moveToward(rc, target);

        paintNearby(rc, mapInfos);
    }

    static void notifyTower(RobotController rc, MapInfo[] mapInfos,
                             RobotInfo[] robots) throws GameActionException {
        MapLocation enemyTile = null;
        MapLocation ruinLoc   = null;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy() && enemyTile == null) enemyTile = mi.getMapLocation();
            if (mi.hasRuin()            && ruinLoc   == null) ruinLoc   = mi.getMapLocation();
        }

        for (RobotInfo r : robots) {
            if (r.getTeam() != rc.getTeam() || !r.getType().isTowerType()) continue;
            if (!rc.canSendMessage(r.getLocation())) continue;

            if (enemyTile != null) {
                rc.sendMessage(r.getLocation(),
                        encodeMsg(MSG_REQUEST_MOPPER, enemyTile.x, enemyTile.y));
                        System.out.println("Sent Mopper request for enemy paint at (" + enemyTile.x + "," + enemyTile.y + ")");
                }
            else if (ruinLoc != null) {
                rc.sendMessage(r.getLocation(),
                        encodeMsg(MSG_RUIN_FOUND, ruinLoc.x, ruinLoc.y));
                        System.out.println("Sent Ruin Found message for ruin at (" + ruinLoc.x + "," + ruinLoc.y + ")");
            }
            break;
        }
    }

    static void tryWithdraw(RobotController rc, RobotInfo[] robots) throws GameActionException {
        if (rc.getPaint() >= (int)(SOLDIER_MAX * HIGH_PNT) || !rc.isActionReady()) return;
        for (RobotInfo r : robots) {
            if (r.getTeam() == rc.getTeam() && r.getType().isTowerType()) {
                int need = SOLDIER_MAX - rc.getPaint();
                if (rc.canTransferPaint(r.getLocation(), -need)) {
                    rc.transferPaint(r.getLocation(), -need);
                    return;
                }
            }
        }
    }

    static void buildTower(RobotController rc, MapLocation ruin,
                            MapInfo[] mapInfos) throws GameActionException {
        UnitType tt = chooseTowerType();

        if (rc.canMarkTowerPattern(tt, ruin)) rc.markTowerPattern(tt, ruin);

        if (rc.isActionReady()) {
            for (MapInfo mi : mapInfos) {
                MapLocation loc = mi.getMapLocation();
                if (Math.abs(loc.x - ruin.x) > 2 || Math.abs(loc.y - ruin.y) > 2) continue;
                PaintType marker = mi.getMark();
                PaintType cur    = mi.getPaint();
                boolean needsPrimary   = marker == PaintType.ALLY_PRIMARY   && cur != PaintType.ALLY_PRIMARY;
                boolean needsSecondary = marker == PaintType.ALLY_SECONDARY  && cur != PaintType.ALLY_SECONDARY;
                if ((needsPrimary || needsSecondary) && rc.canAttack(loc)) {
                    rc.attack(loc, needsSecondary);
                    break;
                }
            }
        }

        if (rc.canCompleteTowerPattern(tt, ruin)) {
            rc.completeTowerPattern(tt, ruin);
            switch (tt) {
                case LEVEL_ONE_MONEY_TOWER:   {builtMoney++; System.out.println("Built Money Tower at " + ruin); break;}
                case LEVEL_ONE_DEFENSE_TOWER: {builtDefense++; System.out.println("Built Defense Tower at " + ruin); break;}
                case LEVEL_ONE_PAINT_TOWER:   {builtPaint++; System.out.println("Built Paint Tower at " + ruin); break;}
            }
            System.out.println("Tower numbers - Money: " + builtMoney + ", Defense: " + builtDefense + ", Paint: " + builtPaint);
        }
    }

    static UnitType chooseTowerType() {
        if (builtDefense * PAINT_PER_DEFENSE <= builtPaint) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        if (builtMoney * PAINT_PER_MONEY <= builtPaint) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    static MapLocation getPatrolTarget(RobotController rc, MapInfo[] mapInfos) {
        MapLocation unpainted = nearestUnpainted(mapInfos);
        if (unpainted != null) return unpainted;

        int w = rc.getMapWidth(), h = rc.getMapHeight();
        if (patrolTarget == null || myLoc.distanceSquaredTo(patrolTarget) <= 2) {
            switch (patrolEdge) {
                case 0: patrolTarget = new MapLocation(
                            (int)(Math.random() * w), h - 1); break;
                case 1: patrolTarget = new MapLocation(
                            w - 1, (int)(Math.random() * h)); break;
                case 2: patrolTarget = new MapLocation(
                            (int)(Math.random() * w), 0); break;
                default: patrolTarget = new MapLocation(
                            0, (int)(Math.random() * h)); break;
            }
        }
        return patrolTarget;
    }

    static MapLocation nearestUnpainted(MapInfo[] mapInfos) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint() == PaintType.EMPTY && mi.isPassable()) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    static MapLocation nearestRuin(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.hasRuin() && !rc.isLocationOccupied(mi.getMapLocation())) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    static MapLocation nearestTower(RobotInfo[] robots) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getType().isTowerType()) {
                int d = myLoc.distanceSquaredTo(r.getLocation());
                if (d < bd) { bd = d; best = r.getLocation(); }
            }
        }
        return best;
    }

    static void paintNearby(RobotController rc, MapInfo[] mapInfos) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint() == PaintType.EMPTY && mi.isPassable() && rc.canAttack(mi.getMapLocation())) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        if (best != null) rc.attack(best);
    }

    static void runMopper(RobotController rc) throws GameActionException {
        MapInfo[]   mapInfos = rc.senseNearbyMapInfos();
        RobotInfo[] robots   = rc.senseNearbyRobots();

        if (rc.isActionReady()) aggressiveSwing(rc, robots);
        if (rc.isActionReady()) mopNearestEnemy(rc, mapInfos);

        givePaintToAllies(rc, robots);

        MapLocation target = deepestEnemyTarget(rc, mapInfos);
        if (target != null) { mopperTarget = target; moveToward(rc, target); }
        else if (mopperTarget != null && roundNum % 5 != 0) moveToward(rc, mopperTarget);
        else { mopperTarget = null; moveRandom(rc); }
    }

    static void aggressiveSwing(RobotController rc, RobotInfo[] robots)
            throws GameActionException {
        Direction[] card = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int[] counts = new int[4];
        for (RobotInfo r : robots) {
            if (r.getTeam() == rc.getTeam()) continue;
            for (int i = 0; i < 4; i++) {
                MapLocation s1 = myLoc.add(card[i]);
                MapLocation s2 = s1.add(card[i]);
                if (r.getLocation().equals(s1) || r.getLocation().equals(s2)) counts[i]++;
            }
        }
        int best = -1, bc = 0;
        for (int i = 0; i < 4; i++) if (counts[i] > bc) { bc = counts[i]; best = i; }
        if (best >= 0 && bc >= 1 && rc.canMopSwing(card[best])) rc.mopSwing(card[best]);
    }

    static void mopNearestEnemy(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy() && rc.canAttack(mi.getMapLocation())) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        if (best != null) rc.attack(best);
    }

    static void givePaintToAllies(RobotController rc, RobotInfo[] robots)
            throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() <= 20) return;
        for (RobotInfo r : robots) {
            if (r.getTeam() != rc.getTeam() || r.getType() != UnitType.SOLDIER) continue;
            if (r.getPaintAmount() < SOLDIER_MAX * 0.40) {
                int give = Math.min(rc.getPaint() - 15, SOLDIER_MAX - r.getPaintAmount());
                if (give > 0 && rc.canTransferPaint(r.getLocation(), give)) {
                    rc.transferPaint(r.getLocation(), give);
                    return;
                }
            }
        }
    }

    static MapLocation deepestEnemyTarget(RobotController rc, MapInfo[] mapInfos) {
        boolean teamA = (rc.getTeam() == Team.A);
        MapLocation anchor = teamA
                ? new MapLocation(0, 0)
                : new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() - 1);

        MapLocation best = null;
        int bd = -1;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy()) {
                int d = anchor.distanceSquaredTo(mi.getMapLocation());
                if (d > bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] mapInfos = rc.senseNearbyMapInfos();

        if (rc.isActionReady()) {
            MapLocation best = bestSplashTarget(rc, mapInfos);
            if (best != null && rc.canAttack(best)) rc.attack(best);
        }

        MapLocation enemy = nearestEnemyPaint(mapInfos);
        if (enemy != null) moveToward(rc, enemy);
        else {
            MapLocation un = nearestUnpainted(mapInfos);
            if (un != null) moveToward(rc, un);
            else            moveRandom(rc);
        }
    }

    static MapLocation bestSplashTarget(RobotController rc, MapInfo[] mapInfos)
            throws GameActionException {
        MapLocation best = null;
        int bs = 0;
        for (MapInfo c : mapInfos) {
            if (!rc.canAttack(c.getMapLocation())) continue;
            int score = 0;
            for (MapInfo inner : mapInfos) {
                if (c.getMapLocation().distanceSquaredTo(inner.getMapLocation()) <= 4) {
                    if (inner.getPaint() == PaintType.EMPTY) score += 2;
                    else if (inner.getPaint().isEnemy())      score += 3;
                }
            }
            if (score > bs) { bs = score; best = c.getMapLocation(); }
        }
        return best;
    }

    static MapLocation nearestEnemyPaint(MapInfo[] mapInfos) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo mi : mapInfos) {
            if (mi.getPaint().isEnemy()) {
                int d = myLoc.distanceSquaredTo(mi.getMapLocation());
                if (d < bd) { bd = d; best = mi.getMapLocation(); }
            }
        }
        return best;
    }

    static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null || myLoc.equals(target)) return;
        Direction dir = myLoc.directionTo(target);
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(dir)) { rc.move(dir); return; }
            dir = dir.rotateRight();
        }
    }

    static void moveRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction[] dirs = Direction.allDirections();
        int start = (int)(Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            Direction d = dirs[(start + i) % 8];
            if (d != Direction.CENTER && rc.canMove(d)) { rc.move(d); return; }
        }
    }

    static int encodeMsg(int type, int x, int y) {
        return (type << 28) | (x << 22) | (y << 16);
    }

    static int getMsgType(int msg) { return (msg >>> 28) & 0xF; }

    static int getMsgX(int msg) { return (msg >>> 22) & 0x3F; }

    static int getMsgY(int msg) { return (msg >>> 16) & 0x3F; }
}
