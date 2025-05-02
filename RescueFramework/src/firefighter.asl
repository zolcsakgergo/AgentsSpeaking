// Firefighter agent

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(firefighter,X,Y).
// Track already extinguished fires to avoid loops
extinguished_fires([]).
// Track visited positions to detect movement loops
visited_positions([]).
debug(true).

// Check if position was visited to detect loops
visited(X, Y) :- visited(L) & .member(pos(X,Y), L).

// Unreachable check
unreachable(X, Y) :- unreachable(pos(X,Y)).

/* Initial goals */
!extinguish_fires.

/* Plans */

// Main plan to search for and extinguish fires
+!extinguish_fires : debug(true)
   <- .print("[DEBUG] Starting firefighter mission with debugging");
      // Force immediate exploration to confirm movement works
      !explore_immediately;
      !find_fires.

// Main plan without debugging
+!extinguish_fires : not debug(true)
   <- .print("Starting firefighter mission");
      !find_fires.

// Extra plan to verify the agent can move
+!explore_immediately
   <- .print("[DEBUG] Forcing initial exploration to verify movement");
      ?pos(firefighter,X,Y);
      .print("[DEBUG] Current position: (", X, ", ", Y, ")");
      // Move to center of grid
      !go_to(10,10);
      .print("[DEBUG] Reached center of grid, now searching for fires");
      !scan_for_fires.

// If we've found a fire, prioritize based on intensity and go extinguish it
+!find_fires : active_fire(X,Y,I) & not unreachable(X,Y)
   <- .print("[FF] Found fire at ", X, ", ", Y, " with intensity ", I);
      !go_to(X,Y);
      !extinguish;
      !find_fires.

// If we've only found unreachable fires, explore more
+!find_fires : active_fire(X,Y,I) & unreachable(X,Y)
   <- .print("[FF] Skipping unreachable fire at ", X, ", ", Y);
      !explore_unknown;
      !find_fires.

// If no active fire is perceived, explore to discover new areas
+!find_fires : not active_fire(_,_,_)
   <- .print("[FF] No active fires detected, exploring...");
      !explore_unknown;
      !scan_for_fires;
      !find_fires.

// Scan all visible fires to make sure we haven't missed any
+!scan_for_fires
   <- .print("[FF] Scanning for undetected fires");
      .findall(fire(FX,FY,FI), (fire(FX,FY,FI) & FI > 0), AllFires);
      .length(AllFires, FireCount);
      .print("[FF] Found ", FireCount, " fires in scan");
      for (.member(fire(FX,FY,FI), AllFires)) {
         ?extinguished_fires(EF);
         if (not .member(pos(FX,FY), EF) & not active_fire(FX,FY,_)) {
            .print("[FF] Found previously undetected fire at ", FX, ", ", FY);
            +active_fire(FX,FY,FI);
         }
      };
      .findall(active_fire(AX,AY,AI), active_fire(AX,AY,AI), ActiveFires);
      .length(ActiveFires, ActiveCount);
      .print("[FF] Currently tracking ", ActiveCount, " active fires").

// Failure recovery for find_fires
-!find_fires
   <- .print("[FF] Failed to find fires, resetting and trying again");
      .abolish(active_fire(_,_,_));
      .wait(500);
      !find_fires.

// If we're at a location with fire, stay put and extinguish
+!extinguish : pos(firefighter,X,Y) & fire(X,Y,I) & I > 0
   <- .print("[FF] Extinguishing fire at ", X, ", ", Y, " with intensity ", I);
      reduce_intensity(200);
      .wait(100); // Wait for perception update
      !extinguish.
      
// If fire is fully extinguished, update beliefs and move on
+!extinguish : pos(firefighter,X,Y) & fire(X,Y,0)
   <- .print("[FF] Fire extinguished at ", X, ", ", Y);
      ?extinguished_fires(EF);
      // Add this location to extinguished fires list
      .concat([pos(X,Y)], EF, NewEF);
      -+extinguished_fires(NewEF);
      -active_fire(X,Y,_); // Remove from active fires
      // Reset visited positions after successful fire extinguishing
      -+visited_positions([]);
      !find_fires.

