package net.creeperhost.creeperlauncher.util;

import net.creeperhost.creeperlauncher.CreeperLogger;

import java.util.HashMap;

public class SettingsChangeUtil {
    private static HashMap<String, ISettingsChangedHandler> settingsChangedListeners = new HashMap<>();

    public static void registerListener(String key, ISettingsChangedHandler handler) {
        if (settingsChangedListeners.containsKey(key)) {
            CreeperLogger.INSTANCE.warning("New handler for " + key + " when already exists, doing nothing");
            return;
        }
        settingsChangedListeners.put(key, handler);
    }

    public static boolean settingsChanged(String key, String value) {
        if (settingsChangedListeners.containsKey(key)) {
            try {
                return settingsChangedListeners.get(key).handle(key, value);
            } catch (Exception e) {
            }
        }
        return true;
    }

    public interface ISettingsChangedHandler {
        boolean handle(String key, String value);
    }
}
