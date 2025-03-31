package rescueframework;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Static class for reading and writing settings to the settings.txt file
 */
public class Settings {
    // The Properties object storing the settings
    private static Properties properties = new Properties();

    /**
     * Load settings from file
     */
    public static void load() {
        InputStream configFile = null;

        // Try multiple paths to open the file
        try {
            RescueFramework.log("Trying to load settings from RescueFramework/settings.txt");
            configFile = new FileInputStream("RescueFramework/settings.txt");
        } catch (FileNotFoundException e) {
            try {
                RescueFramework.log("Trying to load settings from settings.txt");
                configFile = new FileInputStream("settings.txt");
            } catch (FileNotFoundException e1) {
                try {
                    RescueFramework.log("Trying to load settings from ./settings.txt");
                    configFile = new FileInputStream("./settings.txt");
                } catch (FileNotFoundException e2) {
                    RescueFramework.log("Failed to load settings from any location");
                    // Initialize with defaults
                    properties.setProperty("map", "Spaceship.txt");
                    properties.setProperty("agent_count", "1");
                    properties.setProperty("speed", "100");
                    return;
                }
            }
        }

        // Read configuration
        try {
            properties.load(configFile);
            configFile.close();
            RescueFramework.log("Settings loaded successfully");
        } catch (IOException e) {
            RescueFramework.log("Error loading settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Return string value from the settings
     * 
     * @param key          The key to read value from
     * @param defaultValue Default value if the key does not exists
     * @return String value belonging the the key
     */
    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Return int value from the settings
     * 
     * @param key          The key to read value from
     * @param defaultValue Default value if the key does not exists
     * @return Integer value belonging the the key
     */
    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    /**
     * Save the settings to file
     */
    public static void save() {
        try {
            properties.store(new FileOutputStream("RescueFramework/settings.txt"), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the settings and save to file
     * 
     * @param key   The key to update
     * @param value The string value to update with
     */
    public static void setString(String key, String value) {
        properties.setProperty(key, value);
        save();
    }

    /**
     * Update the settings and save to file
     * 
     * @param key   The key to update
     * @param value The int value to update with
     */
    public static void setInt(String key, int value) {
        properties.setProperty(key, value + "");
        save();
    }
}
