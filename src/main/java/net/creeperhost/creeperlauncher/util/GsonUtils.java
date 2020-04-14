package net.creeperhost.creeperlauncher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class GsonUtils
{
    public static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(Artifact.class, new Artifact.Adapter())
            .create();
}
