package lectureplayer;
import battlecode.common.*;
import java.util.ArrayList;

import java.util.ArrayList;

public class Communications {
    RobotController rc;

    // state related only to communications should go here

    // all messages from our team should start with this
    static final int teamSecret = 105797;
    //the second entry in every message tells us the type of message
    static final String[] messageType = {"HQ loc",
            "design school created",
            "soup location"
    };

    public Communications(RobotController r) {
        rc = r;
    }

    public void sendHqLoc(MapLocation loc) throws GameActionException {
        int[] message = new int[7];
        message[0] = teamSecret;
        message[1] = 0;
        message[2] = loc.x;
        message[3] = loc.y;
        if (rc.canSubmitTransaction(message, 3))
            rc.submitTransaction(message, 3);
    }

    // check the latest block for unit creation messages
    public int getNewDesignSchoolCount() throws GameActionException {
        int count = 0;
        for (Transaction tx : rc.getBlock(rc.getRoundNum() - 1)) {
            int[] mess = tx.getMessage();
            if (mess[0] == teamSecret && mess[1] == 1) {
                System.out.println("heard about a cool new school");
                count += 1;
            }
        }
        return count;
    }

    public void broadcastSoupLocation(MapLocation loc) throws GameActionException {
        int[] message = new int[7];
        message[0] = teamSecret;
        message[1] = 2;
        message[2] = loc.x;
        message[3] = loc.y;
        if (rc.canSubmitTransaction(message, 3))
            rc.submitTransaction(message, 3);
    }

    public ArrayList<MapLocation> updateSoupLocations(ArrayList<MapLocation> soupLocations) throws GameActionException {
        for (Transaction tx : rc.getBlock(rc.getRoundNum() - 1)) {
            int[] mess = tx.getMessage();
            if (mess[0] == teamSecret && mess[1] == 2) {
                // TODO: don't add duplicate locations
                System.out.println("heard about a tasty new soup location");
                soupLocations.add(new MapLocation(mess[2], mess[3]));
            }
        }
        return soupLocations;
    }

}
