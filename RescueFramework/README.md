# Rescue Robots Multi-Agent System

This project implements a multi-agent rescue simulation using Jason agent technology, where autonomous agents (firefighter and cleaner robots) operate within a dynamic environment to detect and handle emergencies.

## System Overview

The simulation divides the world into three distinct rooms connected by doors:

- **Storage Room** (rows 0-9)
- **Control Room** (rows 10-14)
- **Hibernation Room** (rows 15-19)

Each agent operates with its own beliefs, goals, and plans to navigate through rooms, detect tasks, and respond to emergencies.

## Agent Implementations

### Cleaner Agent (`cleaner.asl`)

The cleaner agent is responsible for finding and cleaning junk in the environment. Its behavior is governed by a set of beliefs, goals, and plans.

#### Initial Beliefs and Goals

```
storage_room(0, 9).
control_room(10, 14).
hibernation_room(15, 19).

door1_pos(5, 10).
door2_pos(10, 15).

patrol_x(0).
patrol_y(0).
patrol_dir(right).

current_room(1).

patrol_forward(true).
patrol_row(0).
patrol_col(0).
```

These beliefs define the room boundaries, door positions, and initial patrol parameters. The agent starts in room 1 (storage room) and will move in a snake pattern.

#### The Main Plan Structure

The agent follows this basic cycle:

1. Initialize with beliefs about the environment
2. Look for junk to clean
3. If junk exists, find the closest one and clean it
4. If no junk exists, patrol systematically
5. React immediately to new junk perceptions

#### Junk Cleanup Logic

```
+!clean_junk : junk(_,_,_)
   <- ?pos(cleaner,MX,MY);

      .findall(junk(X,Y,Type), junk(X,Y,Type), Junks);
      .print("Currently perceiving ", .length(Junks), " junk items");

      if (.length(Junks) > 0) {
         !find_closest_junk(MX, MY, Junks, ClosestX, ClosestY, ClosestType);
         .print("Found closest junk at ", ClosestX, ", ", ClosestY, " of type ", ClosestType);
         !go_to(ClosestX, ClosestY);
         .print("Cleaning junk at ", ClosestX, ", ", ClosestY);

         .abolish(junk(ClosestX, ClosestY, _));

         clean_junk;

         .wait(200);

         .drop_all_intentions;
         !clean_junk;
      } else {
         .print("No junk found. Starting patrol mode.");
         !patrol;
      }.
```

This plan is triggered when junk items exist (`:junk(_,_,_)` context condition). It:

1. Retrieves the agent's current position with `?pos(cleaner,MX,MY)`
2. Uses `.findall` to gather all junk beliefs into a list
3. Finds the closest junk using Manhattan distance with `!find_closest_junk`
4. Navigates to the junk location using `!go_to`
5. Removes the belief from its knowledge base with `.abolish`
6. Executes the environment action `clean_junk`
7. Waits for percepts to update, then restarts the process

The closest junk finding algorithm works recursively:

```
+!find_closest_junk(MX, MY, [junk(X,Y,Type)], X, Y, Type).

+!find_closest_junk(MX, MY, [junk(X,Y,Type)|Rest], ClosestX, ClosestY, ClosestType)
   <- Distance = math.abs(X-MX) + math.abs(Y-MY);
      !find_closest_junk(MX, MY, Rest, TmpX, TmpY, TmpType);
      TmpDistance = math.abs(TmpX-MX) + math.abs(TmpY-MY);
      if (Distance <= TmpDistance) {
         ClosestX = X;
         ClosestY = Y;
         ClosestType = Type;
      } else {
         ClosestX = TmpX;
         ClosestY = TmpY;
         ClosestType = TmpType;
      }.
```

The first rule handles the base case (a single junk), and the second rule processes a list, comparing each junk's distance to find the minimum.

#### Patrol Behavior

