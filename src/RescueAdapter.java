import rescueframework.RescueFramework;
import world.Map;

/**
 * Adapter class to bridge between our code and the RescueFramework
 */
public class RescueAdapter {

        /**
         * Get the current map from the framework
         */
        public static Map getMap() {
                try {
                        // Use reflection to access the map field
                        java.lang.reflect.Field mapField = RescueFramework.class.getDeclaredField("map");
                        mapField.setAccessible(true);
                        Map map = (Map) mapField.get(null);

                        if (map == null) {
                                throw new RuntimeException("Map is not initialized");
                        }

                        return map;
                } catch (Exception e) {
                        throw new RuntimeException("Failed to access map", e);
                }
        }

        /**
         * Refresh the GUI
         */
        public static void refresh() {
                RescueFramework.refresh();
        }

        /**
         * Log a message
         */
        public static void log(String message) {
                RescueFramework.log(message);
        }
}