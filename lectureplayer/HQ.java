package lectureplayer;
import battlecode.common.*;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class HQ extends Building {
    static int numMiners = 0;

    public HQ(RobotController r) {
        super(r);
    }

    public void takeTurn() throws GameActionException {
        super.takeTurn();

        if (turnCount == 1) {
            comms.sendHqLoc(rc.getLocation());
        }

        if (numMiners < 10) {
            for (Direction dir : Util.directions) {
                if (tryBuild(RobotType.MINER, dir)) {
                    numMiners++;
                }
            }
        }
    }

}