```
+!patrol
   <- ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_forward(Forward);

      !go_to(Col, Row);

      if (Forward) {
         if (Col > 0) {
            -+patrol_col(Col-1);
         } else {
            if (Row > 0) {
               -+patrol_row(Row-1);
               -+patrol_col(19);
            } else {
               -+patrol_forward(false);
               -+patrol_col(1);
            }
         }
      } else {
         if (Col < 19) {
            -+patrol_col(Col+1);
         } else {
            if (Row < 19) {
               -+patrol_row(Row+1);
               -+patrol_col(0);
            } else {
               -+patrol_forward(true);
               -+patrol_col(18);
            }
         }
      }

      .findall(junk(X,Y,Type), junk(X,Y,Type), Junks);
      if (.length(Junks) > 0) {
         .print("Junk detected during patrol. Switching to cleaning mode.");
         !clean_junk;
      } else {
         .wait(200);
         !patrol;
      }.
```

This patrol plan creates a zigzag search pattern:

1. Retrieves current patrol position from beliefs
2. Navigates to that position
3. Calculates the next position based on direction:
   - If moving forward (left), it decrements the column until reaching the edge
   - If moving backward (right), it increments the column until reaching the edge
   - When reaching a row boundary, it changes direction and moves to the next row
4. At the end of the grid, it reverses the overall direction
5. After each move, it checks for junk and switches to cleaning if found

#### The Movement Implementation

```
+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y).

+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving towards (", X, ",", Y, ")");
      move_towards(X,Y);
      .wait(100);
      !go_to(X,Y).
```

Movement uses two plans:

1. A plan that stops when the destination is reached
2. A recursive plan that moves toward the target position until arrival

The `move_towards` action is implemented in the environment, which uses the A\* pathfinding algorithm to determine the optimal path.

#### Room Transition

When transitioning between rooms, the agent must navigate through doors:

```
+!calculate_next_patrol_point
   <- ?patrol_x(X);
      ?patrol_y(Y);
      ?patrol_dir(Dir);
      ?current_room(Room);

      // Get current room boundaries
      if (Room == 1) {
         ?storage_room(MinY, MaxY);
         MaxX = 19;
      } else {
         if (Room == 2) {
            ?control_room(MinY, MaxY);
            MaxX = 19;
         } else {
            ?hibernation_room(MinY, MaxY);
            MaxX = 19;
         }
      }

      // [Direction handling logic...]
      // Special logic for wall rows (10 and 15)
      if (Y+1 == 10 | Y+1 == 15) {
         if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
            // At a door location, proceed normally
         } else {
            // Not at a door, skip this row
            -+patrol_y(Y+2);
         }
      }
```

This code detects when the agent would hit a wall and ensures it only crosses at door positions (X=5 for the first door, X=10 for the second door).

#### Reactive Behavior

The agent immediately responds to new junk through this reactive rule:

```
+junk(X,Y,Type) : not .intend(clean_junk) & not .intend(go_to(_,_))
   <- .print("Detected new junk at ", X, ", ", Y);
      !clean_junk.
```

This plan triggers when a new junk belief is added, as long as the agent isn't already cleaning or moving. It immediately drops current activities and switches to cleaning mode.

### Firefighter Agent (`firefighter.asl`)

The firefighter agent focuses on fire detection and extinguishing instead of junk cleaning.

#### Initial Beliefs and Goals

```
control_room(10, 14).
hibernation_room(15, 19).

door2_x(10).

current_room(3).

patrol_direction(false).
patrol_row(19).
patrol_col(19).
```

Unlike the cleaner, the firefighter starts in room 3 (hibernation room) and has different patrol parameters.

#### Fire Detection and Extinguishing

```
+!extinguish_fires : fire(_,_,_)
   <- ?pos(firefighter,MX,MY);

      .findall(fire(X,Y,I), fire(X,Y,I), Fires);
      .print("Currently perceiving ", .length(Fires), " fires");

      if (.length(Fires) > 0) {
         !find_closest_fire(MX, MY, Fires, ClosestX, ClosestY, Intensity);
         .print("Found closest fire at ", ClosestX, ", ", ClosestY, " with intensity ", Intensity);
         !go_to(ClosestX, ClosestY);
         .print("Reducing fire intensity at ", ClosestX, ", ", ClosestY);

         .abolish(fire(ClosestX, ClosestY, _));

         reduce_intensity(100);

         .wait(200);

         .drop_all_intentions;
         !extinguish_fires;
      } else {
         .print("No fires found. Starting patrol mode.");
         !patrol;
      }.
```

