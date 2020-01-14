package mainplayer;
import battlecode.common.*;
import java.lang.Math;
import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Random rand = new Random();

    static Direction[] directions = {
            Direction.EAST,
            Direction.WEST,
            Direction.SOUTH,
            Direction.NORTH,
            Direction.SOUTHEAST,
            Direction.NORTHWEST,
            Direction.NORTHEAST,
            Direction.SOUTHWEST,
    };

    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    static int numMiners = 0;

    static boolean newMiner;

    static MapLocation hqLoc;
    static MapLocation target;
    static ArrayList<MapLocation> soupLocations = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> locationsTowardsTarget = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> refineryLocations = new ArrayList<MapLocation>();

    // This will be a string of the robot's current task
    static String[] goingToCodes = {"soup", "HQ", "beginning", "refinery"};
    static String goingTo;

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
                refineryLocations.add(hqLoc);
                return;
            }
        }

        // TODO: Check the blockchain to find HQ location
    }

    static void runHQ() throws GameActionException {
        // TODO: Send location info to blockchain

        // HQ also keeps track of locations and informs newly spawned miners.
        updateLocations();

        // Check if we see soup
        MapLocation soupLoc = seeSoup(rc.getType().sensorRadiusSquared);
        // If we see soup, update soupLocations
        if (soupLoc != null && !soupLocations.contains(soupLoc)) {
            soupLocations.add(soupLoc);
            broadcastLocation(soupLocations.get(0), "soup", 1);
        }

        // If there is a new miner, broadcast any soup location
        if (newMiner == true) {
            broadcastLocation(soupLocations.get(0), "soup", 1);
            newMiner = false;
        }

        // Create some miners in the beginning of the game
        if (numMiners < 6) {
            for (Direction dir : directions) {
                if (tryBuild(RobotType.MINER, dir)) {
                    numMiners++;
                    newMiner = true;
                }
            }
        }

        // TODO: Make the HQ shoot drones
    }

    static void runMiner() throws GameActionException {
        // Get status update on locations from blockchain
        updateLocations();
        // If on a tile with soup info but no soup, inform others
        if (soupLocations.contains(rc.getLocation()) && rc.senseSoup(rc.getLocation()) == 0){
            broadcastLocation(rc.getLocation(), "no soup", 2);
        }

        // In the beginning, send targets directly to where they have been going for a while if we don't already know a soup location
        // Else if they have been going for too long, remove target
        if (turnCount == 1 && soupLocations.size() == 0) {
            Direction dir = rc.getLocation().directionTo(hqLoc).opposite();
            changeTarget(rc.getLocation().add(dir).add(dir).add(dir).add(dir).add(dir), "beginning");
        } else if (turnCount == 15 && goingTo == "beginning") {
            changeTarget(null, null);
        }

        // As a general rule, if reached target, disable target
        // Else if going to soup but target is no longer in soupLocations, disable target
        // TODO: Else if can see target but no soup or water - disable but without clash and multibroadcasting
        if (target != null && rc.getLocation() == target) {
            changeTarget(null, null);
        } else if (goingTo == "soup" && !soupLocations.contains(target)) {
            changeTarget(null, null);
        }

        // Check if we see soup
        MapLocation soupLoc = seeSoup(10);
        // If we see soup, set target to soup
        // TODO: Create a blacklist for soup locations that are unreachable
        // Else if we know where soup is, set target to soup
        if (soupLoc != null) {
            changeTarget(soupLoc, "soup");
        } else if ((target == null || goingTo == "beginning") && soupLocations.size() > 0) {
            changeTarget(soupLocations.get(rand.nextInt(soupLocations.size())), "soup");
        }

        // Try to mine soup, if we are able to, reset target
        for (Direction dir : directions) {
            if (tryMine(dir)) {
                changeTarget(null, null);
                // If soup location still has soup is unknown, share it with others
                if (rc.senseSoup(rc.getLocation().add(dir)) > 0 && !soupLocations.contains(rc.getLocation().add(dir))) {
                    broadcastLocation(rc.getLocation().add(dir), "soup", 1);
                }
                // If soup location has no more soup and is known, share the news
                // Also if there is a nearby soup source unknown, publish it
                if (rc.senseSoup(rc.getLocation().add(dir)) == 0) {
                    if (soupLocations.contains(rc.getLocation().add(dir))) {
                        broadcastLocation(rc.getLocation().add(dir), "no soup", 1);
                    }
                }
            }
        }
        // Try to refine soup, reset if successful
        for (Direction dir : directions) {
            if (tryRefine(dir)) {
                changeTarget(null, null);
            }
        }

        // If inventory is full and not already going to a refinery, set target to a refinery
        if (rc.getSoupCarrying() == RobotType.MINER.soupLimit && goingTo != "refinery") {
            /*
            // Search for nearby refineries
            ArrayList<MapLocation> availableRefineries = new ArrayList<MapLocation>();
            for (MapLocation ref : refineryLocations) {
                if (ref.distanceSquaredTo(rc.getLocation()) < 100) {
                    availableRefineries.add(ref);
                }
            }
            System.out.println(availableRefineries);

            // If there is a nearby refinery, pick a random one, else build one
            // TODO: What if there is only one and it gets you stuck? Maybe need to pick a closer range
            // Else if no refinery and enough money to build and broadcast, build one
            // If all else fails, target the HQ
            if (availableRefineries.size() > 0) {
                changeTarget(soupLocations.get(rand.nextInt(availableRefineries.size())), "refinery");
            } else if (rc.getTeamSoup() > RobotType.REFINERY.cost) {
                for (Direction d : directions) {
                    if (tryBuild(RobotType.REFINERY, d)) {
                        broadcastLocation(rc.getLocation().add(d), "refinery", 1);
                        break;
                    }
                }
            } else {
                changeTarget(hqLoc, "refinery");
            }
            */
            changeTarget(hqLoc, "refinery");
        }

        // If there is a target, try to go to the target and add new location to the list, if fails or no target, move randomly
        if (target != null) {
            rc.setIndicatorLine(rc.getLocation(), target, 255, 255, 0);
            if (goTo(target)) {
                locationsTowardsTarget.add(rc.getLocation());
                //If walked towards the same square 3 times, cancel target
                if (Collections.frequency(locationsTowardsTarget, rc.getLocation()) >= 3) {
                    changeTarget(null, null);
                }
            }
        }
        // Else if there is no target, try to go to a place we haven't gone before
        for (Direction d : directions) {
            if (!locationsTowardsTarget.contains(rc.getLocation().add(d)) && tryMove(d)) {
                locationsTowardsTarget.add(rc.getLocation());
            }
        }
        // If all else fails, move randomly
        goTo(randomDirection());
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {

    }

    static void runFulfillmentCenter() throws GameActionException {

    }

    static void runLandscaper() throws GameActionException {

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
     * Changes target, clears target past path.
     *
     * @param newTarget
     * @param newTask
     */
    static void changeTarget(MapLocation newTarget, String newTask) {
        target = newTarget;
        goingTo = newTask;
        locationsTowardsTarget.clear();
        locationsTowardsTarget.add(rc.getLocation());
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

    /**
     * Try to move (any direction).
     *
     * @return boolean: if successful
     * @throws GameActionException
     */
    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
    }

    /**
     * Attempts to move in a given direction if we can and not flooded.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMove(dir) && !rc.senseFlooding(rc.getLocation().add(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction if not flooded.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir) && !rc.senseFlooding(rc.getLocation().add(dir))) {
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
    // 300 is the code for refinery

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
            case "refinery":    message[1] = 300;   break;
        }

        message[2] = loc.x;
        message[3] = loc.y;

        if (rc.canSubmitTransaction(message, cost))
            rc.submitTransaction(message, cost);
    }

    /**
     * Adds or removes locations based on last block.
     *
     * @throws GameActionException
     */
    public static void updateLocations() throws GameActionException {
        for (Transaction tx : rc.getBlock(rc.getRoundNum() - 1)) {
            int[] message = tx.getMessage();

            if (message[0] != teamSecret) {
                continue;
            }

            // TODO: When learned how to use maps, change 100, 200 to soup code
            switch (message[1]) {
                case 100: // "soup"
                    if (!soupLocations.contains(new MapLocation(message[2], message[3]))) {
                        soupLocations.add(new MapLocation(message[2], message[3]));
                    }
                    break;

                case 200: // "no soup"
                    if (soupLocations.contains(new MapLocation(message[2], message[3]))) {
                        soupLocations.remove(new MapLocation(message[2], message[3]));
                    }
                    break;

                case 300: // "refinery"
                    if (!refineryLocations.contains(new MapLocation(message[2], message[3]))) {
                        refineryLocations.add(new MapLocation(message[2], message[3]));
                    }
                    break;
            }
        }
    }
}
