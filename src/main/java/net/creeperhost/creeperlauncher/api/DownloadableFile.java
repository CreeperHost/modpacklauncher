package net.creeperhost.creeperlauncher.api;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.IntegrityCheckException;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.http.DownloadedFile;
import net.creeperhost.creeperlauncher.install.tasks.http.IHttpClient;
import net.creeperhost.creeperlauncher.install.tasks.http.OkHttpClientImpl;
import net.creeperhost.creeperlauncher.util.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadableFile
{
    String version;
    Path path;
    String downloadUrl;
    URL url;
    List<String> expectedChecksums;
    long size;
    boolean clientSide;
    boolean optional;
    long id;
    String name;
    String type;
    String updated;
    boolean hasPrepared;
    MessageDigest digest;
    String sha1;
    Path destination;
    private final IHttpClient client = new OkHttpClientImpl();

    public DownloadableFile(String version, Path path, String url, List<String> acceptedChecksums, long size, boolean clientSide, boolean optional, long id, String name, String type, String updated)
    {
        this.version = version;
        this.path = path;
        this.downloadUrl = url;
        this.expectedChecksums = acceptedChecksums;
        this.size = size;
        this.clientSide = clientSide;
        this.optional = optional;
        this.id = id;
        this.name = name;
        this.type = type;
        this.updated = updated;
        try
        {
            this.digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
    }

    public void prepare() throws IOException
    {
        try
        {
            this.url = new URL(this.downloadUrl.replace(" ", "%20"));
        } catch (Exception ignored)
        {
        }
        boolean remoteExists = false;
        long remoteSize = 0;
        if (this.downloadUrl.length() > 10)
        {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.connect();
            //Grab the new origin header
            int tmpContentLength = 0;
            boolean pokeOrigin = true;
            try {
                tmpContentLength = connection.getContentLength();
                if(tmpContentLength > 0)
                {
                    //We've managed to get the content length from the cdn!
                    pokeOrigin = false;
                }
            } catch (Exception ignored)
            {
                pokeOrigin = true;
            }
            if(pokeOrigin) {
                //cdn is not giving us content length due to gzip(?), let's go poke the origin of the files.
                String origin = connection.getHeaderField("origin");
                if (origin != null && origin.length() > 0) {
                    //If we have an origin header, let's grab the file size from the horses mouth
                    connection.disconnect();
                    URL _url = new URL(origin.replace(" ", "%20"));
                    connection = (HttpURLConnection) _url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(25000);
                    connection.connect();
                    tmpContentLength = connection.getContentLength();
                }
            }
            remoteSize = tmpContentLength;
            remoteExists = ((connection.getResponseCode() == 200) && (tmpContentLength >= 0));
            if(!remoteExists)
            {
                if(connection.getResponseCode() == 200)
                {
                    remoteExists = true;
                    CreeperLogger.INSTANCE.warning(this.getName() + " unable to get content length from HTTP headers!");
                    remoteSize = this.getSize();
                } else {
                    CreeperLogger.INSTANCE.warning(this.getName() + " error "+connection.getResponseCode()+": "+connection.getResponseMessage()+"!");
                }
            }
            connection.disconnect();
        }
        if (this.getSize() != remoteSize)
        {
            if (this.getSize() > 0)
            {
                FTBModPackInstallerTask.overallBytes.set(FTBModPackInstallerTask.overallBytes.get() - this.getSize());
                CreeperLogger.INSTANCE.warning(this.getName() + " size expected does not match remote file size. File size updated.");
            }
            this.size = remoteSize;
            FTBModPackInstallerTask.overallBytes.addAndGet(this.getSize());
        }
        if (!remoteExists)
        {
            throw new FileNotFoundException("File does not exist at URL.");
        }
        this.hasPrepared = true;
    }

    public void download(Path destination, boolean OverwriteOnExist, boolean FailOnExist) throws Throwable
    {
        if (!hasPrepared)
            throw new UnsupportedOperationException("Unable to download file that has not been prepared.");
        if (Files.exists(destination))
        {

            if (!OverwriteOnExist)
            {
                if (FailOnExist)
                {
                    throw new FileAlreadyExistsException("Cannot download to file which already exists.");
                } else
                {

                    CreeperLogger.INSTANCE.warning(this.getName() + " already exists.");
                }
            }
        }
        FileUtils.createDirectories(destination.getParent());
        DownloadedFile send = client.doDownload(this.downloadUrl, destination, (downloaded, delta, total, done) ->
        {
            FTBModPackInstallerTask.currentBytes.addAndGet(delta);
        }, digest, Long.parseLong(Settings.settings.putIfAbsent("speedLimit", "0")) * 1000l); // not really async - our client will run async things on same thread. bit of a hack, but async just froze.
        Path body = send.getDestination();
        sha1 = send.getChecksum();

        this.destination = body;
    }

    public Path getLocalFile()
    {
        return destination;
    }

    public void validate(boolean FailOnChecksum, boolean FailOnFileSize) throws IntegrityCheckException, FileNotFoundException
    {
        if (Files.notExists(destination)) throw new FileNotFoundException("File not saved.");
        AtomicBoolean passChecksum = new AtomicBoolean(false);

        long dstSize = 0;
        try {
            dstSize = Files.size(destination);
        } catch (IOException ignored) {
            CreeperLogger.INSTANCE.warning("Failed to get size of file: " + destination);
        }
        if ((sha1 != null && sha1.length() > 0) && (expectedChecksums != null && expectedChecksums.size() > 0))
        {

            expectedChecksums.forEach((s) ->
            {
                if (s.equalsIgnoreCase(sha1)) passChecksum.set(true);

            });
            if (!passChecksum.get())
            {
                if (FailOnChecksum)
                {
                    throw new IntegrityCheckException("SHA1 checksum does not match.", -1, sha1, expectedChecksums, dstSize, size, downloadUrl, path);
                } else
                {
                    CreeperLogger.INSTANCE.warning(this.getName() + "'s SHA1 checksum failed.");
                }
            }
        }
        if (dstSize != this.getSize())
        {
            if (FailOnFileSize)
            {
                throw new IntegrityCheckException("Downloaded file is not the same size.", -1, sha1, expectedChecksums, dstSize, getSize(), downloadUrl, path);
            } else
            {
                CreeperLogger.INSTANCE.warning(this.getName() + " size incorrect.");
            }
        }
    }


    public String getVersion()
    {
        return version;
    }

    public Path getPath()
    {
        return path;
    }

    public String getUrl()
    {
        return downloadUrl;
    }

    public String getSha1()
    {
        return sha1;
    }

    public List<String> getExpectedSha1()
    {
        return expectedChecksums;
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

    public String getUpdated()
    {
        return updated;
    }

    public long getSize()
    {
        return size;
    }

}