This plan mirrors the cleaner's junk handling but uses `reduce_intensity` instead of `clean_junk`.

#### Advanced Movement with Collision Avoidance

The firefighter implements a sophisticated obstacle avoidance system for the cleaner:

```
+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      if (pos(cleaner,NX,NY)) {
         MoveAround = false;

         // Check if cleaner is to the right
         if (math.abs(NX-(CX+1)) < 0.1) {
            if (math.abs(NY-CY) < 0.1) {
               if (CX < X) {
                  MoveAround = true;
               }
            }
         }

         // Check if cleaner is to the left
         if (math.abs(NX-(CX-1)) < 0.1) {
            if (math.abs(NY-CY) < 0.1) {
               if (CX > X) {
                  MoveAround = true;
               }
            }
         }

         // Check if cleaner is below
         if (math.abs(NX-CX) < 0.1) {
            if (math.abs(NY-(CY+1)) < 0.1) {
               if (CY < Y) {
                  MoveAround = true;
               }
            }
         }

         // Check if cleaner is above
         if (math.abs(NX-CX) < 0.1) {
            if (math.abs(NY-(CY-1)) < 0.1) {
               if (CY > Y) {
                  MoveAround = true;
               }
            }
         }

         // If cleaner is in the way, try to move around
         if (MoveAround) {
            if (CX < X) {
               move_towards(CX, CY+1);
            } else if (CX > X) {
               move_towards(CX, CY-1);
            } else if (CY < Y) {
               move_towards(CX+1, CY);
            } else {
               move_towards(CX-1, CY);
            }
         } else {
            // Normal movement towards target
            move_towards(X,Y);
         }
      } else {
         // Normal movement towards target
         move_towards(X,Y);
      }
      .wait(100);
      !go_to(X,Y).
```

This plan:

1. Checks if the cleaner agent is adjacent in all four directions
2. Determines if the cleaner is blocking the direct path to the target
3. If blocked, calculates an alternative path around the cleaner
4. Otherwise, moves directly toward the target

## Environment Implementation (`RescueEnv.java`)

The environment class is the foundation of the simulation, providing the grid world, perception handling, and agent action execution.

### Environment Setup

```java
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
        model.setEnv(this);
        updatePercepts();
    }
```

This code defines the environment configuration constants and initializes the model and view.

### Perception Generation

```java
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

    // Add fires and junk percepts
    for (int x = 0; x < GSize; x++) {
        for (int y = 0; y < GSize; y++) {
            Location loc = new Location(x, y);
            if (model.hasObject(FIRE, loc)) {
                int intensity = model.getFireIntensity(loc);
                Literal fire = Literal.parseLiteral("fire(" + x + "," + y + "," + intensity + ")");
                addPercept("firefighter", fire);
            }
            if (model.hasObject(JUNK, loc)) {
                String junkType = model.getJunkType(loc);
                Literal junk = Literal.parseLiteral("junk(" + x + "," + y + ",\"" + junkType + "\")");
                addPercept("cleaner", junk);
            }
        }
    }
}
```

This method refreshes the agents' perception of the environment:

1. Clears all existing percepts
2. Adds positions of both agents to both agents' perception
3. Adds fire percepts (only to firefighter)
4. Adds junk percepts (only to cleaner)

### Room and Door Configuration

