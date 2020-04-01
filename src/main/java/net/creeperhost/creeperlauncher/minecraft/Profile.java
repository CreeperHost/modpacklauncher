package net.creeperhost.creeperlauncher.minecraft;

import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.util.GsonUtils;

public class Profile
{
    private String name;
    private String mcVersion;
    private String lastVersionId;
    private String lastUsed;
    private String type;
    private String gameDir;
    private String ID;
    private String javaArgs;
    private String icon;
    private McResolution resolution;

    public Profile(String ID, String name, String mcVersion, String lastVersionId, String lastUsed, String type, String gameDir, String icon, String args, int ram, int width, int height)
    {
        this.name = name;
        this.mcVersion = mcVersion;
        this.lastVersionId = lastVersionId;
        this.lastUsed = lastUsed;
        this.type = type;
        this.gameDir = gameDir;
        this.ID = ID;
        this.javaArgs = ("-Xmx" + ram + "M " + args).trim();
        this.icon = icon;
        this.resolution = new McResolution(width, height);
    }

    public JsonObject toJsonObject()
    {
        return GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(this), JsonObject.class);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getID()
    {
        return ID;
    }

    class McResolution
    {
        int width;
        int height;

        public McResolution(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }
}

