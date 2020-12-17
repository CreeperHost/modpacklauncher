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
            String jsonSettings = gson.toJson(Settings.settings);
            File json = new File(Constants.BIN_LOCATION, "settings.json");
            if (!json.exists()) json.createNewFile();
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(json.getAbsolutePath()));
            fileWriter.write(jsonSettings);
            fileWriter.close();
        } catch (Exception ignored) {}
    }

    public static void loadSettings()
    {
        loadSettings(new File(Constants.BIN_LOCATION, "settings.json"), true);
    }

    // Only use directly during migrate logic to avoid saving settings immediately
    public static void  loadSettings(File json, boolean save)
    {
        try
        {

            if (json.exists()) {
                Gson gson = new Gson();
                JsonReader jr = new JsonReader(new BufferedReader(new FileReader(json.getAbsoluteFile())));
                Settings.settings = gson.fromJson(jr, HashMap.class);
                if (Settings.settings.getClass() != HashMap.class)
                {
                    Settings.settings = new HashMap<>();
                }
            } else {
                Settings.settings = new HashMap<>();
            }
            Settings.settings.put("instanceLocation", Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC));
            if (save)
            {
                saveSettings();
            }
        } catch (Exception err)
        {
            Settings.settings = new HashMap<>();
            Settings.settings.put("instanceLocation", Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC));
            if (save)
            {
                saveSettings();
            }
        }
    }

    public static String getDefaultThreadLimit(String arg)
    {
        int defaultThreads = (Runtime.getRuntime().availableProcessors() / 2) - 1;
        if(defaultThreads < 2) defaultThreads = 2;
        return String.valueOf(defaultThreads);
    }
}
