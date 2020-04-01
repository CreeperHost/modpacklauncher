package net.creeperhost.creeperlauncher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonUtils
{
    public static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(Artifact.class, new Artifact.Adapter())
            .create();
}
