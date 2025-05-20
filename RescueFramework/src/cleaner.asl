
at(P) :- pos(P,X,Y) & pos(cleaner,X,Y).

junk(5, 7, "junk").
junk(12, 3, "junk").
junk(12, 7, "junk").
junk(8, 16, "junk").

!clean_junk.

+!clean_junk : junk(X,Y,Type)
   <- .print("Found junk at ", X, ", ", Y, " of type ", Type);
      !go_to(X,Y);
      clean_junk;
      -junk(X,Y,Type);
      !clean_junk.
   
+!clean_junk 
   <- .print("Waiting for junk");
      .wait(2000);
      !clean_junk.

+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y);
      .wait(100).

+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");

      move_towards(X,Y);


      .wait(100);
      !go_to(X,Y).
