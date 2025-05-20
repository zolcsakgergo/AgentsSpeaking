!init.

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

+!init
   <- .print("Cleaner initialized. Starting junk cleanup...");
      !clean_junk.

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
   
+!clean_junk 
   <- .print("No junk found. Starting patrol mode.");
      !patrol.

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

+!calculate_next_patrol_point
   <- ?patrol_x(X);
      ?patrol_y(Y);
      ?patrol_dir(Dir);
      ?current_room(Room);
      
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
            -+patrol_x(X+1);
         } else {
            -+patrol_y(Y+1);
            -+patrol_dir(down);
            if (Y+1 == 10 | Y+1 == 15) {
               if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
               } else {
                  -+patrol_y(Y+2);
               }
            }
         }
      } else {
         if (Dir == down) {
            if (Y < MaxY) {
               -+patrol_y(Y+1);
               if (Y+1 == 10 | Y+1 == 15) {
                  if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
                  } else {
                     -+patrol_y(Y+2);
                  }
               }
            } else {
               if (Room < 3) {
                  -+current_room(Room+1);
                  !use_door_to_next_room;
               } else {
                  -+current_room(1);
                  -+patrol_x(0);
                  -+patrol_y(0);
                  -+patrol_dir(right);
               }
            }
         } else {
            if (Dir == left) {
               if (X > 0) {
                  -+patrol_x(X-1);
               } else {
                  -+patrol_y(Y+1);
                  -+patrol_dir(down);
                  if (Y+1 == 10 | Y+1 == 15) {
                     if ((Y+1 == 10 & X == 5) | (Y+1 == 15 & X == 10)) {
                     } else {
                        -+patrol_y(Y+2);
                     }
                  }
               }
            } else { 
               if (Y > MinY) {
                  -+patrol_y(Y-1);
                  if (Y-1 == 10 | Y-1 == 15) {
                     if ((Y-1 == 10 & X == 5) | (Y-1 == 15 & X == 10)) {
                     } else {
                        -+patrol_y(Y-2);
                     }
                  }
               } else {
                  -+patrol_x(X+1);
                  -+patrol_dir(right);
               }
            }
         }
      }.

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

+!safe_move_to(X, Y)
   <- ?pos(cleaner,CX,CY);
      ?current_room(MyRoom);
      
      if (Y >= 0 & Y <= 9) {
         TargetRoom = 1;
      } else {
         if (Y >= 10 & Y <= 14) {
            TargetRoom = 2;
         } else {
            TargetRoom = 3;
         }
      }
      
      if (MyRoom \== TargetRoom) {
         if ((MyRoom == 1 & TargetRoom == 2) | (MyRoom == 2 & TargetRoom == 1)) {
            ?door1_pos(DoorX, DoorY);
            !go_to(DoorX, DoorY);
         } else {
            if ((MyRoom == 2 & TargetRoom == 3) | (MyRoom == 3 & TargetRoom == 2)) {
               ?door2_pos(DoorX, DoorY);
               !go_to(DoorX, DoorY);
            }
         }
      } else {
         !go_to(X, Y);
      }.

+!find_closest_junk(MX, MY, [junk(X,Y,Type)], X, Y, Type).

+!find_closest_junk(MX, MY, [junk(X,Y,Type)|Rest], ClosestX, ClosestY, ClosestType)
   <- Distance = math.abs(X-MX) + math.abs(Y-MY);
      !find_closest_junk(MX, MY, Rest, TmpX, TmpY, TmpType);
      TmpDistance = math.abs(TmpX-MX) + math.abs(TmpY-MY);
      if (Distance <= TmpDistance) {
         ClosestX = X;
         ClosestY = Y;
         ClosestType = Type;
      } else {
         ClosestX = TmpX;
         ClosestY = TmpY;
         ClosestType = TmpType;
      }.

+junk(X,Y,Type) : not .intend(clean_junk) & not .intend(go_to(_,_))
   <- .print("Detected new junk at ", X, ", ", Y);
      !clean_junk.

+!go_to(X,Y) : pos(cleaner,X,Y)
   <- .print("Already at target location ", X, ", ", Y).

+!go_to(X,Y)
   <- ?pos(cleaner,CX,CY);
      .print("Moving towards (", X, ",", Y, ")");
      move_towards(X,Y);
      .wait(100);
      !go_to(X,Y).
