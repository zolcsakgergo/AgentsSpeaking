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

These beliefs define the room boundaries, door positions, and initial patrol parameters.

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

This plan handles the primary goal of detecting and cleaning junk:

1. It retrieves all visible junk items using `.findall`
2. If junk is found, it finds the closest one using the Manhattan distance
3. The agent navigates to the junk location
4. Once at the location, it removes the belief and cleans the junk
5. After cleaning, it waits briefly and then restarts the process

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

This patrol plan implements a zigzag pattern across the grid:

1. It first moves to the current patrol point
2. Then calculates the next point based on the current direction (forward/backward)
3. The agent traverses rows completely before moving to the next row
4. It can reverse direction when reaching grid boundaries
5. After each move, it checks for junk and responds if found

#### Room Navigation

```
+!update_current_room(Y)
   <- ?storage_room(SMin, SMax);
      ?control_room(CMin, CMax);
      ?hibernation_room(HMin, HMax);

      if (Y >= SMin & Y <= SMax) {
         -+current_room(1);
      } else {
         if (Y >= CMin & Y <= CMax) {
            -+current_room(2);
         } else {
            if (Y >= HMin & Y <= HMax) {
               -+current_room(3);
            }
         }
      }.
```

This plan helps the agent determine which room it's currently in based on its Y coordinate.

#### Door Transition Logic

```
+!use_door_to_next_room
   <- ?current_room(Room);

      if (Room == 2) {
         ?door1_pos(DoorX, DoorY);
         -+patrol_x(DoorX);
         -+patrol_y(DoorY);
         -+patrol_dir(down);
      } else {
         if (Room == 3) {
            ?door2_pos(DoorX, DoorY);
            -+patrol_x(DoorX);
            -+patrol_y(DoorY);
            -+patrol_dir(down);
         }
      }.
```

This plan manages the transition through doors when moving between rooms.

#### Reactive Behavior

```
+junk(X,Y,Type) : not .intend(clean_junk) & not .intend(go_to(_,_))
   <- .print("Detected new junk at ", X, ", ", Y);
      !clean_junk.
```

This reaction triggers when new junk is detected, causing the agent to switch from patrol to cleaning mode immediately.

### Firefighter Agent (`firefighter.asl`)

The firefighter agent is tasked with finding and extinguishing fires in the environment.

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

The firefighter starts in the hibernation room and primarily operates in the control and hibernation rooms.

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

This plan is similar to the cleaner's junk handling but focuses on fires:

1. It finds all visible fires
2. Locates the closest one
3. Navigates to that fire
4. Reduces the fire's intensity
5. If no fires are found, it switches to patrol mode

#### Obstacle Avoidance

```
+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      if (pos(cleaner,NX,NY)) {
         MoveAround = false;

         if (math.abs(NX-(CX+1)) < 0.1) {
            if (math.abs(NY-CY) < 0.1) {
               if (CX < X) {
                  MoveAround = true;
               }
            }
         }

         // [Additional checks omitted for brevity]

         if (MoveAround) {
            if (CX < X) {
               move_towards(CX, CY+1);
            } else {
               // [Additional movement logic omitted]
            }
         } else {
            move_towards(X,Y);
         }
      } else {
         move_towards(X,Y);
      }
      .wait(100);
      !go_to(X,Y).
```

The firefighter has a more sophisticated movement system that can detect and avoid the cleaner agent when they would otherwise collide.

## Environment Implementation (`RescueEnv.java`)

The environment class implements the simulation world, handles agent actions, and manages the perception of both agents.

### Grid World Model

```java
public class RescueEnv extends Environment {
    public static final int GSize = 20; // grid size
    public static final int FIRE = 16; // fire code in grid model
    public static final int JUNK = 32; // junk code in grid model
    public static final int OBSTACLE = 128; // obstacle code in grid model
    public static final int WALL = 64; // wall code in grid model

    // [Implementation details...]
}
```

The environment uses a 20x20 grid with special codes for different objects.

### Room Configuration

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
    int doorPos1 = GSize / 4;
    remove(WALL, doorPos1, 10);
    remove(WALL, doorPos1 + 1, 10);

    // Create door between Control and Hibernation rooms
    int doorPos2 = GSize / 2;
    remove(WALL, doorPos2, 15);
    remove(WALL, doorPos2 + 1, 15);
}
```

The environment creates three rooms separated by walls, with doors connecting them.

### A\* Pathfinding

```java
private Location findNextStep(Location start, Location goal, int agId) {
    // [A* implementation details...]

    // Priority queue for open nodes
    java.util.PriorityQueue<PathNode> openSet = new java.util.PriorityQueue<>();

    // Set for closed nodes
    java.util.Set<Location> closedSet = new java.util.HashSet<>();

    // [Pathfinding algorithm continues...]
}
```

The environment implements A\* pathfinding to help agents navigate efficiently through the grid, avoiding obstacles and walls.

### Agent Actions

```java
@Override
public boolean executeAction(String ag, Structure action) {
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
        }
    } catch (Exception e) {
        // [Error handling...]
    }

    // [Additional processing...]

    return result;
}
```

The environment handles three main agent actions:

1. `move_towards`: Moves an agent toward a target location
2. `reduce_intensity`: Reduces the intensity of a fire (used by the firefighter)
3. `clean_junk`: Removes junk from a location (used by the cleaner)

### Dynamic Environment

```java
private void startItemSpawner() {
    spawnerThread = new Thread(() -> {
        while (true) {
            try {
                // Sleep for a random time
                Thread.sleep(5000 + random.nextInt(5000));

                // Randomly decide what to spawn and where
                int roomType = random.nextInt(3); // 0=storage, 1=control, 2=hibernation

                // [Spawning logic for each room type...]

                // Update environment if an item was added
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
```

The environment dynamically spawns new fires and junk in different rooms, creating a challenging and realistic simulation.

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