```java
private static final int STORAGE_START_ROW = 0;
private static final int STORAGE_END_ROW = 9;
private static final int CONTROL_START_ROW = 10;
private static final int CONTROL_END_ROW = 14;
private static final int HIBERNATION_START_ROW = 15;
private static final int HIBERNATION_END_ROW = 19;

private void createRoomDividers() {
    // Create wall between Storage and Control rooms
    createHorizontalWall(0, GSize - 1, 10);

    // Create wall between Control and Hibernation rooms
    createHorizontalWall(0, GSize - 1, 15);

    // Create door between Storage and Control rooms
    int doorPos1 = GSize / 4; // Door at x=5, y=10
    remove(WALL, doorPos1, 10);
    remove(WALL, doorPos1 + 1, 10);

    // Create door between Control and Hibernation rooms
    int doorPos2 = GSize / 2; // Door at x=10, y=15
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
```

This code creates the three-room layout by:

1. Defining the row boundaries for each room
2. Creating horizontal walls at rows 10 and 15
3. Creating doors by removing wall sections at specific coordinates
4. Each door is two cells wide for easier navigation

### A\* Pathfinding Implementation

The environment uses A* (A-star) pathfinding for intelligent agent movement. A* is an informed search algorithm that combines:

- **Dijkstra's algorithm** - for favoring nodes close to the starting point (using gCost)
- **Greedy Best-First-Search** - for favoring nodes close to the goal (using hCost)

#### Core Components of A\*

The `PathNode` class forms the building blocks of the algorithm:

```java
private static class PathNode implements Comparable<PathNode> {
    Location loc;        // Grid location (x,y coordinates)
    int gCost;           // Cost from start to current node
    int hCost;           // Estimated cost from current node to goal (heuristic)
    int fCost;           // Total cost (g + h)
    PathNode parent;     // Previous node in the optimal path

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
        // This tie-breaking helps guide search toward the goal
        if (result == 0) {
            result = Integer.compare(this.hCost, other.hCost);
        }

        return result;
    }
}
```

#### Algorithm Workflow

The A\* search process:

1. **Initialize:**

   - Create an "open set" (priority queue) with the start node
   - Create an empty "closed set" for evaluated nodes
   - Calculate initial heuristic cost

2. **While open set is not empty:**

   - Pop the node with lowest fCost from the open set
   - If current node is the goal, reconstruct and return the path
   - Add current node to closed set (mark as evaluated)
   - For each neighbor (8 directions):
     - Skip if wall/obstacle or already evaluated
     - For diagonal movement, check if path is blocked
     - Calculate new path cost to this neighbor
     - If better path found, update neighbor's costs and parent

3. **Path Reconstruction:**
   - Starting from goal node, follow parent pointers back to start
   - Return the first step in this path

#### The Heuristic Function

The A\* algorithm uses Manhattan distance as its heuristic:

```java
private int calculateHeuristic(Location start, Location goal) {
    // Manhattan distance (L1 norm): |x1-x2| + |y1-y2|
    return Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y);
}
```

This heuristic is "admissible" (never overestimates cost) and "consistent" (satisfies triangle inequality), guaranteeing an optimal path in a grid-based world.

#### Detailed Implementation

