package net.creeperhost.creeperlauncher.install.tasks;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.util.MiscUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalCache
{
    private List<UUID> files = new ArrayList<UUID>();

    public LocalCache()
    {
        File cache = new File(Constants.CACHE_LOCATION);
        if (!cache.exists()) cache.mkdirs();
        File[] cached = cache.listFiles();
        if (cached != null)
        {
            for (File f : cached)
            {
                try
                {
                    files.add(UUID.fromString(f.getName()));
                } catch (Exception ignored)
                {
                }
            }
        }
    }

    public boolean exists(String sha1Hash)
    {
        if (sha1Hash == null) return false;
        return files.contains(UUID.nameUUIDFromBytes(sha1Hash.getBytes()));
    }

    public boolean exists(UUID uuid)
    {
        return files.contains(uuid);
    }

    public File get(String sha1Hash)
    {
        return new File(Constants.CACHE_LOCATION + File.separator + UUID.nameUUIDFromBytes(sha1Hash.getBytes()).toString());
    }

    public boolean put(File f, String sha1Hash)
    {
        if (!f.exists()) return false;
        if (sha1Hash == null) return false;
        UUID uuid = UUID.nameUUIDFromBytes(sha1Hash.getBytes());
        if (exists(uuid)) return false;
        if (get(sha1Hash).exists()) return false;
        try
        {
            Files.copy(f.toPath(), get(sha1Hash).toPath());
        } catch (IOException err)
        {
            CreeperLogger.INSTANCE.error("Failed to add '" + f.getAbsolutePath() + "' to local cache.");
            return false;
        }
        files.add(uuid);
        return true;
    }

    public void clean()
    {
        File file = new File(Constants.CACHE_LOCATION);
        File[] cached = file.listFiles();
        Long cacheLife = Long.valueOf(Settings.settings.getOrDefault("cacheLife", "5184000"));
        if (cacheLife < 0) cacheLife = 900l;
        if (cached != null)
        {
            for (File f : cached)
            {
                BasicFileAttributes attrs = null;
                try
                {
                    attrs = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
                } catch (IOException e)
                {
                    CreeperLogger.INSTANCE.warning("Unable to remove local cache file '" + f.getName() + "': " + e.getMessage());
                }
                FileTime time = attrs.lastAccessTime();
                if (time == null) time = attrs.creationTime();
                Long fileAge = MiscUtils.unixtime() - (time.toMillis() / 1000);
                UUID uuid;
                try
                {
                    uuid = UUID.fromString(f.getName());
                } catch (Exception err)
                {
                    if (f.exists())
                    {
                        f.delete();
                        CreeperLogger.INSTANCE.warning("Deleting invalid file '" + f.getName() + "' which is " + fileAge + " seconds old.");
                    }
                    continue;
                }
                if (fileAge > cacheLife)
                {
                    if (files.contains(uuid) && f.exists())
                    {
                        files.remove(uuid);
                        f.delete();
                    }
                }
            }
        }
    }
}
