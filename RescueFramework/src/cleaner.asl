// The cleaner agent for the rescue operation.

/* Initial beliefs and rules */
visited([]).
stuck_count(0).
reset_tries(0).

// Check if position was visited to detect loops
visited(X, Y) :- visited(L) & .member(pos(X,Y), L).

// Unreachable check
unreachable(X, Y) :- unreachable(pos(X,Y)).

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(cleaner,X,Y).
junk_types(["junk10", "junk20"]).
// Track already visited positions to avoid loops
visited_positions([]).
// Track junk we failed to reach
unreachable_junk([]).

/* Initial goals */
!clean_junk.

/* Plans */

// Main plan to find and clean junk
+!clean_junk : true
   <- !find_junk.

// If we've found junk, go to it and clean it that's not unreachable
+!find_junk : active_junk(X,Y,Type) & not unreachable(X,Y)
   <- .print("Found junk at ", X, ", ", Y, " of type ", Type);
      !go_to(X,Y);
      !clean;
      !find_junk.

// If we've only found unreachable junk, explore more
+!find_junk : active_junk(X,Y,Type) & unreachable(X,Y)
   <- .print("Skipping unreachable junk at ", X, ", ", Y);
      !explore_unknown;
      !find_junk.

// If no active junk is perceived, explore to discover new areas
+!find_junk : not active_junk(_,_,_)
   <- !explore_unknown;
      !find_junk.

// Failure recovery for find_junk - reset and try again
-!find_junk
   <- .print("Failed to find junk");
      !reset_mental_state.

// If we're at a location with junk, stay put and clean
+!clean : pos(cleaner,X,Y) & obstacle(X,Y,Type)[source(percept)] & junk_types(Types) & .member(Type, Types)
   <- .print("Cleaning junk at ", X, ", ", Y, " of type ", Type);
      clean_junk; // Environment action to clean junk
      -active_junk(X,Y,_); // Remove active junk belief
      +cleaned(X,Y);
      // Reset visited positions after successful cleaning
      -+visited_positions([]);
      .print("Junk cleaned at ", X, ", ", Y);
      !find_junk.

// If junk is already cleaned, move on
+!clean : pos(cleaner,X,Y) & cleaned(X,Y)
   <- .print("Already cleaned at ", X, ", ", Y);
      -active_junk(X,Y,_); // Ensure we remove any lingering active junk belief
      !find_junk.

// Default clean case if not at junk
+!clean
   <- !find_junk.

// Failure recovery for clean
-!clean
   <- .print("Failed to clean junk");
      !reset_mental_state.

// Go to a specific location
+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y);
      clean;
      .wait(100);
      -+stuck_count(0).
   
