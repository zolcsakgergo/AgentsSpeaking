import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import rescueframework.RescueFramework;
import world.Cell;
import world.Fire;
import world.Robot;

/**
 * Environment class that connects to the rescue simulation
 */
public class RescueEnvironment {

        static Logger logger = Logger.getLogger(RescueEnvironment.class.getName());

        private world.Map worldMap;
        private Robot firefighterRobot;
        private Robot cleanerRobot;

        // Store percepts for each agent
        private Map<String, List<Percept>> agentPercepts = new HashMap<>();

        /**
         * Simple class to represent a percept
         */
        public static class Percept {
                private String name;
                private Object[] terms;

                public Percept(String name, Object... terms) {
                        this.name = name;
                        this.terms = terms;
                }

                public String getName() {
                        return name;
                }

                public Object[] getTerms() {
                        return terms;
                }

                @Override
                public String toString() {
                        StringBuilder sb = new StringBuilder(name);
                        sb.append("(");
                        for (int i = 0; i < terms.length; i++) {
                                sb.append(terms[i]);
                                if (i < terms.length - 1) {
                                        sb.append(",");
                                }
                        }
                        sb.append(")");
                        return sb.toString();
                }
        }

        /**
         * Initialize the environment
         */
        public void init() {
                // Initialize the world map and robots
                this.worldMap = RescueAdapter.getMap();

                // Get the robots from the map
                List<Robot> robots = this.worldMap.getRobots();
                if (robots.size() >= 2) {
                        firefighterRobot = robots.get(0);
                        cleanerRobot = robots.get(1);
                } else {
                        logger.severe("Not enough robots in the simulation!");
                }

                // Initialize percept lists
                agentPercepts.put("firefighter", new ArrayList<>());
                agentPercepts.put("cleaner", new ArrayList<>());

                updatePercepts();
        }

        /**
         * Update the agents' percepts based on the current state of the environment
         */
        void updatePercepts() {
                clearPercepts();

                // Add positions of robots
                if (firefighterRobot != null) {
                        Cell ffLoc = firefighterRobot.getLocation();
                        Percept pos = new Percept("pos", "firefighter", ffLoc.getX(), ffLoc.getY());
                        addPercept("firefighter", pos);
                        addPercept("cleaner", pos);
                }

                if (cleanerRobot != null) {
                        Cell cleanerLoc = cleanerRobot.getLocation();
                        Percept pos = new Percept("pos", "cleaner", cleanerLoc.getX(), cleanerLoc.getY());
                        addPercept("firefighter", pos);
                        addPercept("cleaner", pos);
                }

                // Add fire percepts
                List<Fire> fires = worldMap.getFires();
                for (Fire fire : fires) {
                        if (fire.isActive()) {
                                Cell fireCell = fire.getLocation();
                                Percept fireLit = new Percept("fire", fireCell.getX(), fireCell.getY(),
                                                fire.getIntensity());
                                addPercept("firefighter", fireLit);
                        }
                }

                // Add junk percepts
                // Iterate through all cells to find junk
                for (int x = 0; x < worldMap.getWidth(); x++) {
                        for (int y = 0; y < worldMap.getHeight(); y++) {
                                Cell cell = worldMap.getCell(x, y);
                                if (cell != null && cell.isDiscovered()) {
                                        String obstacleImage = cell.getObstacleImage();

                                        // Check for junk types
                                        if (obstacleImage.equals("junk10") || obstacleImage.equals("junk20")) {
                                                Percept junkLit = new Percept("obstacle", x, y, obstacleImage);
                                                addPercept("cleaner", junkLit);
                                        }
                                }
                        }
                }

                // Add unknown areas for exploration
                List<Cell> unknownCells = worldMap.getUnknownCells();
                for (Cell cell : unknownCells) {
                        Percept unknownLit = new Percept("unknown", cell.getX(), cell.getY());
                        addPercept("firefighter", unknownLit);
                        addPercept("cleaner", unknownLit);
                }
        }

        /**
         * Clear all percepts
         */
        private void clearPercepts() {
                for (String agent : agentPercepts.keySet()) {
                        agentPercepts.get(agent).clear();
                }
        }

        /**
         * Add a percept for an agent
         */
        private void addPercept(String agent, Percept percept) {
                List<Percept> percepts = agentPercepts.get(agent);
                if (percepts != null) {
                        percepts.add(percept);
                }
        }

        /**
         * Get all percepts for an agent
         */
        public List<Percept> getPercepts(String agent) {
                return agentPercepts.getOrDefault(agent, new ArrayList<>());
        }

        /**
         * Execute an action for an agent
         */
        public boolean executeAction(String agName, String action, Object... params) {
                boolean result = false;

                try {
                        // Get the robot for this agent
                        Robot robot = agName.equals("firefighter") ? firefighterRobot : cleanerRobot;

                        if (action.equals("move_towards")) {
                                // Get target coordinates
                                int x = (int) params[0];
                                int y = (int) params[1];

                                // Find path to target
                                Cell targetCell = worldMap.getCell(x, y);
                                if (targetCell != null) {
                                        result = worldMap.moveRobot(robot, targetCell);
                                }
                        } else if (action.equals("reduce_intensity")) {
                                // Reduce fire intensity
                                int amount = (int) params[0];
                                Cell robotLoc = robot.getLocation();

                                if (robotLoc.hasFire() && robotLoc.getFire().isActive()) {
                                        Fire fire = robotLoc.getFire();
                                        fire.reduceIntensity(amount);
                                        result = true;
                                }
                        } else if (action.equals("clean_junk")) {
                                // Clean junk
                                Cell robotLoc = robot.getLocation();

                                if (robotLoc.hasObstacle() &&
                                                (robotLoc.getObstacleImage().equals("junk10") ||
                                                                robotLoc.getObstacleImage().equals("junk20"))) {
                                        robotLoc.setObstacleImage("");
                                        RescueAdapter.refresh();
                                        result = true;
                                }
                        } else if (action.equals("get_closest_unknown")) {
                                // Parameters: current x, current y
                                int currX = (int) params[0];
                                int currY = (int) params[1];

                                // Find closest unknown cell
                                List<Cell> unknownCells = worldMap.getUnknownCells();

                                Cell closest = null;
                                int minDistance = Integer.MAX_VALUE;

                                for (Cell unknown : unknownCells) {
                                        int distance = Math.abs(unknown.getX() - currX)
                                                        + Math.abs(unknown.getY() - currY);
                                        if (distance < minDistance) {
                                                minDistance = distance;
                                                closest = unknown;
                                        }
                                }

                                if (closest != null) {
                                        // Return the closest cell
                                        RescueAdapter.log("Closest unknown cell: " + closest.getX() + ","
                                                        + closest.getY());
                                        result = true;
                                }
                        }
                } catch (Exception e) {
                        logger.severe("Error executing action " + action + ": " + e);
                }

                // Update percepts after action execution
                updatePercepts();
                return result;
        }
}