package net.creeperhost.creeperlauncher.install.tasks;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.MiscUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class LocalCache
{
    //TODO, Using name UUID's here is not unique and can cause collisions as the
    // SHA1 passed in gets converted to an MD5 then represented as a UUID.
    // This should be switched to at least Map<HashCode, Path>
    private final Map<UUID, Path> files = new HashMap<>();
    private final Path cacheLocation = Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC).resolve(".localCache");

    public LocalCache()
    {
        FileUtils.createDirectories(cacheLocation);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheLocation)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    String name = file.getFileName().toString();
                    try {
                        files.put(UUID.fromString(name), file);
                    } catch (Exception e) {
                        CreeperLogger.INSTANCE.warning("Deleting invalid file from cache directory: " + file);
                        try {
                            Files.delete(file);
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public boolean exists(String sha1Hash)
    {
        if (sha1Hash == null) return false;
        return exists(UUID.nameUUIDFromBytes(sha1Hash.getBytes()));
    }

    public boolean exists(UUID uuid)
    {
        return files.containsKey(uuid);
    }

    public Path get(String sha1Hash)
    {
        return files.get(UUID.nameUUIDFromBytes(sha1Hash.getBytes()));
    }

    private Path pathOf(String hash) {
        return cacheLocation.resolve(UUID.nameUUIDFromBytes(hash.getBytes()).toString());
    }

    public boolean put(Path f, String sha1Hash)
    {
        if (Files.notExists(f) || sha1Hash == null) return false;
        UUID uuid = UUID.nameUUIDFromBytes(sha1Hash.getBytes());
        if (exists(uuid)) return false;
        Path cachePath = pathOf(sha1Hash);
        if (Files.exists(cachePath)) return false;
        try
        {
            Files.copy(f, cachePath);
            files.put(uuid, cachePath);
        } catch (IOException err)
        {
            CreeperLogger.INSTANCE.error("Failed to add '" + f.toAbsolutePath() + "' to local cache.");
            return false;
        }
        return true;
    }

    public void clean()
    {
        long cacheLife = Long.parseLong(Settings.settings.getOrDefault("cacheLife", "5184000"));
        if (cacheLife < 0) cacheLife = 900L;
        for (Map.Entry<UUID, Path> entry : files.entrySet())
        {
            UUID uuid = entry.getKey();
            Path file = entry.getValue();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            BasicFileAttributes attrs;
            try
            {
                attrs = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException e)
            {
                CreeperLogger.INSTANCE.warning("Unable to remove local cache file '" + file.toAbsolutePath() + "': " + e.getMessage());
                continue;
            }
            FileTime time = attrs.lastAccessTime();
            if (time == null) time = attrs.creationTime();
            long fileAge = MiscUtils.unixtime() - (time.toMillis() / 1000);
            if (fileAge > cacheLife)
            {
                if (Files.exists(file))
                {
                    files.remove(uuid);
                    try {
                        Files.delete(file);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
