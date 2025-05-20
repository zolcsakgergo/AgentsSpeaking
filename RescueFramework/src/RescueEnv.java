import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.Random;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

public class RescueEnv extends Environment {

    public static final int GSize = 20; // grid size
    public static final int FIRE = 16; // fire code in grid model
    public static final int JUNK = 32; // junk code in grid model
    public static final int OBSTACLE = 128; // obstacle code in grid model
    public static final int WALL = 64; // wall code in grid model

    static Logger logger = Logger.getLogger(RescueEnv.class.getName());

    private RescueModel model;
    private RescueView view;

    @Override
    public void init(String[] args) {
        model = new RescueModel();
        view = new RescueView(model);
        model.setView(view);
        model.setEnv(this); // Pass reference to RescueEnv
        updatePercepts();
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        logger.info(ag + " doing: " + action);
        boolean result = false;

        try {
            if (action.getFunctor().equals("move_towards")) {
                int x = (int) ((NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((NumberTerm) action.getTerm(1)).solve();
                model.moveTowards(ag, x, y);
                result = true;
            } else if (action.getFunctor().equals("reduce_intensity")) {
                int amount = 200;
                if (action.getArity() > 0) {
                    amount = (int) ((NumberTerm) action.getTerm(0)).solve();
                }
                model.reduceIntensity(ag, amount);
                result = true;
            } else if (action.getFunctor().equals("clean_junk")) {
                model.cleanJunk(ag);
                result = true;
            } else {
                logger.warning(ag + " attempted unsupported action: " + action);
            }
        } catch (Exception e) {
            logger.severe(ag + " action failed: " + action + " - " + e.getMessage());
            e.printStackTrace();
        }

        if (result) {
            // Ensure percepts are updated after each action
            updatePercepts();

            try {
                Thread.sleep(100);
            } catch (Exception e) {
                logger.warning("Sleep interrupted: " + e.getMessage());
            }

            // Update the view once all changes are done
            if (view != null) {
                view.repaint();
            }

            informAgsEnvironmentChanged();
        }

        return result;
    }

    /** creates the agents perception based on the model */
    void updatePercepts() {
        // First, completely clear all percepts for both agents
        clearPercepts("firefighter");
        clearPercepts("cleaner");

        // Add positions of agents
        Location firefighterLoc = model.getAgPos(0);
        Location cleanerLoc = model.getAgPos(1);

        Literal pos1 = Literal.parseLiteral("pos(firefighter," + firefighterLoc.x + "," + firefighterLoc.y + ")");
        Literal pos2 = Literal.parseLiteral("pos(cleaner," + cleanerLoc.x + "," + cleanerLoc.y + ")");

        // Add position percepts to both agents
        addPercept("firefighter", pos1);
        addPercept("firefighter", pos2);
        addPercept("cleaner", pos1);
        addPercept("cleaner", pos2);

        // Count fires and junks for logging
        int fireCount = 0;
        int junkCount = 0;

        for (int x = 0; x < GSize; x++) {
            for (int y = 0; y < GSize; y++) {
                Location loc = new Location(x, y);
                if (model.hasObject(FIRE, loc)) {
                    int intensity = model.getFireIntensity(loc);
                    Literal fire = Literal.parseLiteral("fire(" + x + "," + y + "," + intensity + ")");
                    addPercept("firefighter", fire);
                    fireCount++;
                }
                if (model.hasObject(JUNK, loc)) {
                    String junkType = model.getJunkType(loc);
                    Literal junk = Literal.parseLiteral("junk(" + x + "," + y + ",\"" + junkType + "\")");
                    addPercept("cleaner", junk);
                    junkCount++;
                }
            }
        }

        // Log total count of items for debugging
        logger.info("Environment updated - Total fires: " + fireCount + ", Total junks: " + junkCount);
    }

    class RescueModel extends GridWorldModel {
        private Map<Location, Integer> fireIntensities = new HashMap<>();
        private Map<Location, String> junkTypes = new HashMap<>();
        private Random random = new Random(System.currentTimeMillis());
        private RescueEnv env; // Reference to the environment

        // For A* pathfinding
        private static class PathNode implements Comparable<PathNode> {
            Location loc;
            int gCost; // Cost from start
            int hCost; // Heuristic cost to goal
            int fCost; // Total cost (g + h)
            PathNode parent;

            public PathNode(Location loc, int gCost, int hCost, PathNode parent) {
                this.loc = loc;
                this.gCost = gCost;
                this.hCost = hCost;
                this.fCost = gCost + hCost;
                this.parent = parent;
            }

            @Override
            public int compareTo(PathNode other) {
                // Compare by fCost (lower is better)
                int result = Integer.compare(this.fCost, other.fCost);

                // If fCost is the same, prefer the node with lower hCost
                if (result == 0) {
                    result = Integer.compare(this.hCost, other.hCost);
                }

                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof PathNode) {
                    return this.loc.equals(((PathNode) obj).loc);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return loc.hashCode();
            }
        }

        private RescueModel() {
            super(GSize, GSize, 2);

            // initial location of agents
            try {
                setAgPos(0, 0, 0); // firefighter at top-left
                setAgPos(1, GSize - 1, GSize - 1); // cleaner at bottom-right
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Create walls and doors to divide rooms
            createRoomDividers();

            // Initialize the rooms with random items
            initializeRooms();

            // Start thread for continuous spawning of new items
            startItemSpawner();
        }

        // Set reference to environment
        public void setEnv(RescueEnv env) {
            this.env = env;
        }

        // Define room boundaries
        private static final int STORAGE_START_ROW = 0;
        private static final int STORAGE_END_ROW = 9;
        private static final int CONTROL_START_ROW = 10;
        private static final int CONTROL_END_ROW = 14;
        private static final int HIBERNATION_START_ROW = 15;
        private static final int HIBERNATION_END_ROW = 19;

        private void initializeRooms() {
            // Storage room (first half) - only junk
            for (int i = 0; i < 8; i++) {
                int x = random.nextInt(GSize);
                int y = random.nextInt(STORAGE_END_ROW - STORAGE_START_ROW + 1) + STORAGE_START_ROW;
                if (!hasObject(JUNK, x, y) && !hasObject(OBSTACLE, x, y) && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                    addJunk(x, y, "junk");
                }
            }

            // Control room (upper half of second half) - only fires
            for (int i = 0; i < 5; i++) {
                int x = random.nextInt(GSize);
                int y = random.nextInt(CONTROL_END_ROW - CONTROL_START_ROW + 1) + CONTROL_START_ROW;
                if (!hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y) && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                    int intensity = random.nextInt(100) + 200;
                    addFire(x, y, intensity);
                }
            }

            // Hibernation room (lower half of second half) - both junk and fires
            for (int i = 0; i < 3; i++) {
                // Add junk
                int x1 = random.nextInt(GSize);
                int y1 = random.nextInt(HIBERNATION_END_ROW - HIBERNATION_START_ROW + 1) + HIBERNATION_START_ROW;
                if (!hasObject(JUNK, x1, y1) && !hasObject(FIRE, x1, y1) && !hasObject(OBSTACLE, x1, y1)
                        && !hasObject(WALL, x1, y1) && !hasAgent(x1, y1)) {
                    addJunk(x1, y1, "junk");
                }

                // Add fire
                int x2 = random.nextInt(GSize);
                int y2 = random.nextInt(HIBERNATION_END_ROW - HIBERNATION_START_ROW + 1) + HIBERNATION_START_ROW;
                if (!hasObject(JUNK, x2, y2) && !hasObject(FIRE, x2, y2) && !hasObject(OBSTACLE, x2, y2)
                        && !hasObject(WALL, x2, y2) && !hasAgent(x2, y2)) {
                    int intensity = random.nextInt(100) + 200;
                    addFire(x2, y2, intensity);
                }
            }
        }

        private Thread spawnerThread;

        private void startItemSpawner() {
            spawnerThread = new Thread(() -> {
                while (true) {
                    try {
                        // Sleep for a random time between 5-15 seconds
                        // Thread.sleep(500 + random.nextInt(2000));
                        Thread.sleep(5000 + random.nextInt(5000));

                        // Randomly decide what to spawn and where
                        int roomType = random.nextInt(3); // 0=storage, 1=control, 2=hibernation

                        int x = random.nextInt(GSize);
                        int y;
                        boolean itemAdded = false;

                        switch (roomType) {
                            case 0: // Storage room - add junk
                                y = random.nextInt(STORAGE_END_ROW - STORAGE_START_ROW + 1) + STORAGE_START_ROW;
                                if (!hasObject(JUNK, x, y) && !hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y)
                                        && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                                    addJunk(x, y, "junk");
                                    itemAdded = true;
                                }
                                break;

                            case 1: // Control room - add fire
                                y = random.nextInt(CONTROL_END_ROW - CONTROL_START_ROW + 1) + CONTROL_START_ROW;
                                if (!hasObject(JUNK, x, y) && !hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y)
                                        && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                                    int intensity = random.nextInt(100) + 200;
                                    addFire(x, y, intensity);
                                    itemAdded = true;
                                }
                                break;

                            case 2: // Hibernation room - add either junk or fire
                                y = random.nextInt(HIBERNATION_END_ROW - HIBERNATION_START_ROW + 1)
                                        + HIBERNATION_START_ROW;
                                if (!hasObject(JUNK, x, y) && !hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y)
                                        && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                                    if (random.nextBoolean()) {
                                        addJunk(x, y, "junk");
                                    } else {
                                        int intensity = random.nextInt(100) + 200;
                                        addFire(x, y, intensity);
                                    }
                                    itemAdded = true;
                                }
                                break;
                        }

                        // If an item was added, update the view and notify agents
                        if (itemAdded) {
                            updateEnvironment();
                        }

                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });

            spawnerThread.setDaemon(true); // Make this a daemon thread so it doesn't prevent JVM shutdown
            spawnerThread.start();
        }

        // Method to update the view and percepts when new items are spawned
        private void updateEnvironment() {
            // Update percepts
            if (view != null) {
                view.repaint();
            }

            // Update percepts and inform agents
            if (env != null) {
                env.updatePercepts();
                env.informAgsEnvironmentChanged();
            }
        }

        // Helper method to check if a location has an agent
        private boolean hasAgent(int x, int y) {
            Location loc = new Location(x, y);
            for (int i = 0; i < 2; i++) {
                if (getAgPos(i) != null && getAgPos(i).equals(loc)) {
                    return true;
                }
            }
            return false;
        }

        void addFire(int x, int y, int intensity) {
            add(FIRE, x, y);
            fireIntensities.put(new Location(x, y), intensity);
        }

        void addJunk(int x, int y, String type) {
            add(JUNK, x, y);
            junkTypes.put(new Location(x, y), type);
        }

        int getFireIntensity(Location loc) {
            Integer intensity = fireIntensities.get(loc);
            return intensity != null ? intensity : 0;
        }

        String getJunkType(Location loc) {
            String type = junkTypes.get(loc);
            return type != null ? type : "junk";
        }

        // Check if location is free for movement
        public boolean isFree(Location loc) {
            return loc.x >= 0 && loc.x < GSize &&
                    loc.y >= 0 && loc.y < GSize &&
                    !hasObject(OBSTACLE, loc) &&
                    !hasObject(WALL, loc);
        }

        // Enhanced version of isFree that checks agent-specific restrictions
        public boolean isFree(Location loc, int agId) {
            // Basic boundary checks
            if (loc.x < 0 || loc.x >= GSize || loc.y < 0 || loc.y >= GSize) {
                return false;
            }

            // Check for obstacles and walls
            if (hasObject(OBSTACLE, loc) || hasObject(WALL, loc)) {
                return false;
            }

            // For firefighter (agId 0), junk is an obstacle
            if (agId == 0 && hasObject(JUNK, loc)) {
                return false;
            }

            // For cleaner (agId 1), fire is an obstacle
            if (agId == 1 && hasObject(FIRE, loc)) {
                return false;
            }

            // Check if another agent is already at this location
            for (int i = 0; i < 2; i++) { // We know we have exactly 2 agents
                if (i != agId && getAgPos(i) != null && getAgPos(i).equals(loc)) {
                    return false;
                }
            }

            return true;
        }

        void moveTowards(String ag, int x, int y) throws Exception {
            int agId = ag.equals("firefighter") ? 0 : 1;
            Location loc = getAgPos(agId);
            Location targetLoc = new Location(x, y);

            // If already at target, no need to move
            if (loc.equals(targetLoc)) {
                return;
            }

            // Use A* to find the next step towards the target
            Location nextStep = findNextStep(loc, targetLoc, agId);

            if (nextStep != null) {
                setAgPos(agId, nextStep);
            } else {
                // If no path is found, try the old fallback movement
                fallbackMove(loc, targetLoc, agId);
            }
        }

        // A* pathfinding algorithm to find the next step towards the goal
        private Location findNextStep(Location start, Location goal, int agId) {
            // If start and goal are the same, return null (no need to move)
            if (start.equals(goal)) {
                return null;
            }

            logger.fine("Finding path from " + start + " to " + goal + " for agent " + agId);

            // Priority queue for open nodes (to be evaluated)
            java.util.PriorityQueue<PathNode> openSet = new java.util.PriorityQueue<>();

            // Set for closed nodes (already evaluated)
            java.util.Set<Location> closedSet = new java.util.HashSet<>();

            // Start with the initial node
            PathNode startNode = new PathNode(start, 0, calculateHeuristic(start, goal), null);
            openSet.add(startNode);

            // Map to keep track of nodes for faster retrieval
            java.util.Map<Location, PathNode> nodeMap = new java.util.HashMap<>();
            nodeMap.put(start, startNode);

            // Track exploration
            int nodesExplored = 0;

            // Continue searching while there are nodes to evaluate
            while (!openSet.isEmpty()) {
                // Get the node with the lowest fCost
                PathNode current = openSet.poll();
                nodesExplored++;

                // If we reached the goal, reconstruct the path and return the first step
                if (current.loc.equals(goal)) {
                    Location nextStep = getFirstStepInPath(current);
                    logger.fine("Path found! Explored " + nodesExplored + " nodes, next step: " + nextStep);
                    return nextStep;
                }

                // Add the current node to the closed set
                closedSet.add(current.loc);

                // Check all neighbors (8 directions)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        // Skip the current node
                        if (dx == 0 && dy == 0) {
                            continue;
                        }

                        // Skip diagonal movement if either horizontal or vertical path is blocked
                        if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
                            Location horNeighbor = new Location(current.loc.x + dx, current.loc.y);
                            Location verNeighbor = new Location(current.loc.x, current.loc.y + dy);
                            if (!isFree(horNeighbor, agId) || !isFree(verNeighbor, agId)) {
                                continue;
                            }
                        }

                        Location neighborLoc = new Location(current.loc.x + dx, current.loc.y + dy);

                        // Skip if the neighbor is out of bounds, an obstacle, or already evaluated
                        if (!isFree(neighborLoc, agId) || closedSet.contains(neighborLoc)) {
                            continue;
                        }

                        // Calculate the gCost for this neighbor (distance from start)
                        // Add 14 for diagonal movement (approximately sqrt(2) * 10) and 10 for straight
                        // movement
                        int movementCost = (Math.abs(dx) == 1 && Math.abs(dy) == 1) ? 14 : 10;
                        int neighborGCost = current.gCost + movementCost;

                        // Check if the neighbor is already in the open set
                        PathNode neighborNode = nodeMap.get(neighborLoc);

                        // If the neighbor is not in the open set or has a better path now, update it
                        if (neighborNode == null || neighborGCost < neighborNode.gCost) {
                            int hCost = calculateHeuristic(neighborLoc, goal);

                            // If the neighbor is already in the open set, remove it
                            if (neighborNode != null) {
                                openSet.remove(neighborNode);
                            }

                            // Create a new node with the updated values
                            neighborNode = new PathNode(neighborLoc, neighborGCost, hCost, current);

                            // Add the node to the open set and the node map
                            openSet.add(neighborNode);
                            nodeMap.put(neighborLoc, neighborNode);
                        }
                    }
                }
            }

