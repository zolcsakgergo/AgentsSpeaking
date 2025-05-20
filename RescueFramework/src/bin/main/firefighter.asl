!init.

// Room boundaries (copied from RescueEnv.java)
control_room(10, 14). // Control room: y from 10 to 14
hibernation_room(15, 19). // Hibernation room: y from 15 to 19

// Doors
door2_x(10). // Second door at x=10, y=15 (between control and hibernation)

// Current room
current_room(3). // 1=storage, 2=control, 3=hibernation (firefighter starts in hibernation)

// Direction: true = increasing column, false = decreasing column
patrol_direction(false).
patrol_row(19). // Start at bottom row of hibernation room
patrol_col(19). // Start at rightmost column

+!init
   <- .print("Firefighter initialized. Starting fire extinguishing...");
      !extinguish_fires.

// Main goal - Find and extinguish fires
+!extinguish_fires : fire(_,_,_)
   <- // Find the closest fire
      ?pos(firefighter,MX,MY);
      
      // Get all fires only once
      .findall(fire(X,Y,I), fire(X,Y,I), Fires);
      .print("Currently perceiving ", .length(Fires), " fires");
      
      // Check if we have fire items
      if (.length(Fires) > 0) {
         !find_closest_fire(MX, MY, Fires, ClosestX, ClosestY, Intensity);
         .print("Found closest fire at ", ClosestX, ", ", ClosestY, " with intensity ", Intensity);
         !go_to(ClosestX, ClosestY);
         .print("Reducing fire intensity at ", ClosestX, ", ", ClosestY);
      
         // Before reducing intensity, remove all beliefs about this fire
         .abolish(fire(ClosestX, ClosestY, _));
      
         // Now actually reduce the fire intensity
         reduce_intensity(100);
      
         // Pause briefly to allow percepts to update
         .wait(200);
      
         // Drop intentions and start over
         .drop_all_intentions;
         !extinguish_fires;
      } else {
         .print("No fires found. Starting patrol mode.");
         !patrol;
      }.

// No fires remaining, start patrolling
+!extinguish_fires 
   <- .print("No fires found. Starting patrol mode.");
      !patrol.

// Patrol behavior - intelligent room-based patrol
+!patrol
   <- ?pos(firefighter,CX,CY);
      ?current_room(Room);
      
      // Determine which room we're in based on current position
      !update_current_room(CY);
      ?current_room(NewRoom);
      
      // If room changed, update patrol starting point
      if (NewRoom \== Room) {
         .print("Entering new room: ", NewRoom);
         !set_patrol_start_point(NewRoom);
      }
      
      // Get patrol coordinates
      ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_direction(Direction);
      
      // Go to the current patrol point if not already there
      if (not pos(firefighter,Col,Row)) {
         // Check if we need to go through a door
         if ((NewRoom == 2 & Row > 14) | (NewRoom == 3 & Row < 15)) {
            .print("Need to go through a door to reach target");
            !navigate_to_door(NewRoom, Col, Row);
         } else {
            !go_to(Col, Row);
         }
      }
      
      // Calculate next patrol point
      !calculate_next_patrol_point;
      
      // Check for fires before continuing patrol
      .findall(fire(X,Y,I), fire(X,Y,I), Fires);
      if (.length(Fires) > 0) {
         .print("Fire detected during patrol. Switching to extinguishing mode.");
         !extinguish_fires;
      } else {
         .wait(200);
         !patrol;
      }.

// Update current room based on Y position
+!update_current_room(Y)
   <- ?control_room(CMin, CMax);
      ?hibernation_room(HMin, HMax);
      
      if (Y >= CMin & Y <= CMax) {
         -+current_room(2); // Control room
      } else {
         if (Y >= HMin & Y <= HMax) {
            -+current_room(3); // Hibernation room
         }
      }.

// Set patrol start point based on room
+!set_patrol_start_point(Room)
   <- if (Room == 2) {
         // Control room - start at bottom-right
         -+patrol_row(14);
         -+patrol_col(19);
         -+patrol_direction(false);
      } else {
         // Hibernation room - start at bottom-right
         -+patrol_row(19);
         -+patrol_col(19);
         -+patrol_direction(false);
      }.

// Navigate to appropriate door when changing rooms
+!navigate_to_door(TargetRoom, TargetX, TargetY)
   <- ?door2_x(DoorX);
      !go_to(DoorX, 15).

