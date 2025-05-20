!init.

// Room boundaries
storage_room(0, 9).   // Storage room: y from 0 to 9
control_room(10, 14). // Control room: y from 10 to 14
hibernation_room(15, 19). // Hibernation room: y from 15 to 19

// Door positions
door1_pos(5, 10).  // Door between storage and control
door2_pos(10, 15).  // Door between control and hibernation

// Current patrol position
patrol_x(0).
patrol_y(0).
patrol_dir(right). // right, down, left, up

// Current room (1=storage, 2=control, 3=hibernation)
current_room(1).

// Keep track of patrol direction (true = forward, false = backward)
patrol_forward(true).
patrol_row(0). // Start at top row of storage room
patrol_col(0). // Start at leftmost column

+!init
   <- .print("Cleaner initialized. Starting junk cleanup...");
      !clean_junk.

// Main goal - Find and clean junk
+!clean_junk : junk(_,_,_)
   <- // Find the closest junk
      ?pos(cleaner,MX,MY);
      
      // Get all junk only once and print for debugging
      .findall(junk(X,Y,Type), junk(X,Y,Type), Junks);
      .print("Currently perceiving ", .length(Junks), " junk items");
      
      // Check if we have junk items
      if (.length(Junks) > 0) {
         !find_closest_junk(MX, MY, Junks, ClosestX, ClosestY, ClosestType);
         .print("Found closest junk at ", ClosestX, ", ", ClosestY, " of type ", ClosestType);
         !go_to(ClosestX, ClosestY);
         .print("Cleaning junk at ", ClosestX, ", ", ClosestY);
      
         // Before cleaning, remove all beliefs about this junk
         .abolish(junk(ClosestX, ClosestY, _));
      
         // Now actually clean the junk
         clean_junk;
      
         // Pause briefly to allow percepts to update
         .wait(200);
      
         // Drop intentions and start over
         .drop_all_intentions;
         !clean_junk;
      } else {
         .print("No junk found. Starting patrol mode.");
         !patrol;
      }.
   
// No junk remaining, start patrolling
+!clean_junk 
   <- .print("No junk found. Starting patrol mode.");
      !patrol.

// Patrol behavior - move row by row across the grid
+!patrol
   <- ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_forward(Forward);
      
      // Go to the current patrol point
      !go_to(Col, Row);
      
      // Calculate next patrol point
      if (Forward) {
         // Moving forward through the grid
         if (Col > 0) {
            // Move left within the current row
            -+patrol_col(Col-1);
         } else {
            // Start of row reached
            if (Row > 0) {
               // Move up to the end of the previous row
               -+patrol_row(Row-1);
               -+patrol_col(19);
            } else {
               // Reached the top of the grid, reverse direction
               -+patrol_forward(false);
               -+patrol_col(1);
            }
         }
      } else {
         // Moving backward through the grid
         if (Col < 19) {
            // Move right within the current row
            -+patrol_col(Col+1);
         } else {
            // End of row reached
            if (Row < 19) {
               // Move down to the start of the next row
               -+patrol_row(Row+1);
               -+patrol_col(0);
            } else {
               // Reached the bottom of the grid, reverse direction
               -+patrol_forward(true);
               -+patrol_col(18);
            }
         }
      }
      
      // Check for junk before continuing patrol
      .findall(junk(X,Y,Type), junk(X,Y,Type), Junks);
      if (.length(Junks) > 0) {
         .print("Junk detected during patrol. Switching to cleaning mode.");
         !clean_junk;
      } else {
         .wait(200);
         !patrol;
      }.

// Update which room we're in based on Y coordinate
+!update_current_room(Y)
   <- ?storage_room(SMin, SMax);
      ?control_room(CMin, CMax);
      ?hibernation_room(HMin, HMax);
      
      if (Y >= SMin & Y <= SMax) {
         -+current_room(1); // Storage room
      } else {
         if (Y >= CMin & Y <= CMax) {
            -+current_room(2); // Control room  
         } else {
            if (Y >= HMin & Y <= HMax) {
               -+current_room(3); // Hibernation room
            }
         }
      }.

