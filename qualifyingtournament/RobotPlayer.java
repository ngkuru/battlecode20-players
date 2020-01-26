package qualifyingtournament;
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

    static Direction[] directionsOrdered = {
            Direction.EAST,
            Direction.NORTHEAST,
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.WEST,
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
    };

    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    static int numMiners = 0;
    static int numLandscapers = 0;
    static int numDrones = 0;
    static int numDesignSchools = 0;
    static int numFulfillmentCenters = 0;

    static boolean newUnit;
    static boolean battlecry = false;
    static boolean initializedDelivery = false;
    static boolean builtWall = false;

    static MapLocation hqLoc;
    static MapLocation target;
    static MapLocation designSchool;
    static ArrayList<MapLocation> soupLocations = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> locationsTowardsTarget = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> refineryLocations = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> landscaperLocations = new ArrayList<MapLocation>();
    static ArrayList<MapLocation> deliveryLocations = new ArrayList<MapLocation>();

    // This will be a string of the robot's current task
    static String[] goingToCodes = {"soup", "HQ", "beginning", "refinery", "landscaper", "delivery"};
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
        //If we are the HQ, we know the location
        if (rc.getType() == RobotType.HQ) {
            hqLoc = rc.getLocation();
        }

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

        // Search first 5 rounds of blockchain for HQ location
        for (int i = 1; i < 5 && i < rc.getRoundNum(); i++) {
            boolean foundHQ = false;

            for (Transaction tx : rc.getBlock(i)) {
                int[] message = tx.getMessage();

                if (message[0] == teamSecret && message[1] == 500) {
                    foundHQ = true;
                    hqLoc = new MapLocation(message[2], message[3]);
                }
            }

            if (foundHQ == true) {
                break;
            }
        }
    }

    static void runHQ() throws GameActionException {
        //In the beginning of the game, broadcast location
        if (!battlecry) {
            broadcastLocation(rc.getLocation(), "HQ", 1);
            battlecry = true;
        }

        // HQ also keeps track of locations and informs newly spawned miners.
        if (turnCount > 1) {
            receiveMessage();
        }

        // Check if we see soup
        MapLocation soupLoc = seeSoup(rc.getType().sensorRadiusSquared);
        // If we see soup, update soupLocations
        if (soupLoc != null && !soupLocations.contains(soupLoc)) {
            soupLocations.add(soupLoc);
            broadcastLocation(soupLocations.get(0), "soup", 1);
        }

        // If there is a new miner and we know a soup location, broadcast any soup location
        if (newUnit == true && soupLocations.size() > 0) {
            broadcastLocation(soupLocations.get(0), "soup", 1);
            newUnit = false;
        }

        // Create some miners in the beginning of the game
        if (numMiners < 6) {
            if (tryBuild(RobotType.MINER, randomDirection())) {
                numMiners++;
                newUnit = true;
            }
        }

        /*
        // Check if the design school is still alive
        if (numDesignSchools > 0) {
            boolean foundIt = false;

            RobotInfo[] robots = rc.senseNearbyRobots();
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.DESIGN_SCHOOL && robot.team == rc.getTeam()) {
                    foundIt = true;
                    break;
                }
            }

            if (!foundIt) {
                broadcastLocation(rc.getLocation(), "no design school", 1);
            }
        }
         */

        // TODO: Make the HQ shoot drones
    }

    static void runMiner() throws GameActionException {
        // Do not make this list too long for bytecode purposes
        if (locationsTowardsTarget.size() > 10) {
            locationsTowardsTarget.remove(0);
        }

        // Get status update on locations from blockchain
        receiveMessage();
        // If on a tile with soup info but no soup, inform others
        if (soupLocations.contains(rc.getLocation()) && rc.senseSoup(rc.getLocation()) == 0){
            broadcastLocation(rc.getLocation(), "no soup", 2);
        }

        // In the beginning, send targets directly to where they have been going for a while if we don't already know a soup location
        // Else if they have been going for too long, remove target
        if (turnCount == 1 && soupLocations.isEmpty()) {
            Direction dir = rc.getLocation().directionTo(hqLoc).opposite();
            changeTarget(rc.getLocation().add(dir).add(dir).add(dir).add(dir).add(dir), "beginning");
        } else if (turnCount == 15 && goingTo == "beginning") {
            changeTarget(null, null);
        }

        // As a general rule, if reached target, disable target
        // Else if going to soup but target is no longer in soupLocations, disable target
        // TODO: Else if can see target but no soup or water - disable but without clash and multibroadcasting
        // TODO: Else if the target was going to a refinery but it was removed, update
        if (target != null && rc.getLocation() == target) {
            changeTarget(null, null);
        } else if (goingTo == "soup" && !soupLocations.contains(target)) {
            changeTarget(null, null);
        }

        // Check if we see soup
        MapLocation soupLoc = seeSoup(rc.getType().sensorRadiusSquared);
        // If we see soup, set target to soup
        // TODO: Create a blacklist for soup locations that are unreachable
        // Else if we know where soup is, set target to soup
        if (soupLoc != null) {
            changeTarget(soupLoc, "soup");
        } else if ((target == null || goingTo == "beginning") && !soupLocations.isEmpty()) {
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

        // Build at least a refinery before we start building a wall.
        if (refineryLocations.size() == 1) {
            if (rc.getLocation().distanceSquaredTo(hqLoc) > 8 && rc.getLocation().distanceSquaredTo(hqLoc) < 26) {
                Direction dir = rc.getLocation().directionTo(hqLoc).opposite();
                tryBuild(RobotType.REFINERY, dir);
                tryBuild(RobotType.REFINERY, dir.rotateLeft());
                tryBuild(RobotType.REFINERY, dir.rotateRight());
            }
        }

        // Build at least a fulfillment center before we start building a wall.
        if (numFulfillmentCenters < 1) {
            if (rc.getLocation().distanceSquaredTo(hqLoc) > 8 && rc.getLocation().distanceSquaredTo(hqLoc) < 26) {
                Direction dir = rc.getLocation().directionTo(hqLoc).opposite();
                tryBuild(RobotType.FULFILLMENT_CENTER, dir);
                tryBuild(RobotType.FULFILLMENT_CENTER, dir.rotateLeft());
                tryBuild(RobotType.FULFILLMENT_CENTER, dir.rotateRight());
            }
        }

        // If inventory is full and not already going to a refinery, set target to a refinery
        if (rc.getSoupCarrying() == RobotType.MINER.soupLimit && goingTo != "refinery") {
            // Search for nearby refineries
            ArrayList<MapLocation> availableRefineries = new ArrayList<MapLocation>();
            for (MapLocation ref : refineryLocations) {
                if (ref.distanceSquaredTo(rc.getLocation()) < 100) {
                    availableRefineries.add(ref);
                }
            }

            // If there is a nearby refinery, pick a random one, else build one
            // TODO: What if there is only one and it gets you stuck? Maybe need to pick a closer range
            // Else if no refinery and enough money to build, build one
            // If all else fails, target the HQ
            if (!availableRefineries.isEmpty()) {
                MapLocation refLoc = availableRefineries.get(rand.nextInt(availableRefineries.size()));
                changeTarget(refLoc, "refinery");
            } else if (rc.getTeamSoup() > RobotType.REFINERY.cost) {
                for (Direction d : directions) {
                    if (tryBuild(RobotType.REFINERY, d)) {
                        break;
                    }
                }
            } else {
                changeTarget(hqLoc, "refinery");
            }
        }

        // Build some design schools not too close to the HQ
        if (numDesignSchools < 1 && rc.getTeamSoup() > RobotType.REFINERY.cost + RobotType.DESIGN_SCHOOL.cost &&
                rc.getLocation().distanceSquaredTo(hqLoc) > 8 && rc.getLocation().distanceSquaredTo(hqLoc) < 26) {
            Direction dir = rc.getLocation().directionTo(hqLoc).opposite();
            tryBuild(RobotType.DESIGN_SCHOOL, dir);
            tryBuild(RobotType.DESIGN_SCHOOL, dir.rotateLeft());
            tryBuild(RobotType.DESIGN_SCHOOL, dir.rotateRight());
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
        Direction d = randomDirection();
        if (!locationsTowardsTarget.contains(rc.getLocation().add(d)) && tryMove(d)) {
            locationsTowardsTarget.add(rc.getLocation());
        }
        // If all else fails, move randomly
        goTo(randomDirection());

    }

    static void runRefinery() throws GameActionException {
        if (!battlecry) {
            broadcastLocation(rc.getLocation(), "refinery", 1);
            battlecry = true;
        }
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        if (!battlecry) {
            broadcastLocation(rc.getLocation(), "design school", 1);
            battlecry = true;
        }

        // Check if there is an adjacent landscaper that hasn't been picked up
        if (newUnit) {
            boolean adjUnit = false;
            for (Direction d : directions) {
                if (!rc.canSenseLocation(rc.getLocation().add(d)))
                    continue;

                RobotInfo robot = rc.senseRobotAtLocation(rc.getLocation().add(d));
                if (robot != null && robot.getType() == RobotType.LANDSCAPER && robot.getTeam() == rc.getTeam()) {
                    adjUnit = true;
                }
            }
            if (!adjUnit) {
                newUnit = false;
            }
        }


        if (!newUnit && numLandscapers < 20) {
            Direction dir = rc.getLocation().directionTo(hqLoc);
            if (tryBuild(RobotType.LANDSCAPER, dir) ||
                    tryBuild(RobotType.LANDSCAPER, dir.rotateLeft()) ||
                    tryBuild(RobotType.LANDSCAPER, dir.rotateRight()) ||
                    tryBuild(RobotType.LANDSCAPER, randomDirection())) {
                newUnit = true;
            }
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        if (!battlecry) {
            broadcastLocation(rc.getLocation(), "fulfillment center", 1);
            battlecry = true;
        }

        if (numDrones < 1) {
            if (tryBuild(RobotType.DELIVERY_DRONE, randomDirection())) {
                numDrones++;
            }
        }
    }

    static void runLandscaper() throws GameActionException {
        if (!battlecry) {
            broadcastLocation(rc.getLocation(), "landscaper", 1);
            battlecry = true;
        }

        // Landscapers keep track of if the wall is built
        receiveMessage();

        if (builtWall && rc.getLocation().distanceSquaredTo(hqLoc) > 5) {
            goTo(hqLoc);
        }

        if (rc.getLocation().distanceSquaredTo(hqLoc) < 5) {
            if (rc.getDirtCarrying() == 0) {
                Direction d = rc.getLocation().directionTo(hqLoc).opposite();
                if (tryDig(d) || tryDig(d.rotateLeft()) || tryDig(d.rotateRight())){
                    // TODO: Make this less meaningless
                }
            } else {
                System.out.println(rc.getLocation().directionTo(hqLoc));
                Direction directionToBuild = null;
                for (Direction d : directions) {
                    if (!rc.canSenseLocation(rc.getLocation().add(d)) ||
                            rc.getLocation().add(d).distanceSquaredTo(hqLoc) > 2 ||
                            rc.getLocation().add(d).distanceSquaredTo(hqLoc) == 0) {
                        System.out.println("continued " + d);
                        continue;
                    }

                    if (directionToBuild == null || rc.senseElevation(rc.getLocation().add(d)) < rc.senseElevation(rc.getLocation().add(directionToBuild))) {
                        System.out.println("choosing " + d);
                        directionToBuild = d;
                    }
                    System.out.println("skipping " + d);
                }

                System.out.println("depositing at " + directionToBuild);
                if (rc.isReady() && rc.canDepositDirt(directionToBuild))
                    rc.depositDirt(directionToBuild);
            }
        }
    }

    static void runDeliveryDrone() throws GameActionException {
        // Drones keep track of landscaper locations.
        receiveMessage();

        // If it doesn't have a unit nor target set target
        if (!builtWall && !rc.isCurrentlyHoldingUnit() && goingTo == null && !landscaperLocations.isEmpty()) {
            changeTarget(landscaperLocations.get(0), "landscaper");
        }

        // If target is nearby pick up unit, set target to hq
        if (goingTo == "landscaper" && rc.getLocation().distanceSquaredTo(target) < 3) {
            if (rc.canPickUpUnit(rc.senseRobotAtLocation(target).getID())) {
                rc.pickUpUnit(rc.senseRobotAtLocation(target).getID());
                landscaperLocations.remove(target);
                changeTarget(hqLoc, "HQ");
            }
        }

        if (!builtWall && goingTo == "HQ") {
            if (!initializedDelivery && rc.getLocation().distanceSquaredTo(hqLoc) < 5) {
                for (Direction d : directionsOrdered) {
                    if (rc.canSenseLocation(hqLoc.add(d))) {
                        deliveryLocations.add(hqLoc.add(d));
                    }
                }
                initializedDelivery = true;
            }

            if (initializedDelivery) {
                changeTarget(deliveryLocations.get(rand.nextInt(deliveryLocations.size())), "delivery");
            }
        }

        if (goingTo == "delivery" && rc.getLocation().distanceSquaredTo(target) < 3) {
            Direction d = rc.getLocation().directionTo(target);
            if (rc.senseRobotAtLocation(target) != null) {
                changeTarget(deliveryLocations.get(rand.nextInt(deliveryLocations.size())), "delivery");
            } else if (tryDrop(d)) {
                deliveryLocations.remove(target);
                changeTarget(null, null);

                if (deliveryLocations.isEmpty()) {
                    builtWall = true;
                    broadcastLocation(rc.getLocation(), "built wall", 1);
                }
            }
        }

        if (target != null) {
            if(goTo(target)) {
                locationsTowardsTarget.add(rc.getLocation());
            }
        } else {
            // Else if there is no target, try to go to a place we haven't gone before
            Direction d = randomDirection();
            if (!locationsTowardsTarget.contains(rc.getLocation().add(d)) && tryMove(d)) {
                locationsTowardsTarget.add(rc.getLocation());
            } else {
                // If all else fails, move randomly
                System.out.println("Moved randomly");
                goTo(randomDirection());
            }
        }
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
     * Given a location, return if we can sense it and it is not flooded.
     *
     * @param loc
     * @return
     */
    static boolean valid(MapLocation loc) throws GameActionException {
        return rc.canSenseLocation(loc) && !rc.senseFlooding(loc);
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
            if (!locationsTowardsTarget.isEmpty() && locationsTowardsTarget.get(locationsTowardsTarget.size() - 1) == rc.getLocation().add(d)) {
                continue;
            }
            if (valid(rc.getLocation().add(d)) && tryMove(d))
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
        if (rc.isReady() && rc.canMove(dir) && valid(rc.getLocation().add(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction if valid and not flooded.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir) && valid(rc.getLocation().add(dir))) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to drop a unit if holding in a given direction if valid and not flooded.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryDrop(Direction dir) throws GameActionException {
        if (rc.isCurrentlyHoldingUnit() && rc.isReady() && valid(rc.getLocation().add(dir)) && rc.senseRobotAtLocation(rc.getLocation().add(dir)) == null) {
            rc.dropUnit(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to dig dirt from a direction if below dirt limit.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryDig(Direction dir) throws GameActionException {
        if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit && rc.isReady() && rc.canSenseLocation(rc.getLocation().add(dir)) && rc.canDigDirt(dir)) {
            rc.digDirt(dir);
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
     * If we see soup within a radius, return the tile.
     *
     * @param radius
     * @return
     * @throws GameActionException
     */
    static MapLocation seeSoup(int radius) throws GameActionException {
        MapLocation loc = rc.getLocation();
        int xmax = (int) Math.ceil(Math.sqrt(radius));
        int ymax = (int) Math.ceil(Math.sqrt(radius));

        for (int x = -xmax; x <= xmax; x++) {
            for (int y = -ymax; y <= ymax; y++) {
                MapLocation soupLoc = new MapLocation(loc.x-x, loc.y-y);

                if (valid(soupLoc) && rc.senseSoup(soupLoc) > 0) {
                    return soupLoc;
                }
            }
        }
        return null;
    }

    // Communications
    // All messages should start with this
    static int teamSecret = 823642;
    // TODO: Learn how to use maps and create a map of resource codes
    // 100 is the code for there is soup around this location
    // 200 is the code for there is no more soup around this location
    // 300 is the code for refinery
    // 400 is the code for design school
    // 500 is the code for HQ
    // 600 is the code for no more design school at this location
    // 700 is the code for fulfillment center
    // 800 is the code for landscaper

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
            case "soup":                message[1] = 100;       break;
            case "no soup":             message[1] = 200;       break;
            case "refinery":            message[1] = 300;       break;
            case "design school":       message[1] = 400;       break;
            case "HQ":                  message[1] = 500;       break;
            case "no design school":    message[1] = 600;       break;
            case "fulfillment center":  message[1] = 700;       break;
            case "landscaper":          message[1] = 800;       break;
            case "built wall":          message[1] = 900;       break;
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
    public static void receiveMessage() throws GameActionException {
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

                case 400: // "design school"
                    numDesignSchools++;
                    designSchool = new MapLocation(message[2], message[3]);
                    break;

                case 600: // "design school dead"
                    numDesignSchools--;
                    designSchool = null;
                    break;

                case 700: // "fulfillment center"
                    numFulfillmentCenters++;
                    break;

                case 800: // "landscaper"
                    landscaperLocations.add(new MapLocation(message[2], message[3]));
                    break;

                case 900: // "built wall"
                    builtWall = true;
                    break;
            }
        }
    }
}
