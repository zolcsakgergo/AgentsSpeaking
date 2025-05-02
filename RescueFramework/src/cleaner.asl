// Cleaner agent

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(cleaner,X,Y).
junk_types(["junk10", "junk20"]).

/* Initial goals */
!clean_junk.

/* Plans */

// Main plan to find and clean junk
+!clean_junk
   <- .print("Starting cleaner mission");
      !find_junk.

// If we've found junk, go to it and clean it
+!find_junk : active_junk(X,Y,Type)
   <- .print("Found junk at ", X, ", ", Y, " of type ", Type);
      !go_to(X,Y);
      !clean;
      !find_junk.

// If no active junk is perceived, explore to discover new areas
+!find_junk : not active_junk(_,_,_)
   <- !explore_unknown;
      !find_junk.

// If we're at a location with junk, stay put and clean
+!clean : pos(cleaner,X,Y) & obstacle(X,Y,Type)[source(percept)] & junk_types(Types) & .member(Type, Types)
   <- .print("Cleaning junk at ", X, ", ", Y, " of type ", Type);
      clean_junk; // Environment action to clean junk
      +cleaned(X,Y);
      .print("Junk cleaned at ", X, ", ", Y);
      !find_junk.

// If junk is already cleaned, move on
+!clean : pos(cleaner,X,Y) & cleaned(X,Y)
   <- .print("Already cleaned at ", X, ", ", Y);
      !find_junk.

// Default clean case if not at junk
+!clean
   <- !find_junk.

// Go to a specific location
+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y).
   
+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      move_towards(X,Y);
      !go_to(X,Y).

// Error handling for position perception
-?pos(cleaner,X,Y) 
   <- .print("Position not found, waiting for perception...");
      .wait(200);
      ?pos(cleaner,X,Y).

// Explore unknown areas to discover junk
+!explore_unknown
   <- ?pos(cleaner,X,Y);
      .print("Exploring from ", X, ", ", Y);
      !find_closest_unknown;
      !find_junk.

// Find closest unknown location (simpler implementation)
+!find_closest_unknown
   <- ?pos(cleaner,MyX,MyY);
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
   <- ?pos(cleaner,X,Y);
      .random(RX); .random(RY);
      NX = math.round(RX * 19);
      NY = math.round(RY * 19);
      !go_to(NX,NY).

// Helper rule to find closest active junk
+active_junk(X,Y,Type) : not .desire(go_to(X,Y))
   <- ?pos(cleaner,RX,RY);
      .findall(obstacle(PX,PY,PT), obstacle(PX,PY,PT) & not cleaned(PX,PY), JunkList);
      .length(JunkList, Len);
      if (Len > 0) {
         .nth(math.floor(math.random(Len)), JunkList, obstacle(TargetX,TargetY,_));
         !go_to(TargetX,TargetY);
      }.

// Detect junk types specifically for junk10 and junk20
+obstacle(X,Y,Type)[source(percept)] : junk_types(Types) & .member(Type, Types)
   <- .print("Detected junk at ", X, ", ", Y, " of type ", Type);
      +active_junk(X,Y,Type). 