// Calculate next patrol point using a simple snake pattern
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
      
      if (Dir == right) {
         if (X < MaxX) {
            // Continue moving right
            -+patrol_x(X+1);
         } else {
            // Turn and move down
            -+patrol_y(Y+1);
            -+patrol_dir(down);
            // Skip wall rows (10 and 15)
            if (Y+1 == 10 | Y+1 == 15) {
               // Check if we're at a door position
               if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
                  // At a door location, so it's okay to proceed
               } else {
                  // Not at a door, so skip this row
                  -+patrol_y(Y+2);
               }
            }
         }
      } else {
         if (Dir == down) {
            if (Y < MaxY) {
               // Continue moving down
               -+patrol_y(Y+1);
               // Skip wall rows (10 and 15)
               if (Y+1 == 10 | Y+1 == 15) {
                  // Check if we're at a door position
                  if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
                     // At a door location, so it's okay to proceed
                  } else {
                     // Not at a door, so skip this row
                     -+patrol_y(Y+2);
                  }
               }
            } else {
               if (Room < 3) {
                  // Move to next room through door
                  -+current_room(Room+1);
                  !use_door_to_next_room;
               } else {
                  // Back to first room
                  -+current_room(1);
                  -+patrol_x(0);
                  -+patrol_y(0);
                  -+patrol_dir(right);
               }
            }
         } else {
            if (Dir == left) {
               if (X > 0) {
                  // Continue moving left
                  -+patrol_x(X-1);
               } else {
                  // Turn and move down
                  -+patrol_y(Y+1);
                  -+patrol_dir(down);
                  // Skip wall rows (10 and 15)
                  if (Y+1 == 10 | Y+1 == 15) {
                     // Check if we're at a door position
                     if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
                        // At a door location, so it's okay to proceed
                     } else {
                        // Not at a door, so skip this row
                        -+patrol_y(Y+2);
                     }
                  }
               }
            } else { 
               // Dir == up
               if (Y > MinY) {
                  // Continue moving up
                  -+patrol_y(Y-1);
                  // Skip wall rows (10 and 15)
                  if (Y-1 == 10 | Y-1 == 15) {
                     // Check if we're at a door position
                     if ((Y-1 == 10 & X == 5) | (Y-1 == 15 & X == 10)) {
                        // At a door location, so it's okay to proceed
                     } else {
                        // Not at a door, so skip this row
                        -+patrol_y(Y-2);
                     }
                  }
               } else {
                  // Turn and move right
                  -+patrol_x(X+1);
                  -+patrol_dir(right);
               }
            }
         }
      }.

// Move to the next room using the appropriate door
+!use_door_to_next_room
   <- ?current_room(Room);
      
      if (Room == 2) {
         // Use door between storage and control
         ?door1_pos(DoorX, DoorY);
         -+patrol_x(DoorX);
         -+patrol_y(DoorY);
         -+patrol_dir(down);
      } else {
         if (Room == 3) {
            // Use door between control and hibernation
            ?door2_pos(DoorX, DoorY);
            -+patrol_x(DoorX);
            -+patrol_y(DoorY);
            -+patrol_dir(down);
         }
      }.

// Safe movement that ensures proper door navigation
+!safe_move_to(X, Y)
   <- ?pos(cleaner,CX,CY);
      ?current_room(MyRoom);
      
      // Determine which room the target is in
      if (Y >= 0 & Y <= 9) {
         TargetRoom = 1;
      } else {
         if (Y >= 10 & Y <= 14) {
            TargetRoom = 2;
         } else {
            TargetRoom = 3;
         }
      }
      
      // If target is in a different room, first go to the door
      if (MyRoom \== TargetRoom) {
         if ((MyRoom == 1 & TargetRoom == 2) | (MyRoom == 2 & TargetRoom == 1)) {
            // Need to use door between storage and control
            ?door1_pos(DoorX, DoorY);
            !go_to(DoorX, DoorY);
         } else {
            if ((MyRoom == 2 & TargetRoom == 3) | (MyRoom == 3 & TargetRoom == 2)) {
               // Need to use door between control and hibernation
               ?door2_pos(DoorX, DoorY);
               !go_to(DoorX, DoorY);
            }
         }
      } else {
         // Same room, go directly
         !go_to(X, Y);
      }.

// Find the closest junk based on Manhattan distance
+!find_closest_junk(MX, MY, [junk(X,Y,Type)], X, Y, Type).

+!find_closest_junk(MX, MY, [junk(X,Y,Type)|Rest], ClosestX, ClosestY, ClosestType)
   <- // Calculate Manhattan distance to this junk
      Distance = math.abs(X-MX) + math.abs(Y-MY);
      // Find closest from rest of the list
      !find_closest_junk(MX, MY, Rest, TmpX, TmpY, TmpType);
      // Calculate distance to the temporary closest
      TmpDistance = math.abs(TmpX-MX) + math.abs(TmpY-MY);
      // Compare and select the closer one
      if (Distance <= TmpDistance) {
         ClosestX = X;
         ClosestY = Y;
         ClosestType = Type;
      } else {
         ClosestX = TmpX;
         ClosestY = TmpY;
         ClosestType = TmpType;
      }.

// Handle junk detection directly
+junk(X,Y,Type) : not .intend(clean_junk) & not .intend(go_to(_,_))
   <- .print("Detected new junk at ", X, ", ", Y);
      !clean_junk.

// Arrived at destination
+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y).

// Move towards target
+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving towards (", X, ",", Y, ")");
      move_towards(X,Y);
      .wait(100);
      !go_to(X,Y).
