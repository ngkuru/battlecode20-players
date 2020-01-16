package sprinttournament;
import battlecode.common.*;
import java.lang.Math;
import java.lang.reflect.Array;
import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Random rand = new Random();

    static Direction[] directions = {
            Direction.EAST,
            Direction.WEST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.NORTHEAST,
            Direction.SOUTHWEST,
            Direction.SOUTHEAST,
            Direction.NORTHWEST};

    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    static int numMiners = 0;
    static int numDesignSchools = 0;

    static MapLocation hqLoc;
    static MapLocation target;
    static ArrayList<MapLocation> soupLocations = new ArrayList<MapLocation>();

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
                findHQ();
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case REFINERY:           runRefinery();          break;
                    case VAPORATOR:          runVaporator();         break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
                    case NET_GUN:            runNetGun();            break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Finds the HQ either by looking around or checking the blockchain.
     *
     * @throws GameActionException
     */
    static void findHQ() throws GameActionException {
        // If we already know where the HQ is, return
        if (hqLoc != null) {
            return;
        }

        // Check around to see if we can see the HQ
        RobotInfo[] robots = rc.senseNearbyRobots();
        for (RobotInfo robot : robots) {
            if (robot.type == RobotType.HQ && robot.team == rc.getTeam()) {
                hqLoc = robot.location;
                return;
            }
        }

        getHqLocFromBlockchain();
    }

    static void runHQ() throws GameActionException {
        if (turnCount == 1) {
            sendHqLoc(rc.getLocation());
        }

        // Create 10 miners in the beginning of the game
        if (numMiners < 10) {
            for (Direction dir : directions) {
                if (tryBuild(RobotType.MINER, dir)) {
                    numMiners++;
                }
            }
        }

        // TODO: Make the HQ shoot drones
    }

    static void runMiner() throws GameActionException {
        // Get status update on soup locations from blockchain
        updateSoupLocations();
        // If on a tile with soup info but no soup, inform others
        System.out.println(rc.getLocation());
        System.out.println(soupLocations);
        if (soupLocations.contains(rc.getLocation()) && rc.senseSoup(rc.getLocation()) == 0){
            broadcastLocation(rc.getLocation(), "no soup", 2);
        }
        updateUnitCounts();

        // If target is no longer in soupLocations, disable target
        // TODO: Else if can see target but no soup - disable but without clash and multibroadcasting
        if (target != null && !soupLocations.contains(target)) {
            target = null;
        }

        // Check if we see soup
        // MapLocation soupLoc = seeSoup(5);
        MapLocation soupLoc = seeSoup(10);
        // If we see soup, set target to soup
        // TODO: Create a blacklist for soup locations that are unreachable
        // Else if we know where soup is, set target to soup
        if (soupLoc != null) {
            target = soupLoc;
        } else if (target == null && soupLocations.size() > 0) {
            target = soupLocations.get(rand.nextInt(soupLocations.size()));
        }

        // Try to mine soup, if we are able to, reset target
        for (Direction dir : directions) {
            if (tryMine(dir)) {
                target = null;
                // If soup location is unknown, share it with others
                if (!soupLocations.contains(rc.getLocation().add(dir))) {
                    broadcastLocation(rc.getLocation().add(dir), "soup", 1);
                }
            }
        }

        if (numDesignSchools < 3){
            if (tryBuild(RobotType.DESIGN_SCHOOL, randomDirection()))
                System.out.println("created a design school");
        }

        // If inventory is full, set target to HQ
        if (rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
            target = hqLoc;
        }

        // Try to refine soup, reset if successful
        for (Direction dir : directions) {
            if (tryRefine(dir)) {
                target = null;
            }
        }

        // If there is a target, try to go to the target, if fails or no target, move randomly
        System.out.println("target " + target);
        if (target != null) {
            rc.setIndicatorLine(rc.getLocation(), target, 255, 255, 0);
            goTo(target);
        }
        goTo(randomDirection());
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        if (!broadcastedCreation) {
            broadcastDesignSchoolCreation(rc.getLocation());
            broadcastedCreation = true;
        }
        for (Direction dir : directions) {
            tryBuild(RobotType.LANDSCAPER, dir);
        }
    }

    static void runFulfillmentCenter() throws GameActionException {

    }

    static void runLandscaper() throws GameActionException {
        if (rc.getDirtCarrying() == 0) {
            tryDig();
        }

        MapLocation bestPlaceToBuildWall = null;
        //find best place to build
        if (hqLoc != null) {
            int lowestElevation = 999999;
            for (Direction dir : directions) {
                MapLocation tileToCheck = hqLoc.add(dir);
                if (rc.getLocation().distanceSquaredTo(tileToCheck) < 4
                        && rc.canDepositDirt(rc.getLocation().directionTo(tileToCheck))) {
                    if (rc.senseElevation(tileToCheck) < lowestElevation) {
                        lowestElevation = rc.senseElevation(tileToCheck);
                        bestPlaceToBuildWall = tileToCheck;
                    }
                }
            }
        }

        if (Math.random() < 0.4) {
            //build the wall
            if (bestPlaceToBuildWall != null) {
                rc.depositDirt(rc.getLocation().directionTo(bestPlaceToBuildWall));
                rc.setIndicatorDot(bestPlaceToBuildWall, 0, 255, 0);
                System.out.println("building a wall");
            }
        }

        //otherwise try to get to the hq
        if (hqLoc != null) {
            goTo(hqLoc);
        } else {
            tryMove(randomDirection());
        }
    }

    static void runDeliveryDrone() throws GameActionException {

    }

    static void runNetGun() throws GameActionException {

    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    /**
     * Try to go towards a direction. If can't go directly, try to go via a 45 or 90 degree angle.
     *
     * @param dir
     * @return boolean
     * @throws GameActionException
     */
    static boolean goTo(Direction dir) throws GameActionException {
        Direction[] toTry = {dir, dir.rotateLeft(),
                dir.rotateRight(), dir.rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight()};
        for (Direction d : toTry){
            if (!rc.senseFlooding(rc.getLocation().add(d)) && tryMove(d))
                return true;
        }
        return false;
    }

    /**
     * Try to go towards a location
     *
     * @param destination
     * @return
     * @throws GameActionException
     */
    static boolean goTo(MapLocation destination) throws GameActionException {
        return goTo(rc.getLocation().directionTo(destination));
    }

    static boolean tryDig() throws GameActionException {
        Direction dir = randomDirection();
        if (rc.canDigDirt(dir)) {
            rc.digDirt(dir);
            rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);
            return true;
        }
        return false;
    }

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    /**
     * Returns a list of locations closer than a radius.
     *
     * @return vis
     * @throws GameActionException
     */
    static ArrayList<MapLocation> vision(int radius) throws GameActionException {
        MapLocation loc = rc.getLocation();

        ArrayList<MapLocation> vis = new ArrayList<MapLocation>();
        vis.add(loc);

        for (int x = 0; x*x <= radius; x++) {
            for (int y = 0; x*x + y*y <= radius; y++) {
                if (x == 0 && y == 0) {
                    continue;
                }

                if (rc.canSenseLocation(new MapLocation(loc.x + x, loc.y + y))){
                    vis.add(new MapLocation(loc.x + x, loc.y + y));
                }
                if (rc.canSenseLocation(new MapLocation(loc.x + x, loc.y - y))){
                    vis.add(new MapLocation(loc.x + x, loc.y - y));
                }
                if (rc.canSenseLocation(new MapLocation(loc.x - x, loc.y + y))){
                    vis.add(new MapLocation(loc.x - x, loc.y + y));
                }
                if (rc.canSenseLocation(new MapLocation(loc.x - x, loc.y - y))){
                    vis.add(new MapLocation(loc.x - x, loc.y - y));
                }
            }
        }

        return vis;
    }

    /**
     * If we see soup on a non flooded tile, return the tile.
     *
     * @return loc
     * @throws GameActionException
     */
    static MapLocation seeSoup(int radius) throws GameActionException {
        ArrayList<MapLocation> vis = vision(radius);
        for (MapLocation loc : vis) {
            if (rc.senseSoup(loc) > 0 && !rc.senseFlooding(loc)) {
                return loc;
            }
        }
        return null;
    }

    // Communications
    // All messages should start with this
    static int teamSecret = 823642;
    // TODO: Learn how to use maps and create a map of resource codes
    // 100 is the code for there is soup around this location.
    // 200 is the code for there is no more soup around this location.
    // 300 is the code for the HQ

    /**
     * Broadcasts location with resource info
     *
     * @param loc
     * @param resourceType
     * @param cost
     * @throws GameActionException
     */
    public static void broadcastLocation(MapLocation loc, String resourceType, int cost) throws GameActionException {
        int[] message = new int[7];

        message[0] = teamSecret;
        switch (resourceType) {
            // TODO: When learned how to use maps, change 100 to soup code
            case "soup":        message[1] = 100;   break;
            case "no soup":     message[1] = 200;   break;
        }

        message[2] = loc.x;
        message[3] = loc.y;

        if (rc.canSubmitTransaction(message, cost))
            rc.submitTransaction(message, cost);
    }

    public static void sendHqLoc(MapLocation loc) throws GameActionException {
        int[] message = new int[7];
        message[0] = teamSecret;
        message[1] = 300;
        message[2] = loc.x;
        message[3] = loc.y;
        if (rc.canSubmitTransaction(message, 1))
            rc.submitTransaction(message, 1);
    }

    public static void getHqLocFromBlockchain() throws GameActionException {
        for (int i = 1; i < rc.getRoundNum(); i++) {
            for (Transaction tx : rc.getBlock(i)) {
                int[] mess = tx.getMessage();
                if (mess[0] == teamSecret && mess[1] == 300) {
                    System.out.println("found the HQ");
                    hqLoc = new MapLocation(mess[2], mess[3]);
                    return;
                }
            }
        }
    }

    /**
     * Adds or removes soup locations based on last block.
     *
     * @throws GameActionException
     */
    public static void updateSoupLocations() throws GameActionException {
        for (Transaction tx : rc.getBlock(rc.getRoundNum() - 1)) {
            int[] message = tx.getMessage();

            if (message[0] != teamSecret) {
                continue;
            }

            // TODO: When learned how to use maps, change 100, 200 to soup code
            if (message[1] == 100) {
                soupLocations.add(new MapLocation(message[2], message[3]));
            } else if (message[1] == 200 &&
                    soupLocations.contains(new MapLocation(message[2], message[3]))) {
                soupLocations.remove(new MapLocation(message[2], message[3]));
            }
        }
    }

    public static boolean broadcastedCreation = false;
    public static void broadcastDesignSchoolCreation(MapLocation loc) throws GameActionException {
        int[] message = new int[7];
        message[0] = teamSecret;
        message[1] = 1;
        message[2] = loc.x;
        message[3] = loc.y;
        if (rc.canSubmitTransaction(message, 3))
            rc.submitTransaction(message, 3);
    }

    // check the latest block for unit creation messages
    public static void updateUnitCounts() throws GameActionException {
        for (Transaction tx : rc.getBlock(rc.getRoundNum() - 1)) {
            int[] mess = tx.getMessage();
            if (mess[0] == teamSecret && mess[1] == 1) {
                System.out.println("heard about a cool new school");
                numDesignSchools += 1;
            }
        }
    }
}
