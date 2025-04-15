// Firefighter agent

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(firefighter,X,Y).

/* Initial goals */
!extinguish_fires.

/* Plans */

// Main plan to search for and extinguish fires
+!extinguish_fires
   <- !find_fires.

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

// Explore unknown areas to discover fires
+!explore_unknown
   <- ?pos(firefighter,X,Y);
      .print("Exploring from ", X, ", ", Y);
      get_closest_unknown(X,Y,NX,NY);
      !go_to(NX,NY).

// Helper rule to find closest active fire
+active_fire(X,Y,I) : not .desire(go_to(X,Y))
   <- .findall(fire(PX,PY,PI), fire(PX,PY,PI) & PI > 0, Fires);
      .sort(Fires, SortedFires); // Sort by intensity (highest first)
      .nth(0, SortedFires, fire(TX,TY,_)); // Get highest intensity fire
      !go_to(TX,TY).

// Handle perception of new fires
+fire(X,Y,I)[source(percept)] : I > 0
   <- .print("Detected fire at ", X, ", ", Y, " with intensity ", I);
      +active_fire(X,Y,I).

// Handle extinguished fires
+fire(X,Y,0)[source(percept)] : active_fire(X,Y,_)
   <- .print("Fire at ", X, ", ", Y, " has been extinguished");
      -active_fire(X,Y,_). 