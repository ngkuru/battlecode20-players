package lectureplayer;
import battlecode.common.*;

public class Navigation {
    RobotController rc;

    // state related only to navigation should go here

    public Navigation(RobotController r) {
        rc = r;
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    boolean goTo(Direction dir) throws GameActionException {
        Direction[] toTry = {dir, dir.rotateLeft(),
                dir.rotateRight(), dir.rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight()};
        for (Direction d : toTry){
            if (tryMove(d))
                return true;
        }
        return false;
    }

    boolean goTo(MapLocation destination) throws GameActionException {
        return goTo(rc.getLocation().directionTo(destination));
    }

}