// Movement with loop detection and tracking
+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      ?visited_positions(VP);
      
      // Calculate steps taken so far to this position
      .count(.member(pos(CX,CY), VP), LoopCount);
      
      // If we've been here too many times, mark this junk as unreachable
      if (LoopCount > 3) {
         .print("** This junk seems unreachable, marking it and trying another target **");
         +unreachable(X,Y);
         -active_junk(X,Y,_); // Remove from active junk list
         -+visited_positions([]); // Reset visited positions
         !find_junk;
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
            
            .print("Taking larger step to break loop: (", NX, ",", NY, ")");
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
-?pos(cleaner,X,Y) 
   <- .print("Position not found, waiting for perception...");
      .wait(200);
      ?pos(cleaner,X,Y).

// Failure handling for go_to
-!go_to(X,Y)
   <- .print("Failed to reach position (", X, ",", Y, "), trying a different approach");
      ?pos(cleaner,CX,CY);
      if (math.abs(X-CX) > math.abs(Y-CY)) {
         // Try moving vertically first
         NY = CY + math.round((Y-CY)/math.abs(Y-CY));
         !go_to(CX,NY);
      } else {
         // Try moving horizontally first
         NX = CX + math.abs(X-CX);
         !go_to(NX,CY);
      }.

// Explore unknown areas to discover junk
+!explore_unknown
   <- ?pos(cleaner,X,Y);
      .print("Exploring from ", X, ", ", Y);
      !find_closest_unknown;
      .wait(200); // Wait a bit to let perceptions update
      !find_junk.

// Find closest unknown location with better distance calculation
+!find_closest_unknown
   <- ?pos(cleaner,MyX,MyY);
      .findall(unknown(UX,UY,Dist), (unknown(UX,UY) & Dist = math.sqrt((UX-MyX)*(UX-MyX) + (UY-MyY)*(UY-MyY))), UnknownsWithDist);
      .length(UnknownsWithDist, Len);
      if (Len > 0) {
         .sort(UnknownsWithDist, SortedUnknowns); // Sort by distance (closest first)
         .nth(0, SortedUnknowns, unknown(TargetX,TargetY,_));
         !go_to(TargetX,TargetY);
      } else {
         !random_move;
      }.

// Move randomly when there's nothing else to do
+!random_move
   <- ?pos(cleaner,X,Y);
      .random(RX); .random(RY);
      NX = math.round(X + (RX-0.5) * 5); // Move at most 2.5 spaces from current
      NY = math.round(Y + (RY-0.5) * 5);
      // Keep within grid bounds
      if (NX < 0) { NX = 0; }
      if (NY < 0) { NY = 0; }
      if (NX >= 20) { NX = 19; }
      if (NY >= 20) { NY = 19; }
      .print("Moving randomly to ", NX, ", ", NY);
      !go_to(NX,NY).

// Better plan to find closest active junk
+active_junk(X,Y,Type) : not .desire(go_to(X,Y)) & not unreachable(X,Y)
   <- ?pos(cleaner,MyX,MyY);
      .findall(junk(PX,PY,PT,Dist), 
              (obstacle(PX,PY,PT) & junk_types(Types) & .member(PT, Types) & 
               not cleaned(PX,PY) & not unreachable(PX,PY) &
               Dist = math.sqrt((PX-MyX)*(PX-MyX) + (PY-MyY)*(PY-MyY))), 
              JunkList);
      .length(JunkList, Len);
      if (Len > 0) {
         .sort(JunkList, SortedJunk); // Sort by distance (closest first)
         .nth(0, SortedJunk, junk(TargetX,TargetY,_,_));
         .print("Selected closest junk at ", TargetX, ", ", TargetY);
         !go_to(TargetX,TargetY);
      }.

// Detect junk types specifically for junk10 and junk20
+obstacle(X,Y,Type)[source(percept)] : junk_types(Types) & .member(Type, Types) & not cleaned(X,Y)
   <- .print("Detected junk at ", X, ", ", Y, " of type ", Type);
      +active_junk(X,Y,Type).

// Handle case when obstacle is no longer perceived (cleaned by environment)
-obstacle(X,Y,Type)[source(percept)] : active_junk(X,Y,Type)
   <- .print("Junk at ", X, ", ", Y, " is no longer there");
      -active_junk(X,Y,Type);
      +cleaned(X,Y).

+!reset_mental_state : reset_tries(R) & R < 3
   <- .print("Resetting mental state. Attempt: ", R+1);
      -+visited([]);
      -+stuck_count(0);
      -+reset_tries(R+1);
      !clean_junk.

+!reset_mental_state
   <- .print("Too many reset attempts, will wait for environment changes");
      .wait(3000);
      -+reset_tries(0);
      !clean_junk.

-!clean_junk
   <- .print("Failed to clean junk");
      !reset_mental_state.

-!explore
   <- .print("Failed to explore");
      !reset_mental_state.

// Detected potential loop or obstruction
+!go_to(X, Y) : pos(CX, CY) & stuck_count(SC) & SC > 5
   <- .print("Stuck while going to (", X, ",", Y, "). Marking as unreachable.");
      +unreachable(pos(X,Y));
      -+stuck_count(0);
      !clean_junk.

// Handle wall/obstacles - try to go around
+!go_to(X, Y) : pos(CX, CY) & obstacle(X, Y)[source(percept)]
   <- .print("Obstacle at target (", X, ",", Y, "). Finding another junk.");
      +unreachable(pos(X,Y));
      !clean_junk.

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

-!clean_junk
   <- .print("Failed to clean junk");
      !reset_mental_state.

-!explore
   <- .print("Failed to explore");
      !reset_mental_state. 