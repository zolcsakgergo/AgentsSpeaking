!init.

control_room(10, 14).
hibernation_room(15, 19).

door2_x(10).

current_room(3).

patrol_direction(false).
patrol_row(19).
patrol_col(19).

+!init
   <- .print("Firefighter initialized. Starting fire extinguishing...");
      !extinguish_fires.

+!extinguish_fires : fire(_,_,_)
   <- ?pos(firefighter,MX,MY);
      
      .findall(fire(X,Y,I), fire(X,Y,I), Fires);
      .print("Currently perceiving ", .length(Fires), " fires");
      
      if (.length(Fires) > 0) {
         !find_closest_fire(MX, MY, Fires, ClosestX, ClosestY, Intensity);
         .print("Found closest fire at ", ClosestX, ", ", ClosestY, " with intensity ", Intensity);
         !go_to(ClosestX, ClosestY);
         .print("Reducing fire intensity at ", ClosestX, ", ", ClosestY);
      
         .abolish(fire(ClosestX, ClosestY, _));
      
         reduce_intensity(100);
      
         .wait(200);
      
         .drop_all_intentions;
         !extinguish_fires;
      } else {
         .print("No fires found. Starting patrol mode.");
         !patrol;
      }.

+!extinguish_fires 
   <- .print("No fires found. Starting patrol mode.");
      !patrol.

+!patrol
   <- ?pos(firefighter,CX,CY);
      ?current_room(Room);
      
      !update_current_room(CY);
      ?current_room(NewRoom);
      
      if (NewRoom \== Room) {
         .print("Entering new room: ", NewRoom);
         !set_patrol_start_point(NewRoom);
      }
      
      ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_direction(Direction);
      
      if (not pos(firefighter,Col,Row)) {
         if ((NewRoom == 2 & Row > 14) | (NewRoom == 3 & Row < 15)) {
            .print("Need to go through a door to reach target");
            !navigate_to_door(NewRoom, Col, Row);
         } else {
            !go_to(Col, Row);
         }
      }
      
      !calculate_next_patrol_point;
      
      .findall(fire(X,Y,I), fire(X,Y,I), Fires);
      if (.length(Fires) > 0) {
         .print("Fire detected during patrol. Switching to extinguishing mode.");
         !extinguish_fires;
      } else {
         .wait(200);
         !patrol;
      }.

+!update_current_room(Y)
   <- ?control_room(CMin, CMax);
      ?hibernation_room(HMin, HMax);
      
      if (Y >= CMin & Y <= CMax) {
         -+current_room(2);
      } else {
         if (Y >= HMin & Y <= HMax) {
            -+current_room(3);
         }
      }.

+!set_patrol_start_point(Room)
   <- if (Room == 2) {
         -+patrol_row(14);
         -+patrol_col(19);
         -+patrol_direction(false);
      } else {
         -+patrol_row(19);
         -+patrol_col(19);
         -+patrol_direction(false);
      }.

+!navigate_to_door(TargetRoom, TargetX, TargetY)
   <- ?door2_x(DoorX);
      !go_to(DoorX, 15).

+!calculate_next_patrol_point
   <- ?patrol_row(Row);
      ?patrol_col(Col);
      ?patrol_direction(Direction);
      ?current_room(Room);
      
      if (Room == 2) {
         ?control_room(MinY, MaxY);
      } else {
         ?hibernation_room(MinY, MaxY);
      }
      
      if (Direction) {
         if (Col < 19) {
            -+patrol_col(Col+1);
         } else {
            if (Row > MinY) {
               if (Row-1 == 10 | Row-1 == 15) {
                  if (Row-1 == 10) {
                     if (Col == 5) {
                        -+patrol_row(Row-1);
                     } else {
                        -+patrol_row(Row-2);
                     }
                  } else {
                     if (Col == 10) {
                        -+patrol_row(Row-1);
                     } else {
                        -+patrol_row(Row-2);
                     }
                  }
               } else {
                  -+patrol_row(Row-1);
               }
               -+patrol_direction(false);
            } else {
               if (Room == 2) {
                  if (Row == MinY) {
                     if (Col == 19) {
                        -+current_room(3);
                        -+patrol_row(19);
                        -+patrol_col(19);
                        -+patrol_direction(false);
                     }
                  }
               } else {
                  if (Room == 3) {
                     -+current_room(2);
                     !set_patrol_start_point(2);
                  }
               }
            }
         }
      } else {
         if (Col > 0) {
            -+patrol_col(Col-1);
         } else {
            if (Row > MinY) {
               if (Row-1 == 10 | Row-1 == 15) {
                  if (Row-1 == 10) {
                     if (Col == 5) {
                        -+patrol_row(Row-1);
                     } else {
                        -+patrol_row(Row-2);
                     }
                  } else {
                     if (Col == 10) {
                        -+patrol_row(Row-1);
                     } else {
                        -+patrol_row(Row-2);
                     }
                  }
               } else {
                  -+patrol_row(Row-1);
               }
               -+patrol_direction(true);
            } else {
               if (Room == 2) {
                  if (Row == MinY) {
                     if (Col == 0) {
                        -+current_room(3);
                        -+patrol_row(19);
                        -+patrol_col(19);
                        -+patrol_direction(false);
                     }
                  }
               } else {
                  if (Room == 3) {
                     -+current_room(2);
                     !set_patrol_start_point(2);
                  }
               }
            }
         }
      }.

+fire(X,Y,I) : not .intend(extinguish_fires) & not .intend(go_to(_,_))
   <- .print("Detected new fire at ", X, ", ", Y, " with intensity ", I);
      !extinguish_fires.

+!find_closest_fire(MX, MY, [fire(X,Y,I)], X, Y, I).

+!find_closest_fire(MX, MY, [fire(X,Y,I)|Rest], ClosestX, ClosestY, ClosestI)
   <- Distance = math.abs(X-MX) + math.abs(Y-MY);
      !find_closest_fire(MX, MY, Rest, TmpX, TmpY, TmpI);
      TmpDistance = math.abs(TmpX-MX) + math.abs(TmpY-MY);
      if (Distance <= TmpDistance) {
         ClosestX = X;
         ClosestY = Y;
         ClosestI = I;
      } else {
         ClosestX = TmpX;
         ClosestY = TmpY;
         ClosestI = TmpI;
      }.

+!go_to(X,Y) : pos(firefighter,X,Y)
   <- .print("Already at target location ", X, ", ", Y);
      .wait(100).

+!go_to(X,Y)
   <- ?pos(firefighter,CX,CY);
      .print("Moving from (", CX, ",", CY, ") towards (", X, ",", Y, ")");
      if (pos(cleaner,NX,NY)) {
         MoveAround = false;
         
         if (math.abs(NX-(CX+1)) < 0.1) {
            if (math.abs(NY-CY) < 0.1) {
               if (CX < X) {
                  MoveAround = true;
               }
            }
         }
         
         if (math.abs(NX-(CX-1)) < 0.1) {
            if (math.abs(NY-CY) < 0.1) {
               if (CX > X) {
                  MoveAround = true;
               }
            }
         }
         
         if (math.abs(NX-CX) < 0.1) {
            if (math.abs(NY-(CY+1)) < 0.1) {
               if (CY < Y) {
                  MoveAround = true;
               }
            }
         }
         
         if (math.abs(NX-CX) < 0.1) {
            if (math.abs(NY-(CY-1)) < 0.1) {
               if (CY > Y) {
                  MoveAround = true;
               }
            }
         }
         
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
            move_towards(X,Y);
         }
      } else {
         move_towards(X,Y);
      }
      .wait(100);
      !go_to(X,Y).