// If fire is not visible, check if we're at the right position
+!extinguish : pos(firefighter,X,Y) & active_fire(X,Y,_) & not fire(X,Y,_)
   <- .print("[FF] Expected fire at ", X, ", ", Y, " but none was found");
      -active_fire(X,Y,_);
      !find_fires.

// Default extinguish case if not at a fire
+!extinguish
   <- .print("[FF] Not at fire location, searching again");
      !find_fires.

// Failure recovery for extinguish
-!extinguish
   <- .print("[FF] Failed to extinguish, resetting and trying again");
      ?pos(firefighter,X,Y);
      -active_fire(X,Y,_);
      !find_fires.

// Go to a specific location
+!go_to(X,Y) : pos(firefighter,X,Y)
   <- .print("[FF] Already at target location ", X, ", ", Y).
   
// Movement with loop detection and tracking
+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("[FF] Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      ?visited_positions(VP);
      
      // Calculate steps taken so far to this position
      .count(.member(pos(CX,CY), VP), LoopCount);
      
      // If we've been here too many times, mark this fire as unreachable
      if (LoopCount > 3) {
         .print("[FF] ** This fire seems unreachable, marking it and trying another target **");
         +unreachable(X,Y);
         -active_fire(X,Y,_); // Remove from active fire list
         -+visited_positions([]); // Reset visited positions
         !find_fires;
      } else {
         // Update visited positions
         .concat([pos(CX,CY)], VP, NewVP);
         if (.length(NewVP) > 10) { .nth(10, NewVP, _); .delete(10, NewVP, TrimmedVP); -+visited_positions(TrimmedVP); }
         else { -+visited_positions(NewVP); }
         
         // Try making larger steps if we seem to be moving slowly
         if (LoopCount > 1) {
            // Make bigger jumps toward target
            if (CX < X) { NX = CX + 2; } 
            elif (CX > X) { NX = CX - 2; }
            else { NX = CX; }
            
            if (CY < Y) { NY = CY + 2; } 
            elif (CY > Y) { NY = CY - 2; }
            else { NY = CY; }
            
            // Ensure we stay within grid bounds
            if (NX < 0) { NX = 0; }
            if (NY < 0) { NY = 0; }
            if (NX >= 20) { NX = 19; }
            if (NY >= 20) { NY = 19; }
            
            .print("[FF] Taking larger step to break loop: (", NX, ",", NY, ")");
            move_towards(NX,NY);
         } else {
            // Normal movement
            move_towards(X,Y);
         }
         
         // Add a timeout to prevent infinite loops
         .wait(100);
         !go_to(X,Y);
      }.

// Error handling for position perception
-?pos(firefighter,X,Y) 
   <- .print("[FF] Position not found, waiting for perception...");
      .wait(200);
      ?pos(firefighter,X,Y).

// Failure handling for go_to
-!go_to(X,Y)
   <- .print("[FF] Failed to reach position (", X, ",", Y, "), trying a different approach");
      ?pos(firefighter,CX,CY);
      if (math.abs(X-CX) > math.abs(Y-CY)) {
         // Try moving vertically first
         NY = CY + math.round((Y-CY)/math.abs(Y-CY));
         !go_to(CX,NY);
      } else {
         // Try moving horizontally first
         NX = CX + math.round((X-CX)/math.abs(X-CX));
         !go_to(NX,CY);
      }.

// Explore unknown areas to discover fires
+!explore_unknown
   <- ?pos(firefighter,X,Y);
      .print("[FF] Exploring from ", X, ", ", Y);
      !find_closest_unknown;
      .wait(200); // Wait a bit to let perceptions update
      !find_fires.

// Find closest unknown location with better distance calculation
+!find_closest_unknown
   <- ?pos(firefighter,MyX,MyY);
      .findall(unknown(UX,UY,Dist), (unknown(UX,UY) & Dist = math.sqrt((UX-MyX)*(UX-MyX) + (UY-MyY)*(UY-MyY))), UnknownsWithDist);
      .length(UnknownsWithDist, Len);
      if (Len > 0) {
         .sort(UnknownsWithDist, SortedUnknowns); // Sort by distance (closest first)
         .nth(0, SortedUnknowns, unknown(TargetX,TargetY,_));
         .print("[FF] Moving to closest unknown area at ", TargetX, ", ", TargetY);
         !go_to(TargetX,TargetY);
      } else {
         !random_move;
      }.

// Move randomly when there's nothing else to do
+!random_move
   <- ?pos(firefighter,X,Y);
      .random(RX); .random(RY);
      NX = math.round(X + (RX-0.5) * 5); // Move at most 2.5 spaces from current
      NY = math.round(Y + (RY-0.5) * 5);
      // Keep within grid bounds
      if (NX < 0) { NX = 0; }
      if (NY < 0) { NY = 0; }
      if (NX >= 20) { NX = 19; }
      if (NY >= 20) { NY = 19; }
      .print("[FF] Moving randomly to ", NX, ", ", NY);
      !go_to(NX,NY).

// Better fire detection and prioritization with intensity and distance
+active_fire(X,Y,I) : not .desire(go_to(X,Y)) & not unreachable(X,Y)
   <- ?pos(firefighter,MyX,MyY);
      .print("[FF] Processing active fire at ", X, ", ", Y);
      // Calculate distance factor and combine with intensity for priority
      .findall(fire(PX,PY,PI,Priority), 
               (fire(PX,PY,PI) & PI > 0 & 
                ?extinguished_fires(EF) & not .member(pos(PX,PY), EF) &
                not unreachable(PX,PY) &
                Dist = math.sqrt((PX-MyX)*(PX-MyX) + (PY-MyY)*(PY-MyY)) &
                Priority = (PI / Dist)), 
               Fires);
      .length(Fires, Len);
      .print("[FF] Found ", Len, " fires to prioritize");
      if (Len > 0) {
         .sort(Fires, SortedFires); // Sort by priority (highest first)
         .nth(0, SortedFires, fire(TX,TY,_,_)); // Get highest priority fire
         .print("[FF] Selected highest priority fire at ", TX, ", ", TY);
         !go_to(TX,TY);
      } else {
         .print("[FF] No reachable fires found, going to explore");
         !explore_unknown;
      }.

// Handle perception of new fires - with checks to avoid duplicates and extinguished fires
+fire(X,Y,I)[source(percept)] : I > 0
   <- ?extinguished_fires(EF);
      if (not .member(pos(X,Y), EF) & not unreachable(X,Y)) {
         .print("[FF] Detected fire at ", X, ", ", Y, " with intensity ", I);
         // Check if we already have this as active fire
         if (not active_fire(X,Y,_)) {
            +active_fire(X,Y,I);
         }
      }.

// Handle extinguished fires
+fire(X,Y,0)[source(percept)] : active_fire(X,Y,_)
   <- .print("[FF] Fire at ", X, ", ", Y, " has been extinguished");
      -active_fire(X,Y,_);
      ?extinguished_fires(EF);
      .concat([pos(X,Y)], EF, NewEF);
      -+extinguished_fires(NewEF).

// In case a fire appears again at a location we thought was extinguished
+fire(X,Y,I)[source(percept)] : I > 0 & extinguished_fires(EF) & .member(pos(X,Y), EF)
   <- .print("[FF] Fire has rekindled at ", X, ", ", Y);
      ?extinguished_fires(OldEF);
      .delete(pos(X,Y), OldEF, NewEF);
      -+extinguished_fires(NewEF);
      +active_fire(X,Y,I).

// Firefighter agent.

/* Initial beliefs */
visited([]).
extinguished_fires([]).
stuck_count(0).
reset_tries(0).

/* Initial goals */
!extinguish_fires.

/* Plans */
+!extinguish_fires : true 
   <- .print("Starting firefighter mission");
      !find_fire.

+!reset_mental_state : reset_tries(R) & R < 3
   <- .print("Resetting mental state. Attempt: ", R+1);
      -+visited([]);
      -+stuck_count(0);
      -+reset_tries(R+1);
      !extinguish_fires.

+!reset_mental_state
   <- .print("Too many reset attempts, will wait for environment changes");
      .wait(3000);
      -+reset_tries(0);
      !extinguish_fires.

// Find any active fire and go to it
+!find_fire : fire(X, Y, I)[source(percept)] & I > 0 & not unreachable(X, Y)
   <- .print("Found fire at ", X, ", ", Y, " with intensity ", I);
      +active_fire(X, Y, I);
      !go_to(X, Y);
      extinguish;
      !find_fire.

// No active fires found, explore
+!find_fire : true
   <- .print("No fires found, exploring...");
      !explore;
      !find_fire.

+!explore : pos(X, Y)
   <- ?visited(VisitedList);
      .concat([pos(X,Y)], VisitedList, NewVisited);
      -+visited(NewVisited);
      
      // Choose direction that hasn't been visited yet
      if (not visited(X+1, Y)) {
         !go_to(X+1, Y);
      } elif (not visited(X, Y+1)) {
         !go_to(X, Y+1);
      } elif (not visited(X-1, Y)) {
         !go_to(X-1, Y);
      } elif (not visited(X, Y-1)) {
         !go_to(X, Y-1);
      } else {
         // All adjacent cells visited, choose random direction
         .random(R);
         if (R < 0.25) {
            !go_to(X+1, Y);
         } elif (R < 0.5) {
            !go_to(X, Y+1);
         } elif (R < 0.75) {
            !go_to(X-1, Y);
         } else {
            !go_to(X, Y-1);
         }
      }.

// Go to specific position
+!go_to(X, Y) : pos(X, Y)
   <- .print("Already at ", X, ", ", Y);
      extinguish;
      .wait(100);
      -+stuck_count(0).

// Detected potential loop or obstruction
+!go_to(X, Y) : pos(CX, CY) & stuck_count(SC) & SC > 5
   <- .print("Stuck while going to (", X, ",", Y, "). Marking as unreachable.");
      +unreachable(pos(X,Y));
      -+stuck_count(0);
      !find_fire.

// Handle wall/obstacles
+!go_to(X, Y) : pos(CX, CY) & obstacle(X, Y)[source(percept)]
   <- .print("Obstacle at target (", X, ",", Y, "). Finding another fire.");
      +unreachable(pos(X,Y));
      !find_fire.

// Normal movement - try to reach target
+!go_to(X, Y) : pos(CX, CY) & stuck_count(SC)
   <- .print("Moving toward (", X, ",", Y, ") from (", CX, ",", CY, ")");
      if (X > CX) { moveeast; }
      elif (X < CX) { movewest; }
      elif (Y > CY) { movenorth; }
      elif (Y < CY) { movesouth; }
      -+stuck_count(SC+1);
      .wait(100).

// Plan failed - try to recover
-!go_to(X, Y) 
   <- .print("Failed to go to (", X, ",", Y, ")");
      !reset_mental_state.

-!find_fire
   <- .print("Failed to find fire");
      !reset_mental_state.

-!extinguish_fires
   <- .print("Failed to extinguish fires");
      !reset_mental_state.

-!explore
   <- .print("Failed to explore");
      !reset_mental_state.

// Handle extinguished fires
+fire(X,Y,0)[source(percept)] : active_fire(X,Y,_)
   <- .print("Fire at ", X, ", ", Y, " has been extinguished");
      -active_fire(X,Y,_);
      ?extinguished_fires(EF);
      .concat([pos(X,Y)], EF, NewEF);
      -+extinguished_fires(NewEF).

// In case a fire appears again at a location we thought was extinguished
+fire(X,Y,I)[source(percept)] : I > 0 & extinguished_fires(EF) & .member(pos(X,Y), EF)
   <- .print("Fire has rekindled at ", X, ", ", Y);
      ?extinguished_fires(OldEF);
      .delete(pos(X,Y), OldEF, NewEF);
      -+extinguished_fires(NewEF);
      +active_fire(X,Y,I). 