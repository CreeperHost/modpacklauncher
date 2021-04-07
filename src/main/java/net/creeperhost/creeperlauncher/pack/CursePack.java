package net.creeperhost.creeperlauncher.pack;

import net.creeperhost.creeperlauncher.api.SimpleDownloadableFile;

import java.nio.file.Path;
import java.util.List;

public class CursePack extends FTBPack
{
    public CursePack(String name, String version, Path dir, List<String> authors, String description, String mcVersion, String URL, String artUrl, long id, int minMemory, int recMemory, List<SimpleDownloadableFile> files)
    {
        super(name, version, dir, authors, description, mcVersion, URL, artUrl, id, minMemory, recMemory, files);
    }
}
