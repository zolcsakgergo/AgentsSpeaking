package rescueagents;

import java.util.ArrayList;
import java.util.Comparator;

import rescueframework.AbstractRobotControl;
import rescueframework.RescueFramework;
import world.AStarSearch;
import world.Fire;
import world.Path;
import world.Robot;
import world.RobotPercepcion;

/**
 * RobotControl class to implement custom robot control strategies
 */
public class RobotControl extends AbstractRobotControl {
	static ArrayList<Fire> extinguished = new ArrayList<Fire>();
	Fire planned = null;
	private int stepsOnFire = 0;
	private static final int EXTINGUISH_TIME = 1; // Steps needed to extinguish a fire

	/**
	 * Default constructor saving world robot object and percepcion
	 * 
	 * @param robot      The robot object in the world
	 * @param percepcion Percepcion of all robots
	 */
	public RobotControl(Robot robot, RobotPercepcion percepcion) {
		super(robot, percepcion);
	}

	/**
	 * Extinguish fires around the ship.
	 * 
	 * @return Return NULL for staying in place, 0 = step up, 1 = step right,
	 *         2 = step down, 3 = step left
	 */
	public Integer step() {
		// Add null check for robot location
		if (robot == null || robot.getLocation() == null) {
			RescueFramework.log("Robot or location is null");
			return null;
		}

		// If we have a fire on our current cell, extinguish it quickly then move on
		if (robot.getLocation().hasFire()) {
			stepsOnFire++;
			Fire currentFire = robot.getLocation().getFire();

			// If we've spent enough time on the fire, consider it extinguished
			if (stepsOnFire >= EXTINGUISH_TIME) {
				// Force extinguish the fire completely
				currentFire.setExtinguished();
				robot.getLocation().setFire(null);

				// Remove from active fires and move to extinguished list
				if (!extinguished.contains(currentFire)) {
					extinguished.add(currentFire);
				}

				RescueFramework.log("Fire extinguished at " + robot.getLocation().getX() + ","
						+ robot.getLocation().getY() + " - moving to next target");
				planned = null;
				stepsOnFire = 0;

				// Immediately find the next fire
				Fire nextFire = findClosestFire(getActiveDiscoveredFires());
				if (nextFire != null) {
					planned = nextFire;
					Path firePath = AStarSearch.search(robot.getLocation(), planned.getLocation(),
							-1);
					if (firePath != null && firePath.getFirstCell() != null) {
						return firePath.getFirstCell().directionFrom(robot.getLocation());
					}
				}

				// If no fires left, explore
				Path explorePath = percepcion.getShortestUnknownPath(robot.getLocation());
				if (explorePath != null && explorePath.getFirstCell() != null) {
					return explorePath.getFirstCell().directionFrom(robot.getLocation());
				}
			}

			// Continue extinguishing (stay in place)
			return null;
		} else if (planned != null && planned.getLocation() != null && planned.isActive()) {
			// Add null check for AStarSearch result
			Path path = AStarSearch.search(robot.getLocation(), planned.getLocation(), -1);
			if (path == null || path.getFirstCell() == null) {
				planned = null;
				stepsOnFire = 0;
				return null;
			}
			// Continue to planned fire
			return path.getFirstCell().directionFrom(robot.getLocation());
		} else {
			// Find closest discovered fire
			stepsOnFire = 0;
			ArrayList<Fire> discoveredFires = getActiveDiscoveredFires();

			// Sort fires by distance (closest first)
			Fire closestFire = findClosestFire(discoveredFires);

			Path path = percepcion.getShortestUnknownPath(robot.getLocation());

			if (path != null && discoveredFires.isEmpty()) {
				// Explore if no fires to extinguish
				return path.getFirstCell().directionFrom(robot.getLocation());
			} else if (closestFire != null) {
				// Go to closest fire
				planned = closestFire;
				extinguished.add(planned);

				Path firePath = AStarSearch.search(robot.getLocation(), planned.getLocation(), -1);
				if (firePath != null && firePath.getFirstCell() != null) {
					return firePath.getFirstCell().directionFrom(robot.getLocation());
				} else {
					// If path not found, try exploring
					return path != null ? path.getFirstCell().directionFrom(robot.getLocation())
							: null;
				}
			} else {
				// Stay in place if no action needed
				return null;
			}
		}
	}

	/**
	 * Gets a list of discovered fires that are still active and haven't been
	 * targeted
	 */
	private ArrayList<Fire> getActiveDiscoveredFires() {
		ArrayList<Fire> discoveredFires = new ArrayList<Fire>();

		for (Fire fire : percepcion.getDiscoveredFires()) {
			if (fire.isActive() && fire.getLocation() != null && extinguished.indexOf(fire) == -1) {
				discoveredFires.add(fire);
			}
		}

		return discoveredFires;
	}

	/**
	 * Find the closest fire to the robot
	 * 
	 * @param fires List of fires to check
	 * @return The closest fire or null if none found
	 */
	private Fire findClosestFire(ArrayList<Fire> fires) {
		if (fires.isEmpty()) {
			return null;
		}

		Fire closest = null;
		int shortestDistance = Integer.MAX_VALUE;

		for (Fire fire : fires) {
			Path path = AStarSearch.search(robot.getLocation(), fire.getLocation(), -1);
			if (path != null) {
				int distance = path.getLength();
				if (distance < shortestDistance) {
					shortestDistance = distance;
					closest = fire;
				}
			}
		}

		return closest;
	}
}