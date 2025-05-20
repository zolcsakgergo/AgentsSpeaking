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
    public static final int UNKN = 64; // unknown area code
    public static final int OBSTACLE = 128; // obstacle code in grid model

    static Logger logger = Logger.getLogger(RescueEnv.class.getName());

    private RescueModel model;
    private RescueView  view;

    @Override
    public void init(String[] args) {
        model = new RescueModel();
        view  = new RescueView(model);
        model.setView(view);
        updatePercepts();
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        logger.info(ag + " doing: " + action);
        boolean result = false;
        
        try {
            if (action.getFunctor().equals("move_towards")) {
                int x = (int)((NumberTerm)action.getTerm(0)).solve();
                int y = (int)((NumberTerm)action.getTerm(1)).solve();
                model.moveTowards(ag, x, y);
                result = true;
            } else if (action.getFunctor().equals("reduce_intensity")) {
                int amount = 200;
                if (action.getArity() > 0) {
                    amount = (int)((NumberTerm)action.getTerm(0)).solve();
                }
                model.reduceIntensity(ag, amount);
                result = true;
            } else if (action.getFunctor().equals("clean_junk")) {
                model.cleanJunk(ag);
                result = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (result) {
            updatePercepts();
            
            try {
                Thread.sleep(100);
            } catch (Exception e) {}
            
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
        clearPercepts();

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

        // Add fires, junk and unknown areas
        for (int x = 0; x < GSize; x++) {
            for (int y = 0; y < GSize; y++) {
                Location loc = new Location(x, y);
                if (model.hasObject(FIRE, loc)) {
                    int intensity = model.getFireIntensity(loc);
                    Literal fire = Literal.parseLiteral("fire(" + x + "," + y + "," + intensity + ")");
                    //addPercept("firefighter", fire);
                }
                if (model.hasObject(JUNK, loc)) {
                    String junkType = model.getJunkType(loc);
                    Literal junk = Literal.parseLiteral("junk(" + x + "," + y + ",\"" + junkType + "\")");
                    //addPercept("cleaner", junk);
                }
                if (model.hasObject(UNKN, loc)) {
                    Literal unknown = Literal.parseLiteral("unknown(" + x + "," + y + ")");
                    addPercept("firefighter", unknown);
                    addPercept("cleaner", unknown);
                }
            }
        }
    }

    class RescueModel extends GridWorldModel {
        private Map<Location, Integer> fireIntensities = new HashMap<>();
        private Map<Location, String> junkTypes = new HashMap<>();
        private Random random = new Random(System.currentTimeMillis());

        private RescueModel() {
            super(GSize, GSize, 2);

            // initial location of agents
            try {
                setAgPos(0, 0, 0); // firefighter at top-left
                setAgPos(1, GSize-1, GSize-1); // cleaner at bottom-right
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Add some fires to start with
            addFire(3, 4, 500);
            addFire(15, 7, 800);
            addFire(10, 15, 600);

            // Add some junk
            addJunk(5, 7, "junk");
            addJunk(12, 3, "junk");
            addJunk(12, 7, "junk");
            addJunk(8, 16, "junk");

            // Add some unknown areas
            for (int i = 0; i < 10; i++) {
                int x = random.nextInt(GSize);
                int y = random.nextInt(GSize);
                add(UNKN, x, y);
            }
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
            return type != null ? type : "junk10";
        }

        // Check if location is free for movement
        public boolean isFree(Location loc) {
            return loc.x >= 0 && loc.x < GSize && 
                   loc.y >= 0 && loc.y < GSize && 
                   !hasObject(OBSTACLE, loc);
        }
        
        // Enhanced version of isFree that checks agent-specific restrictions
        public boolean isFree(Location loc, int agId) {
            // Basic boundary checks
            if (loc.x < 0 || loc.x >= GSize || loc.y < 0 || loc.y >= GSize) {
                return false;
            }
            
            // Check for obstacles
            if (hasObject(OBSTACLE, loc)) {
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
            Location oldLoc = new Location(loc.x, loc.y); // Remember old location
            
            // Calculate direction
            int dx = 0;
            int dy = 0;
            
            if (loc.x < x) dx = 1;
            else if (loc.x > x) dx = -1;
            
            if (loc.y < y) dy = 1;
            else if (loc.y > y) dy = -1;
            
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
            
            // If we couldn't move at all, try a random direction to break out of potential deadlock
            if (!moved && !loc.equals(oldLoc)) {
                // Try random directions until one works or we've tried all options
                int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
                // Shuffle directions
                Random r = new Random();
                for(int i = 0; i < directions.length; i++) {
                    int j = r.nextInt(directions.length);
                    int[] temp = directions[i];
                    directions[i] = directions[j];
                    directions[j] = temp;
                }
                
                for(int[] dir : directions) {
                    newLoc = new Location(loc.x + dir[0], loc.y + dir[1]);
                    if (isFree(newLoc, agId)) {
                        setAgPos(agId, newLoc);
                        moved = true;
                        break;
                    }
                }
            }

            // Discover area around agent regardless of movement
            discoverArea(getAgPos(agId), 2);
            
            // Do NOT repaint here - let the main execution loop handle repainting
            // to avoid flickering caused by too many repaints
        }

        void discoverArea(Location center, int radius) {
            for (int x = center.x - radius; x <= center.x + radius; x++) {
                for (int y = center.y - radius; y <= center.y + radius; y++) {
                    if (x >= 0 && x < GSize && y >= 0 && y < GSize) {
                        Location loc = new Location(x, y);
                        if (hasObject(UNKN, loc)) {
                            remove(UNKN, loc);
                        }
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
                }
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
                case RescueEnv.UNKN:
                    drawUnknown(g, x, y);
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

        public void drawUnknown(Graphics g, int x, int y) {
            g.setColor(Color.lightGray);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
            g.setColor(Color.black);
            drawString(g, x, y, defaultFont, "?");
        }
    }
} 