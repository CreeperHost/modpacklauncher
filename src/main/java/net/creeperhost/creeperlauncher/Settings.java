package net.creeperhost.creeperlauncher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Settings
{
    public static HashMap<String, String> settings = new HashMap<String, String>();
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
        try
        {
            File json = new File(Constants.BIN_LOCATION, "settings.json");
            if (json.exists())
            {
                Gson gson = new Gson();
                JsonReader jr = new JsonReader(new BufferedReader(new FileReader(json.getAbsoluteFile())));
                Settings.settings = gson.fromJson(jr, HashMap.class);
                if (Settings.settings.getClass() != HashMap.class)
                {
                    Settings.settings = new HashMap<String, String>();
                }
            }
        } catch (Exception err)
        {
            Settings.settings = new HashMap<String, String>();
        }
    }

    public static String getDefaultThreadLimit(String arg)
    {
        int defaultThreads = (Runtime.getRuntime().availableProcessors() / 2) - 1;
        if(defaultThreads < 2) defaultThreads = 2;
        return String.valueOf(defaultThreads);
    }
}
