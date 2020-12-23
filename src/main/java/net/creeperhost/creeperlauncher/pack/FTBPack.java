package net.creeperhost.creeperlauncher.pack;

import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.SimpleDownloadableFile;

import java.nio.file.Path;
import java.util.List;

public class FTBPack implements IPack
{
    private final String name;
    private final String version;
    private final Path dir;
    private final List<String> authors;
    private final String description;
    private final String mcVersion;
    private final String url;
    private final String artUrl;
    private final int minMemory;
    private final int recMemory;
    private final long id;
    private final List<SimpleDownloadableFile> mods;

    public FTBPack(String name, String version, Path dir, List<String> authors, String description, String mcVersion, String URL, String artUrl, long id, int minMemory, int recMemory, List<SimpleDownloadableFile> mods)
    {
        this.name = name;
        this.version = version;
        this.dir = dir;
        this.authors = authors;
        this.description = description;
        this.mcVersion = mcVersion;
        this.url = URL;
        this.artUrl = artUrl;
        this.id = id;
        this.minMemory = minMemory;
        this.recMemory = recMemory;
        this.mods = mods;
    }

    @Override
    public long getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getVersion()
    {
        return version;
    }

    @Override
    public Path getDir()
    {
        return dir;
    }

    @Override
    public List<String> getAuthors()
    {
        return authors;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String getMcVersion()
    {
        return mcVersion;
    }

    @Override
    public String getUrl()
    {
        return url;
    }

    @Override
    public String getArtURL()
    {
        return artUrl;
    }

    @Override
    public int getMinMemory()
    {
        return minMemory;
    }

    @Override
    public int getRecMemory()
    {
        return recMemory;
    }

    public List<SimpleDownloadableFile> getMods() {
        return mods;
    }
}
