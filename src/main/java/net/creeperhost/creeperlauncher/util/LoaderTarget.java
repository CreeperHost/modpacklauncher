package net.creeperhost.creeperlauncher.util;

public class LoaderTarget
{
    String name;
    long id;
    String type;
    String version;

    public LoaderTarget(String name, String version, long id, String type)
    {
        this.name = name;
        this.version = version;
        this.id = id;
        this.type = type;
    }

    public String getType()
    {
        return type;
    }

    public String getName()
    {
        return name;
    }

    public long getId()
    {
        return id;
    }

    public String getVersion()
    {
        return version;
    }
}
