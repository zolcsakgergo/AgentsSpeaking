// Firefighter agent

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(firefighter,X,Y).

/* Initial goals */
!extinguish_fires.

/* Plans */

// Main plan to search for and extinguish fires
+!extinguish_fires
   <- .print("Starting firefighter mission");
      !find_fires.

// If we've found a fire, prioritize based on intensity and go extinguish it
+!find_fires : active_fire(X,Y,I)
   <- .print("Found fire at ", X, ", ", Y, " with intensity ", I);
      !go_to(X,Y);
      !extinguish;
      !find_fires.

// If no active fire is perceived, explore to discover new areas
+!find_fires : not active_fire(_,_,_)
   <- !explore_unknown;
      !find_fires.

// If we're at a location with fire, stay put and extinguish
+!extinguish : pos(firefighter,X,Y) & fire(X,Y,I) & I > 0
   <- .print("Extinguishing fire at ", X, ", ", Y);
      reduce_intensity(200);
      !extinguish.
      
// If fire is fully extinguished, move on
+!extinguish : pos(firefighter,X,Y) & fire(X,Y,0)
   <- .print("Fire extinguished at ", X, ", ", Y);
      !find_fires.

// Default extinguish case if not at a fire
+!extinguish
   <- !find_fires.

// Go to a specific location
+!go_to(X,Y) : pos(firefighter,X,Y)
   <- .print("Already at target location ", X, ", ", Y).
   
+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      move_towards(X,Y);
      !go_to(X,Y).

// Error handling for position perception
-?pos(firefighter,X,Y) 
   <- .print("Position not found, waiting for perception...");
      .wait(200);
      ?pos(firefighter,X,Y).

// Explore unknown areas to discover fires
+!explore_unknown
   <- ?pos(firefighter,X,Y);
      .print("Exploring from ", X, ", ", Y);
      !find_closest_unknown;
      !find_fires.

// Helper rule to find closest active fire
+active_fire(X,Y,I) : not .desire(go_to(X,Y))
   <- .findall(fire(PX,PY,PI), fire(PX,PY,PI) & PI > 0, Fires);
      .length(Fires, Len);
      if (Len > 0) {
         .sort(Fires, SortedFires); // Sort by intensity (highest first)
         .nth(0, SortedFires, fire(TX,TY,_)); // Get highest intensity fire
         !go_to(TX,TY);
      }.

// Find closest unknown location (simpler implementation)
+!find_closest_unknown
   <- ?pos(firefighter,MyX,MyY);
      .findall(unknown(UX,UY), unknown(UX,UY), Unknowns);
      .length(Unknowns, Len);
      if (Len > 0) {
         .nth(math.floor(math.random(Len)), Unknowns, unknown(TargetX,TargetY));
         !go_to(TargetX,TargetY);
      } else {
         !random_move;
      }.

// Move randomly when there's nothing else to do
+!random_move
   <- ?pos(firefighter,X,Y);
      .random(RX); .random(RY);
      NX = math.round(RX * 19);
      NY = math.round(RY * 19);
      !go_to(NX,NY).

// Handle perception of new fires
+fire(X,Y,I)[source(percept)] : I > 0
   <- .print("Detected fire at ", X, ", ", Y, " with intensity ", I);
      +active_fire(X,Y,I).

// Handle extinguished fires
+fire(X,Y,0)[source(percept)] : active_fire(X,Y,_)
   <- .print("Fire at ", X, ", ", Y, " has been extinguished");
      -active_fire(X,Y,_). 