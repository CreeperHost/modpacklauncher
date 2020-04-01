package net.creeperhost.creeperlauncher.pack;

import java.util.List;

public interface IPack
{
    long getId();

    String getName();

    String getVersion();

    default String getDir()
    {
        return getName();
    }

    List<String> getAuthors();

    String getDescription();

    String getMcVersion();

    String getUrl();

    String getArtURL();

    int getMinMemory();

    int getRecMemory();
}
