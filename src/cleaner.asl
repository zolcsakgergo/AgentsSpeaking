// Cleaner agent

/* Initial beliefs */
at(P) :- pos(P,X,Y) & pos(cleaner,X,Y).
junk_types(["junk10", "junk20"]).
cleaning_time(1). // Steps needed to clean junk

/* Initial goals */
!clean_junk.

/* Plans */

// Main plan to find and clean junk
+!clean_junk
   <- !find_junk.

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
+!clean : pos(cleaner,X,Y) & junk(X,Y,Type) & not cleaned(X,Y)
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

// Explore unknown areas to discover junk
+!explore_unknown
   <- ?pos(cleaner,X,Y);
      .print("Exploring from ", X, ", ", Y);
      get_closest_unknown(X,Y,NX,NY);
      !go_to(NX,NY).

// Helper rule to find closest active junk
+active_junk(X,Y,Type) : not .desire(go_to(X,Y))
   <- .findall(junk(PX,PY,PT), junk(PX,PY,PT) & not cleaned(PX,PY), JunkList);
      .length(JunkList, Len);
      if (Len > 0) {
         .min_distance(JunkList, junk(TX,TY,_));
         !go_to(TX,TY);
      }.

// Handle perception of new junk
+junk(X,Y,Type)[source(percept)] : not cleaned(X,Y)
   <- .print("Detected junk at ", X, ", ", Y, " of type ", Type);
      +active_junk(X,Y,Type).

// Handle cleaned junk
+cleaned(X,Y) : active_junk(X,Y,_)
   <- .print("Junk at ", X, ", ", Y, " has been cleaned");
      -active_junk(X,Y,_).

// Detect junk types specifically for junk10 and junk20
+obstacle(X,Y,Type)[source(percept)] : junk_types(Types) & .member(Type, Types)
   <- +junk(X,Y,Type);
      +active_junk(X,Y,Type). 