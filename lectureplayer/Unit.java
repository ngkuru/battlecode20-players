package lectureplayer;
import battlecode.common.*;

public class Unit extends Robot {

    Navigation nav;

    MapLocation hqLoc;

    public Unit(RobotController r) {
        super(r);
        nav = new Navigation(rc);
    }

    public void takeTurn() throws GameActionException {
        super.takeTurn();

        System.out.println("I'm a unit");
    }

    void findHQ() throws GameActionException {
        if (hqLoc == null) {
            // search surroundings for HQ
            RobotInfo[] robots = rc.senseNearbyRobots();
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.HQ && robot.team == rc.getTeam()) {
                    hqLoc = robot.location;
                }
            }
            if (hqLoc == null)  {
                // if still null, search the blockchain
                getHqLocFromBlockchain();
            }
        }
    }

}
