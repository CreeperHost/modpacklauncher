package net.creeperhost.creeperlauncher.api;

import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.IntegrityCheckException;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.http.DownloadedFile;
import net.creeperhost.creeperlauncher.install.tasks.http.IHttpClient;
import net.creeperhost.creeperlauncher.install.tasks.http.OkHttpClientImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleDownloadableFile
{
    String version;
    String path;
    long size;
    boolean clientSide;
    boolean optional;
    long id;
    String name;
    String type;
    String sha1;

    public SimpleDownloadableFile(String version, String path, long size, boolean clientSide, boolean optional, long id, String name, String type)
    {
        this.version = version;
        this.path = path;
        this.size = size;
        this.clientSide = clientSide;
        this.optional = optional;
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getVersion()
    {
        return version;
    }

    public String getPath()
    {
        return path;
    }

    public String getSha1()
    {
        return sha1;
    }

    public long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getType()
    {
        return type;
    }

    public long getSize()
    {
        return size;
    }

}