```java
private Location findNextStep(Location start, Location goal, int agId) {
    // If start and goal are the same, no movement needed
    if (start.equals(goal)) {
        return null;
    }

    logger.fine("Finding path from " + start + " to " + goal + " for agent " + agId);

    // Priority queue for open nodes (to be evaluated)
    // Automatically sorts nodes by fCost (and hCost for ties)
    java.util.PriorityQueue<PathNode> openSet = new java.util.PriorityQueue<>();

    // Set for closed nodes (already evaluated)
    java.util.Set<Location> closedSet = new java.util.HashSet<>();

    // Start with the initial node
    PathNode startNode = new PathNode(start, 0, calculateHeuristic(start, goal), null);
    openSet.add(startNode);

    // Map to efficiently retrieve nodes by location
    java.util.Map<Location, PathNode> nodeMap = new java.util.HashMap<>();
    nodeMap.put(start, startNode);

    // Counter for performance analysis
    int nodesExplored = 0;

    // Main loop - continue until we find the goal or exhaust all possibilities
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

        // Check all 8 neighboring cells (4 orthogonal + 4 diagonal)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                // Skip the current node
                if (dx == 0 && dy == 0) {
                    continue;
                }

                // Special check for diagonal movement:
                // Ensure both horizontal and vertical paths aren't blocked
                // This prevents the agent from "cutting corners"
                if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
                    Location horNeighbor = new Location(current.loc.x + dx, current.loc.y);
                    Location verNeighbor = new Location(current.loc.x, current.loc.y + dy);
                    if (!isFree(horNeighbor, agId) || !isFree(verNeighbor, agId)) {
                        continue;
                    }
                }

                Location neighborLoc = new Location(current.loc.x + dx, current.loc.y + dy);

                // Skip if obstacle/wall, out of bounds, or already evaluated
                if (!isFree(neighborLoc, agId) || closedSet.contains(neighborLoc)) {
                    continue;
                }

                // Calculate movement cost (14 for diagonal ≈ √2 × 10, 10 for orthogonal)
                int movementCost = (Math.abs(dx) == 1 && Math.abs(dy) == 1) ? 14 : 10;
                int neighborGCost = current.gCost + movementCost;

                // Check if neighbor is in open set
                PathNode neighborNode = nodeMap.get(neighborLoc);

                // If neighbor isn't in open set OR we found a better path, update it
                if (neighborNode == null || neighborGCost < neighborNode.gCost) {
                    int hCost = calculateHeuristic(neighborLoc, goal);

                    // If exists but with worse cost, remove old version
                    if (neighborNode != null) {
                        openSet.remove(neighborNode);
                    }

                    // Create updated node
                    neighborNode = new PathNode(neighborLoc, neighborGCost, hCost, current);

                    // Add to data structures
                    openSet.add(neighborNode);
                    nodeMap.put(neighborLoc, neighborNode);
                }
            }
        }
    }

    // If we reach here, no path was found
    logger.warning("No path found from " + start + " to " + goal + " for agent " + agId +
            ". Explored " + nodesExplored + " nodes. Falling back to direct movement.");
    return null;
}
```

#### Path Extraction

Once a path is found, we need to extract just the first step to return to the agent:

```java
private Location getFirstStepInPath(PathNode goalNode) {
    // If the parent of the goal node is null, return the goal node itself
    if (goalNode.parent == null) {
        return goalNode.loc;
    }

    // Backtrack from goal to find the first step after start
    PathNode current = goalNode;
    while (current.parent != null && current.parent.parent != null) {
        current = current.parent;
    }

    // Return the first step in the path
    return current.loc;
}
```

#### Fallback Mechanism

If A\* fails to find a path (which can happen in complex environments), a fallback movement system attempts to get closer to the target:

```java
private void fallbackMove(Location loc, Location target, int agId) throws Exception {
    // Calculate direction vector toward target
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

    // First try: move diagonally (both x and y)
    Location newLoc = new Location(loc.x + dx, loc.y + dy);
    if (isFree(newLoc, agId)) {
        setAgPos(agId, newLoc);
        moved = true;
    } else {
        // Second try: move horizontally only
        newLoc = new Location(loc.x + dx, loc.y);
        if (dx != 0 && isFree(newLoc, agId)) {
            setAgPos(agId, newLoc);
            moved = true;
        } else {
            // Third try: move vertically only
            newLoc = new Location(loc.x, loc.y + dy);
            if (dy != 0 && isFree(newLoc, agId)) {
                setAgPos(agId, newLoc);
                moved = true;
            }
        }
    }

    // Last resort: try random directions if all else fails
    if (!moved) {
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        // Shuffle directions for randomness
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
```

#### Special Features of the Implementation

