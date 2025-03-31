package world;

/**
 * Represents a fire hazard in the simulation
 */
public class Fire {
    private Cell location;
    private int intensity;
    private boolean discovered = false;

    /**
     * Creates a new fire at the specified location with given intensity
     * 
     * @param location  The cell where the fire is located
     * @param intensity Initial intensity of the fire (0-1000)
     */
    public Fire(Cell location, int intensity) {
        this.location = location;
        this.intensity = intensity;
    }

    /**
     * @return The cell where the fire is located
     */
    public Cell getLocation() {
        return location;
    }

    /**
     * @return Current intensity of the fire
     */
    public int getIntensity() {
        return intensity;
    }

    /**
     * Reduce the fire's intensity by the specified amount
     * 
     * @param amount Amount to reduce intensity by
     */
    public void reduceIntensity(int amount) {
        intensity = Math.max(0, intensity - amount);
    }

    /**
     * @return Whether this fire has been discovered by robots
     */
    public boolean isDiscovered() {
        return discovered;
    }

    /**
     * Mark this fire as discovered
     */
    public void setDiscovered() {
        discovered = true;
    }

    /**
     * Mark the fire as extinguished
     */
    public void setExtinguished() {
        intensity = 0;
    }

    /**
     * @return Whether the fire is still active (intensity > 0)
     */
    public boolean isActive() {
        return intensity > 0;
    }
}