            // If we reach here, no path was found, log the failure
            logger.warning("No path found from " + start + " to " + goal + " for agent " + agId +
                    ". Explored " + nodesExplored + " nodes. Falling back to direct movement.");
            return null;
        }

        // Calculate the heuristic cost (Manhattan distance) from a node to the goal
        private int calculateHeuristic(Location start, Location goal) {
            return Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y);
        }

        // Get the first step in the path reconstructed from the goal node
        private Location getFirstStepInPath(PathNode goalNode) {
            // If the parent of the goal node is null, return the goal node itself
            if (goalNode.parent == null) {
                return goalNode.loc;
            }

            // Backtrack to find the first step from the start node
            PathNode current = goalNode;
            while (current.parent != null && current.parent.parent != null) {
                current = current.parent;
            }

            // Return the first step in the path
            return current.loc;
        }

        // Fallback movement when A* fails
        private void fallbackMove(Location loc, Location target, int agId) throws Exception {
            // Calculate direction
            int dx = 0;
            int dy = 0;

            if (loc.x < target.x)
                dx = 1;
            else if (loc.x > target.x)
                dx = -1;

            if (loc.y < target.y)
                dy = 1;
            else if (loc.y > target.y)
                dy = -1;

            // Try to move in both directions
            boolean moved = false;

            // First try: move in both directions if possible
            Location newLoc = new Location(loc.x + dx, loc.y + dy);
            if (isFree(newLoc, agId)) {
                setAgPos(agId, newLoc);
                moved = true;
            } else {
                // Second try: move horizontally
                newLoc = new Location(loc.x + dx, loc.y);
                if (dx != 0 && isFree(newLoc, agId)) {
                    setAgPos(agId, newLoc);
                    moved = true;
                } else {
                    // Third try: move vertically
                    newLoc = new Location(loc.x, loc.y + dy);
                    if (dy != 0 && isFree(newLoc, agId)) {
                        setAgPos(agId, newLoc);
                        moved = true;
                    }
                }
            }

            // If we couldn't move at all, try a random direction
            if (!moved) {
                // Try random directions until one works or we've tried all options
                int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                // Shuffle directions
                for (int i = 0; i < directions.length; i++) {
                    int j = random.nextInt(directions.length);
                    int[] temp = directions[i];
                    directions[i] = directions[j];
                    directions[j] = temp;
                }

                for (int[] dir : directions) {
                    newLoc = new Location(loc.x + dir[0], loc.y + dir[1]);
                    if (isFree(newLoc, agId)) {
                        setAgPos(agId, newLoc);
                        moved = true;
                        break;
                    }
                }
            }
        }

        void reduceIntensity(String ag, int amount) {
            if (ag.equals("firefighter")) {
                Location loc = getAgPos(0);
                if (hasObject(FIRE, loc)) {
                    Integer intensity = fireIntensities.get(loc);
                    if (intensity != null) {
                        intensity -= amount;
                        if (intensity <= 0) {
                            remove(FIRE, loc);
                            fireIntensities.remove(loc);
                            logger.info("Fire at " + loc + " extinguished and removed");

                            // Call updateEnvironment to ensure percepts are refreshed
                            updateEnvironment();
                        } else {
                            fireIntensities.put(loc, intensity);
                        }
                    }
                }
            }
        }

        void cleanJunk(String ag) {
            if (ag.equals("cleaner")) {
                Location loc = getAgPos(1);
                if (hasObject(JUNK, loc)) {
                    // Properly remove the junk from both the grid and the map
                    remove(JUNK, loc);
                    junkTypes.remove(loc);
                    logger.info("Junk at " + loc + " cleaned and removed");

                    // Call updateEnvironment to ensure percepts are refreshed
                    updateEnvironment();
                }
            }
        }

        // Create walls and doors to divide the rooms
        private void createRoomDividers() {
            // Create wall between Storage and Control rooms (at row border 9-10)
            createHorizontalWall(0, GSize - 1, 10);

            // Create wall between Control and Hibernation rooms (at row border 14-15)
            createHorizontalWall(0, GSize - 1, 15);

            // Create door between Storage and Control rooms (in the upper half)
            // Door is 2 cells wide, around 1/4 of the way across
            int doorPos1 = GSize / 4;
            remove(WALL, doorPos1, 10);
            remove(WALL, doorPos1 + 1, 10);

            // Create door between Control and Hibernation rooms (in middle)
            // Door is 2 cells wide, in the middle
            int doorPos2 = GSize / 2;
            remove(WALL, doorPos2, 15);
            remove(WALL, doorPos2 + 1, 15);

            logger.info("Room dividers created with doors at positions " + doorPos1 +
                    " and " + doorPos2);
        }

        // Create a horizontal wall between start and end x coordinates at the given y
        // coordinate
        private void createHorizontalWall(int startX, int endX, int y) {
            for (int x = startX; x <= endX; x++) {
                add(WALL, x, y);
            }
        }
    }

    class RescueView extends GridWorldView {
        private final RescueModel env;

        public RescueView(RescueModel model) {
            super(model, "Rescue World", 600);
            this.env = model;
            defaultFont = new Font("Arial", Font.BOLD, 12);
            setVisible(true);
            repaint();
        }

        public void update() {
            repaint();
        }

        /** draw application objects */
        @Override
        public void draw(Graphics g, int x, int y, int object) {
            switch (object) {
                case RescueEnv.FIRE:
                    drawFire(g, x, y);
                    break;
                case RescueEnv.JUNK:
                    drawJunk(g, x, y);
                    break;
                case RescueEnv.WALL:
                    drawWall(g, x, y);
                    break;
            }
        }

        @Override
        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            String label = id == 0 ? "FF" : "CL";
            c = id == 0 ? Color.red : Color.blue;

            super.drawAgent(g, x, y, c, -1);
            g.setColor(Color.white);
            super.drawString(g, x, y, defaultFont, label);
        }

        public void drawFire(Graphics g, int x, int y) {
            g.setColor(Color.orange);
            g.fillRect(x * cellSizeW + 2, y * cellSizeH + 2, cellSizeW - 4, cellSizeH - 4);

            int intensity = env.getFireIntensity(new Location(x, y));
            g.setColor(Color.white);
            drawString(g, x, y, defaultFont, "F" + intensity);
        }

        public void drawJunk(Graphics g, int x, int y) {
            g.setColor(Color.darkGray);
            g.fillRect(x * cellSizeW + 2, y * cellSizeH + 2, cellSizeW - 4, cellSizeH - 4);

            String type = env.getJunkType(new Location(x, y));
            g.setColor(Color.white);
            drawString(g, x, y, defaultFont, type);
        }

        public void drawWall(Graphics g, int x, int y) {
            g.setColor(Color.black);
            g.fillRect(x * cellSizeW, y * cellSizeH, cellSizeW, cellSizeH);
        }
    }
}