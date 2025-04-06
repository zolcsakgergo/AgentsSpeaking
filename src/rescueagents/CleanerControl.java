package rescueagents;

import java.util.ArrayList;
import java.util.Comparator;

import rescueframework.AbstractRobotControl;
import rescueframework.RescueFramework;
import world.AStarSearch;
import world.Cell;
import world.Path;
import world.Robot;
import world.RobotPercepcion;

/**
 * CleanerControl class to implement custom robot control strategies for
 * cleaning junk
 */
public class CleanerControl extends AbstractRobotControl {
        static ArrayList<Cell> cleaned = new ArrayList<Cell>();
        Cell planned = null;
        private int stepsOnJunk = 0;
        private static final int CLEAN_TIME = 1; // Steps needed to clean a junk

        /**
         * Default constructor saving world robot object and percepcion
         * 
         * @param robot      The robot object in the world
         * @param percepcion Percepcion of all robots
         */
        public CleanerControl(Robot robot, RobotPercepcion percepcion) {
                super(robot, percepcion);
        }

        /**
         * Clean junk around the ship.
         * 
         * @return Return NULL for staying in place, 0 = step up, 1 = step right,
         *         2 = step down, 3 = step left
         */
        public Integer step() {
                // Add null check for robot location
                if (robot == null || robot.getLocation() == null) {
                        RescueFramework.log("Robot or location is null");
                        return null;
                }

                // If we have junk on our current cell, clean it
                String obstacleImage = robot.getLocation().getObstacleImage();
                if (robot.getLocation().hasObstacle()
                                && (obstacleImage.equals("junk10") || obstacleImage.equals("junk20"))) {
                        stepsOnJunk++;

                        // If we've spent enough time on the junk, consider it cleaned
                        if (stepsOnJunk >= CLEAN_TIME) {
                                // Remove the junk
                                robot.getLocation().setObstacleImage("");

                                // Add to cleaned list
                                if (!cleaned.contains(robot.getLocation())) {
                                        cleaned.add(robot.getLocation());
                                }

                                RescueFramework.log("Junk cleaned at " + robot.getLocation().getX() + ","
                                                + robot.getLocation().getY() + " - moving to next target");
                                planned = null;
                                stepsOnJunk = 0;

                                // Immediately find the next junk
                                Cell nextJunk = findClosestJunk(getActiveDiscoveredJunk());
                                if (nextJunk != null) {
                                        planned = nextJunk;
                                        Path junkPath = AStarSearch.search(robot.getLocation(), planned, -1);
                                        if (junkPath != null && junkPath.getFirstCell() != null) {
                                                return junkPath.getFirstCell().directionFrom(robot.getLocation());
                                        }
                                }

                                // If no junk left, explore
                                Path explorePath = percepcion.getShortestUnknownPath(robot.getLocation());
                                if (explorePath != null && explorePath.getFirstCell() != null) {
                                        return explorePath.getFirstCell().directionFrom(robot.getLocation());
                                }
                        }

                        // Continue cleaning (stay in place)
                        return null;
                }

                // If we have a planned target, try to move towards it
                if (planned != null) {
                        Path path = AStarSearch.search(robot.getLocation(), planned, -1);
                        if (path != null && path.getFirstCell() != null) {
                                return path.getFirstCell().directionFrom(robot.getLocation());
                        } else {
                                // If path not found, clear planned target
                                planned = null;
                        }
                }

                // Find closest discovered junk
                stepsOnJunk = 0;
                ArrayList<Cell> discoveredJunk = getActiveDiscoveredJunk();
                Cell closestJunk = findClosestJunk(discoveredJunk);

                if (closestJunk != null) {
                        // Go to closest junk
                        planned = closestJunk;
                        Path junkPath = AStarSearch.search(robot.getLocation(), planned, -1);
                        if (junkPath != null && junkPath.getFirstCell() != null) {
                                return junkPath.getFirstCell().directionFrom(robot.getLocation());
                        }
                }

                // If no junk found or path to junk not found, try exploring
                Path explorePath = percepcion.getShortestUnknownPath(robot.getLocation());
                if (explorePath != null && explorePath.getFirstCell() != null) {
                        return explorePath.getFirstCell().directionFrom(robot.getLocation());
                }

                // Stay in place if no action needed
                return null;
        }

        /**
         * Gets a list of discovered junk that hasn't been cleaned yet
         */
        private ArrayList<Cell> getActiveDiscoveredJunk() {
                ArrayList<Cell> discoveredJunk = new ArrayList<Cell>();

                // Get all cells that the robot can see
                for (Robot r : percepcion.getRobots()) {
                        Cell location = r.getLocation();
                        if (location != null) {
                                // Check cells in visibility range
                                for (int dx = -3; dx <= 3; dx++) {
                                        for (int dy = -3; dy <= 3; dy++) {
                                                // Skip cells too far away
                                                if (Math.sqrt(dx * dx + dy * dy) > 3.5)
                                                        continue;

                                                // Get cell at offset by moving in steps
                                                Cell current = location;
                                                boolean valid = true;

                                                // Move horizontally
                                                for (int i = 0; i < Math.abs(dx); i++) {
                                                        current = current.getAccessibleNeigbour(dx > 0 ? 1 : 3);
                                                        if (current == null) {
                                                                valid = false;
                                                                break;
                                                        }
                                                }
                                                if (!valid)
                                                        continue;

                                                // Move vertically
                                                for (int i = 0; i < Math.abs(dy); i++) {
                                                        current = current.getAccessibleNeigbour(dy > 0 ? 2 : 0);
                                                        if (current == null) {
                                                                valid = false;
                                                                break;
                                                        }
                                                }
                                                if (!valid)
                                                        continue;

                                                // Check if cell has junk (either type)
                                                String obstacleImage = current.getObstacleImage();
                                                if (current.isDiscovered() && current.hasObstacle() &&
                                                                (obstacleImage.equals("junk10")
                                                                                || obstacleImage.equals("junk20"))
                                                                &&
                                                                !cleaned.contains(current)) {
                                                        discoveredJunk.add(current);
                                                }
                                        }
                                }
                        }
                }

                return discoveredJunk;
        }

        /**
         * Find the closest junk to the robot
         * 
         * @param junk List of junk cells to check
         * @return The closest junk cell or null if none found
         */
        private Cell findClosestJunk(ArrayList<Cell> junk) {
                if (junk.isEmpty()) {
                        return null;
                }

                Cell closest = null;
                int shortestDistance = Integer.MAX_VALUE;

                for (Cell cell : junk) {
                        Path path = AStarSearch.search(robot.getLocation(), cell, -1);
                        if (path != null) {
                                int distance = path.getLength();
                                if (distance < shortestDistance) {
                                        shortestDistance = distance;
                                        closest = cell;
                                }
                        }
                }

                return closest;
        }
}