1. **Obstacle Definition**: Each agent type has different obstacle definitions:

   ```java
   public boolean isFree(Location loc, int agId) {
       // Check boundaries
       if (loc.x < 0 || loc.x >= GSize || loc.y < 0 || loc.y >= GSize) {
           return false;
       }

       // Check walls and obstacles
       if (hasObject(OBSTACLE, loc) || hasObject(WALL, loc)) {
           return false;
       }

       // Firefighter treats junk as obstacles
       if (agId == 0 && hasObject(JUNK, loc)) {
           return false;
       }

       // Cleaner treats fires as obstacles
       if (agId == 1 && hasObject(FIRE, loc)) {
           return false;
       }

       // Check for other agents
       for (int i = 0; i < 2; i++) {
           if (i != agId && getAgPos(i) != null && getAgPos(i).equals(loc)) {
               return false;
           }
       }

       return true;
   }
   ```

2. **Diagonal Movement Constraints**: Prevents "cutting corners" by ensuring both horizontal and vertical paths around a corner are clear.

3. **Tie-breaking**: When nodes have equal fCost, the implementation prefers those with lower hCost, which biases toward exploring nodes closer to the goal.

4. **Multi-level Fallback**: If A\* fails, the system tries multiple approaches to move the agent sensibly.

#### Integration with Agent Actions

The A\* pathfinding is integrated with agent movement through the `move_towards` action:

```java
void moveTowards(String ag, int x, int y) throws Exception {
    // Determine agent ID (0 for firefighter, 1 for cleaner)
    int agId = ag.equals("firefighter") ? 0 : 1;
    Location loc = getAgPos(agId);
    Location targetLoc = new Location(x, y);

    // If already at target, no need to move
    if (loc.equals(targetLoc)) {
        return;
    }

    // Find next step using A* pathfinding
    Location nextStep = findNextStep(loc, targetLoc, agId);

    // Move to next step if found, otherwise use fallback
    if (nextStep != null) {
        setAgPos(agId, nextStep);
    } else {
        fallbackMove(loc, targetLoc, agId);
    }
}
```

When an agent calls `move_towards(X,Y)` in Jason:

1. The environment translates coordinates to a grid location
2. A\* pathfinding calculates the optimal next step
3. The agent moves to that step
4. The process repeats on subsequent calls until the destination is reached

This approach allows for intelligent navigation through complex environments while avoiding obstacles and other agents.

### Agent Actions

```java
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
```

This method implements the key agent actions:

1. `move_towards(X,Y)` - Move the agent toward coordinates X,Y using A\* pathfinding
2. `reduce_intensity(Amount)` - Reduce fire intensity (used by firefighter)
3. `clean_junk` - Remove junk from the environment (used by cleaner)

After each action, it updates percepts and informs agents of environment changes.

### Dynamic Environment

The environment includes a spawner that periodically creates new fires and junk:

```java
private void startItemSpawner() {
    spawnerThread = new Thread(() -> {
        while (true) {
            try {
                // Sleep for a random time between 5-15 seconds
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
```

This thread runs continuously and:

1. Waits for a random interval between 5-15 seconds
2. Selects a random room to place a new item
3. Creates junk in the storage room, fires in the control room, and either fires or junk in the hibernation room
4. Updates the environment to notify agents of the new items

### Visualization

The environment includes a graphical view to display the simulation:

```java
class RescueView extends GridWorldView {
    private final RescueModel env;

    public RescueView(RescueModel model) {
        super(model, "Rescue World", 600);
        this.env = model;
        defaultFont = new Font("Arial", Font.BOLD, 12);
        setVisible(true);
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
```

This visualization provides:

1. A color-coded grid with red for the firefighter and blue for the cleaner
2. Orange cells for fires (with intensity shown)
3. Gray cells for junk items
4. Black cells for walls
5. The view automatically updates when the model changes

## Running the Program

To run the simulation:

1. Make sure you have Jason installed (http://jason.sourceforge.net/)
2. Navigate to the project's `src` directory
3. Run the following command:

```bash
jason RescueRobots.mas2j
```

This will launch the simulation environment with both the firefighter and cleaner agents. You'll see the graphical interface showing the grid world with:

- The firefighter agent (marked as FF in red)
- The cleaner agent (marked as CL in blue)
- Fires and junk distributed across the three rooms
- Walls separating the rooms and doors connecting them

The agents will automatically start patrolling and responding to fires and junk as they appear in the environment.
