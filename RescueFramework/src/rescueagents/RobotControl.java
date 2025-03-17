package rescueagents;

import java.util.ArrayList;

import rescueframework.AbstractRobotControl;
import world.Injured;
import world.Path;
import world.Robot;
import world.RobotPercepcion;
import world.AStarSearch;
import world.Cell;

/**
 *  RobotControl class to implement custom robot control strategies
 */
public class RobotControl extends AbstractRobotControl{
	static ArrayList<Injured> rescued = new ArrayList<Injured>();
	Injured planned = null;
    /**
     * Default constructor saving world robot object and percepcion
     * 
     * @param robot         The robot object in the world
     * @param percepcion    Percepcion of all robots
     */
    public RobotControl(Robot robot, RobotPercepcion percepcion) {
        super(robot, percepcion);
    }
    
    
    /**
     * Explore the map and rescue the injured.
     * 
     * @return  Return NULL for staying in place, 0 = step up, 1 = step right,
     *          2 = step down, 3 = step left
     */
    public Integer step() {
    	// Ha szallit valakit minden esetben vigye ki egybol
    	if(robot.hasInjured()) {
    		planned = null;
    		return percepcion.getShortestExitPath(robot.getLocation()).getFirstCell().directionFrom(robot.getLocation());
    	} else if(planned != null) {
    		return AStarSearch.search(robot.getLocation(), planned.getLocation(),-1).getFirstCell().directionFrom(robot.getLocation());
    	} else {
    		// Eletero szerint rendezett emberek listaja akik felfedezettek es kivihetoek
    		ArrayList<Injured> injuredOrderedList = new ArrayList<Injured>();

        	for(Injured injured : percepcion.getDiscoveredInjureds()) {
        		if(injured.getHealth() > 0 && injured.getLocation() != null && percepcion.getShortestExitPath(injured.getLocation()) != null && rescued.indexOf(injured) == -1) {
        			
        			injuredOrderedList.add(injured);
        			
        		}
        	}
        	injuredOrderedList.sort((a, b) -> {return a.getHealth() - b.getHealth();});
        	
        	// worst case kiviteli ido
        	ArrayList<Integer> rescueTime = new ArrayList<Integer>();
        	int t = 0;
        	Cell pos = robot.getLocation();
        	for(Injured injured : injuredOrderedList) {
        		Cell loc = injured.getLocation();
        		Path pathToInjured = AStarSearch.search(pos, loc,-1);
        		Path exitPath = percepcion.getShortestExitPath(loc);
        		
        		t += pathToInjured.getLength();
        		t += exitPath.getLength();
        		
        		rescueTime.add(t);
        		pos = exitPath.getPath().get(exitPath.getLength() - 1);
        	}
        	
        	Path path = percepcion.getShortestUnknownPath(robot.getLocation());
        	int unknownPathLength = 0;
        	if(path != null)
        		unknownPathLength = path.getLength();
        	
        	boolean rescue = false;
        	for(int i = 0; i < rescueTime.size(); i++) {
        		// Feleslegesen ne induljon el felterkepezni ha ugyis vissza indul menteni mielott odaerne
        		if(injuredOrderedList.get(i).getHealth() <= rescueTime.get(i) + unknownPathLength)
        			rescue = true;
        	}
        	
        	if (path != null && rescue == false) {
        		
        	    return path.getFirstCell().directionFrom(robot.getLocation());
        	} else if(injuredOrderedList.size() > 0) {
        		planned = injuredOrderedList.get(0);
        		rescued.add(planned);
        		return AStarSearch.search(robot.getLocation(), planned.getLocation(),-1).getFirstCell().directionFrom(robot.getLocation());
        	} else {
        		// Elhunytak kivitele ha mar nincsenek serultek
        		Path p = percepcion.getShortestInjuredPath(robot.getLocation());
        		if(p != null)
        			return p.getFirstCell().directionFrom(robot.getLocation());
        		else
        			return null;
        	}
    	}
    }
}