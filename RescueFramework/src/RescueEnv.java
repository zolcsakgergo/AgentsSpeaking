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

    public static final int GSize = 20;
    public static final int FIRE = 16;
    public static final int JUNK = 32;
    public static final int OBSTACLE = 128;
    public static final int WALL = 64;

    static Logger logger = Logger.getLogger(RescueEnv.class.getName());

    private RescueModel model;
    private RescueView view;

    @Override
    public void init(String[] args) {
        model = new RescueModel();
        view = new RescueView(model);
        model.setView(view);
        model.setEnv(this);
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
            updatePercepts();

            try {
                Thread.sleep(100);
            } catch (Exception e) {
                logger.warning("Sleep interrupted: " + e.getMessage());
            }

            if (view != null) {
                view.repaint();
            }

            informAgsEnvironmentChanged();
        }

        return result;
    }

    void updatePercepts() {
        clearPercepts("firefighter");
        clearPercepts("cleaner");

        Location firefighterLoc = model.getAgPos(0);
        Location cleanerLoc = model.getAgPos(1);

        Literal pos1 = Literal.parseLiteral("pos(firefighter," + firefighterLoc.x + "," + firefighterLoc.y + ")");
        Literal pos2 = Literal.parseLiteral("pos(cleaner," + cleanerLoc.x + "," + cleanerLoc.y + ")");

        addPercept("firefighter", pos1);
        addPercept("firefighter", pos2);
        addPercept("cleaner", pos1);
        addPercept("cleaner", pos2);

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

        logger.info("Environment updated - Total fires: " + fireCount + ", Total junks: " + junkCount);
    }

    class RescueModel extends GridWorldModel {
        private Map<Location, Integer> fireIntensities = new HashMap<>();
        private Map<Location, String> junkTypes = new HashMap<>();
        private Random random = new Random(System.currentTimeMillis());
        private RescueEnv env;

        private static class PathNode implements Comparable<PathNode> {
            Location loc;
            int gCost;
            int hCost;
            int fCost;
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
                int result = Integer.compare(this.fCost, other.fCost);

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

            try {
                setAgPos(0, 0, 0);
                setAgPos(1, GSize - 1, GSize - 1);
            } catch (Exception e) {
                e.printStackTrace();
            }

            createRoomDividers();

            initializeRooms();

            startItemSpawner();
        }

        public void setEnv(RescueEnv env) {
            this.env = env;
        }

        private static final int STORAGE_START_ROW = 0;
        private static final int STORAGE_END_ROW = 9;
        private static final int CONTROL_START_ROW = 10;
        private static final int CONTROL_END_ROW = 14;
        private static final int HIBERNATION_START_ROW = 15;
        private static final int HIBERNATION_END_ROW = 19;

        private void initializeRooms() {
            for (int i = 0; i < 8; i++) {
                int x = random.nextInt(GSize);
                int y = random.nextInt(STORAGE_END_ROW - STORAGE_START_ROW + 1) + STORAGE_START_ROW;
                if (!hasObject(JUNK, x, y) && !hasObject(OBSTACLE, x, y) && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                    addJunk(x, y, "junk");
                }
            }

            for (int i = 0; i < 5; i++) {
                int x = random.nextInt(GSize);
                int y = random.nextInt(CONTROL_END_ROW - CONTROL_START_ROW + 1) + CONTROL_START_ROW;
                if (!hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y) && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                    int intensity = random.nextInt(100) + 200;
                    addFire(x, y, intensity);
                }
            }

            for (int i = 0; i < 3; i++) {
                int x1 = random.nextInt(GSize);
                int y1 = random.nextInt(HIBERNATION_END_ROW - HIBERNATION_START_ROW + 1) + HIBERNATION_START_ROW;
                if (!hasObject(JUNK, x1, y1) && !hasObject(FIRE, x1, y1) && !hasObject(OBSTACLE, x1, y1)
                        && !hasObject(WALL, x1, y1) && !hasAgent(x1, y1)) {
                    addJunk(x1, y1, "junk");
                }

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
                        Thread.sleep(5000 + random.nextInt(5000));

                        int roomType = random.nextInt(3);

                        int x = random.nextInt(GSize);
                        int y;
                        boolean itemAdded = false;

                        switch (roomType) {
                            case 0:
                                y = random.nextInt(STORAGE_END_ROW - STORAGE_START_ROW + 1) + STORAGE_START_ROW;
                                if (!hasObject(JUNK, x, y) && !hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y)
                                        && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                                    addJunk(x, y, "junk");
                                    itemAdded = true;
                                }
                                break;

                            case 1:
                                y = random.nextInt(CONTROL_END_ROW - CONTROL_START_ROW + 1) + CONTROL_START_ROW;
                                if (!hasObject(JUNK, x, y) && !hasObject(FIRE, x, y) && !hasObject(OBSTACLE, x, y)
                                        && !hasObject(WALL, x, y) && !hasAgent(x, y)) {
                                    int intensity = random.nextInt(100) + 200;
                                    addFire(x, y, intensity);
                                    itemAdded = true;
                                }
                                break;

                            case 2:
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

                        if (itemAdded) {
                            updateEnvironment();
                        }

                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });

            spawnerThread.setDaemon(true);
            spawnerThread.start();
        }

        private void updateEnvironment() {
            if (view != null) {
                view.repaint();
            }

            if (env != null) {
                env.updatePercepts();
                env.informAgsEnvironmentChanged();
            }
        }

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

        public boolean isFree(Location loc) {
            return loc.x >= 0 && loc.x < GSize &&
                    loc.y >= 0 && loc.y < GSize &&
                    !hasObject(OBSTACLE, loc) &&
                    !hasObject(WALL, loc);
        }

        public boolean isFree(Location loc, int agId) {
            if (loc.x < 0 || loc.x >= GSize || loc.y < 0 || loc.y >= GSize) {
                return false;
            }

            if (hasObject(OBSTACLE, loc) || hasObject(WALL, loc)) {
                return false;
            }

            if (agId == 0 && hasObject(JUNK, loc)) {
                return false;
            }

            if (agId == 1 && hasObject(FIRE, loc)) {
                return false;
            }

            for (int i = 0; i < 2; i++) {
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

            if (loc.equals(targetLoc)) {
                return;
            }

            Location nextStep = findNextStep(loc, targetLoc, agId);

            if (nextStep != null) {
                setAgPos(agId, nextStep);
            } else {
                fallbackMove(loc, targetLoc, agId);
            }
        }

        private Location findNextStep(Location start, Location goal, int agId) {
            if (start.equals(goal)) {
                return null;
            }

            logger.fine("Finding path from " + start + " to " + goal + " for agent " + agId);

            java.util.PriorityQueue<PathNode> openSet = new java.util.PriorityQueue<>();

            java.util.Set<Location> closedSet = new java.util.HashSet<>();

            PathNode startNode = new PathNode(start, 0, calculateHeuristic(start, goal), null);
            openSet.add(startNode);

            java.util.Map<Location, PathNode> nodeMap = new java.util.HashMap<>();
            nodeMap.put(start, startNode);

            int nodesExplored = 0;

            while (!openSet.isEmpty()) {
                PathNode current = openSet.poll();
                nodesExplored++;

                if (current.loc.equals(goal)) {
                    Location nextStep = getFirstStepInPath(current);
                    logger.fine("Path found! Explored " + nodesExplored + " nodes, next step: " + nextStep);
                    return nextStep;
                }

                closedSet.add(current.loc);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }

                        if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
                            Location horNeighbor = new Location(current.loc.x + dx, current.loc.y);
                            Location verNeighbor = new Location(current.loc.x, current.loc.y + dy);
                            if (!isFree(horNeighbor, agId) || !isFree(verNeighbor, agId)) {
                                continue;
                            }
                        }

                        Location neighborLoc = new Location(current.loc.x + dx, current.loc.y + dy);

                        if (!isFree(neighborLoc, agId) || closedSet.contains(neighborLoc)) {
                            continue;
                        }

                        int movementCost = (Math.abs(dx) == 1 && Math.abs(dy) == 1) ? 14 : 10;
                        int neighborGCost = current.gCost + movementCost;

                        PathNode neighborNode = nodeMap.get(neighborLoc);

                        if (neighborNode == null || neighborGCost < neighborNode.gCost) {
                            int hCost = calculateHeuristic(neighborLoc, goal);

                            if (neighborNode != null) {
                                openSet.remove(neighborNode);
                            }

                            neighborNode = new PathNode(neighborLoc, neighborGCost, hCost, current);

                            openSet.add(neighborNode);
                            nodeMap.put(neighborLoc, neighborNode);
                        }
                    }
                }
            }

            logger.warning("No path found from " + start + " to " + goal + " for agent " + agId +
                    ". Explored " + nodesExplored + " nodes. Falling back to direct movement.");
            return null;
        }

        private int calculateHeuristic(Location start, Location goal) {
            return Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y);
        }

        private Location getFirstStepInPath(PathNode goalNode) {
            if (goalNode.parent == null) {
                return goalNode.loc;
            }

            PathNode current = goalNode;
            while (current.parent != null && current.parent.parent != null) {
                current = current.parent;
            }

            return current.loc;
        }

        private void fallbackMove(Location loc, Location target, int agId) throws Exception {
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

            boolean moved = false;

            Location newLoc = new Location(loc.x + dx, loc.y + dy);
            if (isFree(newLoc, agId)) {
                setAgPos(agId, newLoc);
                moved = true;
            } else {
                newLoc = new Location(loc.x + dx, loc.y);
                if (dx != 0 && isFree(newLoc, agId)) {
                    setAgPos(agId, newLoc);
                    moved = true;
                } else {
                    newLoc = new Location(loc.x, loc.y + dy);
                    if (dy != 0 && isFree(newLoc, agId)) {
                        setAgPos(agId, newLoc);
                        moved = true;
                    }
                }
            }

            if (!moved) {
                int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
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
                    remove(JUNK, loc);
                    junkTypes.remove(loc);
                    logger.info("Junk at " + loc + " cleaned and removed");

                    updateEnvironment();
                }
            }
        }

        private void createRoomDividers() {
            createHorizontalWall(0, GSize - 1, 10);

            createHorizontalWall(0, GSize - 1, 15);

            int doorPos1 = GSize / 4;
            remove(WALL, doorPos1, 10);
            remove(WALL, doorPos1 + 1, 10);

            int doorPos2 = GSize / 2;
            remove(WALL, doorPos2, 15);
            remove(WALL, doorPos2 + 1, 15);

            logger.info("Room dividers created with doors at positions " + doorPos1 +
                    " and " + doorPos2);
        }

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