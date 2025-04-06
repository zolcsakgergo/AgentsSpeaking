package world;

import rescueagents.CleanerControl;
import rescueagents.RobotControl;
import rescueframework.AbstractRobotControl;

/**
 * Robot on the map
 */
public class Robot {
    // Location of the robot
    private Cell location;

    // The fire being carried by the robot
    private Fire targetFire = null;

    // The control object of th robot
    private AbstractRobotControl control;

    // The image to display for the robot
    private String image;

    /**
     * Default constructor
     * 
     * @param startCell  The start cell of the robot
     * @param percepcion Percepcion of the robot
     * @param isCleaner  True if this robot should be a cleaner
     */
    public Robot(Cell startCell, RobotPercepcion percepcion, boolean isCleaner) {
        location = startCell;
        if (isCleaner) {
            control = new CleanerControl(this, percepcion);
            image = "wallee_trash";
        } else {
            control = new RobotControl(this, percepcion);
            image = "robot1";
        }
    }

    /**
     * Return the robot location
     * 
     * @return The robot location
     */
    public Cell getLocation() {
        return location;
    }

    /**
     * Set the location of the robot
     * 
     * @param newLocation The new location of the robot
     */
    public void setCell(Cell newLocation) {
        location = newLocation;
    }

    /**
     * Return true if the robot is currently carrying a fire
     * 
     * @return True if the robot is carrying a fire
     */
    public boolean hasFire() {
        return targetFire != null;
    }

    /**
     * Pick up a fire if the robot does not carries any
     */
    public boolean setTargetFire(Cell location) {
        if (location.hasFire()) {
            targetFire = location.getFire();
            location.setFire(null);
            return true;
        }
        return false;
    }

    /**
     * Drop the fire if the robot carries one
     * 
     * @return Return the fire that is dropped
     */
    public Fire extinguishFire() {
        Fire result = targetFire;
        targetFire = null;
        return result;
    }

    /**
     * Return the fire being carried by the robot
     * 
     * @return The fire being carried by the robot
     */
    public Fire getTargetFire() {
        return targetFire;
    }

    /**
     * Call the AbstractRobotControl to decide the next step of the robot
     * 
     * @return Stepping direction of the robot or NULL to stay in place
     */
    public Integer step() {
        if (control == null)
            return null;
        return control.step();
    }

    /**
     * Get the image to display for this robot
     * 
     * @return The image filename
     */
    public String getImage() {
        return image;
    }
}
