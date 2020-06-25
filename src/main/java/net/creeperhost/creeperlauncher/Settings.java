package net.creeperhost.creeperlauncher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;

public class Settings
{
    public static HashMap<String, String> settings = new HashMap<>();
    public static WebSocketAPI webSocketAPI;

    public static void saveSettings()
    {
        try
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            boolean migrate = !Settings.settings.getOrDefault("migrate", "no").equals("no");
            Settings.settings.remove("migrate");
            String jsonSettings = gson.toJson(Settings.settings);
            File json = new File(Constants.BIN_LOCATION, "settings.json");
            if (!json.exists()) json.createNewFile();
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(json.getAbsolutePath()));
            fileWriter.write(jsonSettings);
            fileWriter.close();
            if (migrate) Settings.settings.put("migrate", "yes");
        } catch (Exception ignored) {}
    }

    public static void loadSettings()
    {
        try
        {
            File json = new File(Constants.BIN_LOCATION, "settings.json");
            boolean old = false;
            if (!json.exists() || new File(Constants.WORKING_DIR, "instances").exists())
            {
                File jsonOld = new File(Constants.BIN_LOCATION_OURS, "settings.json");
                old = jsonOld.exists();
                if (old) {
                    json.getParentFile().mkdirs();
                    Files.copy(jsonOld.toPath(), json.toPath());
                }
            }

            if (json.exists()) {
                Gson gson = new Gson();
                JsonReader jr = new JsonReader(new BufferedReader(new FileReader(json.getAbsoluteFile())));
                Settings.settings = gson.fromJson(jr, HashMap.class);
                if (Settings.settings.getClass() != HashMap.class)
                {
                    Settings.settings = new HashMap<>();
                }
                if (old)
                {
                    Settings.settings.put("migrate", "yes");
                }
            } else {
                Settings.settings = new HashMap<>();
                json.createNewFile();
            }
            Settings.settings.put("instanceLocation", Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC));
            saveSettings();
        } catch (Exception err)
        {
            Settings.settings = new HashMap<>();
            Settings.settings.put("instanceLocation", Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC));
            saveSettings();
        }
    }

    public static String getDefaultThreadLimit(String arg)
    {
        int defaultThreads = (Runtime.getRuntime().availableProcessors() / 2) - 1;
        if(defaultThreads < 2) defaultThreads = 2;
        return String.valueOf(defaultThreads);
    }
}