// Calculate next patrol point
+!calculate_next_patrol_point
   <- ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_direction(Direction);
      ?current_room(Room);
      
      // Get current room boundaries
      if (Room == 2) {
         ?control_room(MinY, MaxY);
      } else {
         ?hibernation_room(MinY, MaxY);
      }
      
      // Snake pattern within current room
      if (Direction) {
         // Moving right
         if (Col < 19) {
            // Try next column
            -+patrol_col(Col+1);
         } else {
            // End of row reached, move up if still in room
            if (Row > MinY) {
               // Check if moving to a wall row
               if (Row-1 == 10 | Row-1 == 15) {
                  // Check if at door positions with nested ifs instead of complex logic
                  if (Row-1 == 10) {
                     if (Col == 5) {
                        // At door location, proceed normally
                        -+patrol_row(Row-1);
                     } else {
                        // Not at door, skip wall row
                        -+patrol_row(Row-2);
                     }
                  } else {
                     // Must be Row-1 == 15
                     if (Col == 10) {
                        // At door location, proceed normally
                        -+patrol_row(Row-1);
                     } else {
                        // Not at door, skip wall row
                        -+patrol_row(Row-2);
                     }
                  }
               } else {
                  // Normal move up
                  -+patrol_row(Row-1);
               }
               // Change direction (snake pattern)
               -+patrol_direction(false);
            } else {
               // Check if we reached the top-left of the control room
               if (Room == 2) {
                  if (Row == MinY) {
                     if (Col == 19) {
                        // Go back to the starting point (hibernation room)
                        -+current_room(3);
                        -+patrol_row(19);
                        -+patrol_col(19);
                        -+patrol_direction(false);
                     }
                  }
               } else {
                  // If in hibernation and top is reached, move to control room
                  if (Room == 3) {
                     -+current_room(2);
                     !set_patrol_start_point(2);
                  }
               }
            }
         }
      } else {
         // Moving left
         if (Col > 0) {
            // Try previous column
            -+patrol_col(Col-1);
         } else {
            // Start of row reached, move up if still in room
            if (Row > MinY) {
               // Check if moving to a wall row
               if (Row-1 == 10 | Row-1 == 15) {
                  // Check if at door positions with nested ifs instead of complex logic
                  if (Row-1 == 10) {
                     if (Col == 5) {
                        // At door location, proceed normally
                        -+patrol_row(Row-1);
                     } else {
                        // Not at door, skip wall row
                        -+patrol_row(Row-2);
                     }
                  } else {
                     // Must be Row-1 == 15
                     if (Col == 10) {
                        // At door location, proceed normally
                        -+patrol_row(Row-1);
                     } else {
                        // Not at door, skip wall row
                        -+patrol_row(Row-2);
                     }
                  }
               } else {
                  // Normal move up
                  -+patrol_row(Row-1);
               }
               // Change direction (snake pattern)
               -+patrol_direction(true);
            } else {
               // Check if we reached the top-left of the control room
               if (Room == 2) {
                  if (Row == MinY) {
                     if (Col == 0) {
                        // Go back to the starting point (hibernation room)
                        -+current_room(3);
                        -+patrol_row(19);
                        -+patrol_col(19);
                        -+patrol_direction(false);
                     }
                  }
               } else {
                  // If in hibernation and top is reached, move to control room
                  if (Room == 3) {
                     -+current_room(2);
                     !set_patrol_start_point(2);
                  }
               }
            }
         }
      }.

// Handle fire detection directly
+fire(X,Y,I) : not .intend(extinguish_fires) & not .intend(go_to(_,_))
   <- .print("Detected new fire at ", X, ", ", Y, " with intensity ", I);
      !extinguish_fires.

// Find the closest fire based on Manhattan distance
+!find_closest_fire(MX, MY, [fire(X,Y,I)], X, Y, I).

+!find_closest_fire(MX, MY, [fire(X,Y,I)|Rest], ClosestX, ClosestY, ClosestI)
   <- // Calculate Manhattan distance to this fire
      Distance = math.abs(X-MX) + math.abs(Y-MY);
      // Find closest from rest of the list
      !find_closest_fire(MX, MY, Rest, TmpX, TmpY, TmpI);
      // Calculate distance to the temporary closest
      TmpDistance = math.abs(TmpX-MX) + math.abs(TmpY-MY);
      // Compare and select the closer one
      if (Distance <= TmpDistance) {
         ClosestX = X;
         ClosestY = Y;
         ClosestI = I;
      } else {
         ClosestX = TmpX;
         ClosestY = TmpY;
         ClosestI = TmpI;
      }.

// Arrived at destination
+!go_to(X,Y) : pos(firefighter,X,Y)
   <- .print("Already at target location ", X, ", ", Y);
      .wait(100).

// Pathfinding with obstacle avoidance
+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      // Check if cleaner is blocking the path
      if (pos(cleaner,NX,NY)) {
         // Check individual conditions with nested ifs
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
            } else {
               if (CX > X) {
                  move_towards(CX, CY-1);
               } else {
                  if (CY < Y) {
                     move_towards(CX+1, CY);
                  } else {
                     move_towards(CX-1, CY);
                  }
               }
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


