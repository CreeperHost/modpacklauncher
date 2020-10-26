package net.creeperhost.creeperlauncher.install.tasks;

import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.IntegrityCheckException;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.InstalledFileEventData;
import net.creeperhost.creeperlauncher.api.handlers.InstallInstanceHandler;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public class DownloadTask implements IInstallTask
{
    private final Path destination;
    private boolean canChecksum = false;
    private boolean checksumComplete;
    private String sha1;
    static int nThreads = Integer.parseInt(Settings.settings.computeIfAbsent("threadLimit", Settings::getDefaultThreadLimit));
    public static final Executor threadPool = new ThreadPoolExecutor(nThreads, nThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private int tries = 0;
    private final DownloadableFile file;

    public DownloadTask(DownloadableFile file, Path destination)
    {
        this.file = file;
        this.destination = destination;
    }

    @Override
    public CompletableFuture<Void> execute()
    {
        return CompletableFuture.runAsync(() ->
        {
            boolean complete = false;
            if(file.getType().equalsIgnoreCase("mod"))
                Settings.webSocketAPI.sendMessage(new InstalledFileEventData.Reply(file.getName(), "preparing"));
            while (!complete && tries < 3)
            {
                try
                {
                    ++tries;
                    file.prepare();
                    complete = true;
                } catch (SocketTimeoutException err)
                {
                    if (tries == 3)
                    {
                        throw new IntegrityCheckException(err.getMessage(), -2, "", null, 0, 0, file.getUrl(), destination.toString());
                    }
                } catch (IOException e)
                {
                    CreeperLogger.INSTANCE.error("Unable to download " + file.getName());
                    return;
                }
            }
            complete = false;
            tries = 0;
            while (!complete && tries < 3)
            {
                if (tries == 0)
                {
                    for (String checksum : file.getExpectedSha1())
                    {
                        if (CreeperLauncher.localCache.exists(checksum))
                        {
                            if (checksum != null)
                            {
                                File cachedFile = CreeperLauncher.localCache.get(checksum);
                                if (destination.toFile().exists()) break;
                                try
                                {
                                    destination.toFile().getParentFile().mkdirs();
                                    Files.copy(cachedFile.toPath(), destination);
                                    FTBModPackInstallerTask.currentBytes.addAndGet(cachedFile.length());
                                    if(file.getType().equalsIgnoreCase("mod"))
                                        Settings.webSocketAPI.sendMessage(new InstalledFileEventData.Reply(file.getName(), "downloaded"));
                                    complete = true;
                                    break;
                                } catch (IOException ignored)
                                {
                                }
                            }
                        }
                    }
                }
                if (!complete)
                {
                    try
                    {
                        ++tries;
                        file.download(destination, false, false);
                        file.validate(true, true);
                        try
                        {
                            CreeperLauncher.localCache.put(file.getLocalFile(), file.getSha1());
                        } catch (Exception err)
                        {
                            CreeperLogger.INSTANCE.error("Error whilst adding to cache: ", err);
                        }
                        if(file.getType().equalsIgnoreCase("mod"))
                            Settings.webSocketAPI.sendMessage(new InstalledFileEventData.Reply(file.getName(), "downloaded"));
                        complete = true;
                    } catch (Throwable e)
                    {
                        if (tries == 3)
                        {
                            IntegrityCheckException thrown;
                            if (e instanceof IntegrityCheckException)
                            {
                                CreeperLogger.INSTANCE.debug("Integrity error whilst getting file: ", e);
                                thrown = (IntegrityCheckException)e;
                            } else
                            {
                                CreeperLogger.INSTANCE.debug("Unknown error whilst getting file: ", thrown = new IntegrityCheckException(e, -1, "", null, 0, 0, file.getUrl(), destination.toString())); // TODO: make this better
                            }
                            if(Settings.settings.getOrDefault("unforgiving", "false").equals("true"))
                            {
                                throw thrown;
                            }
                        }
                    }
                }
            }
        }, threadPool);
    }

    @Override
    public Double getProgress()
    {
        if (FTBModPackInstallerTask.currentBytes.get() == 0 || FTBModPackInstallerTask.overallBytes.get() == 0)
            return 0.00d;
        return ((FTBModPackInstallerTask.currentBytes.get() / FTBModPackInstallerTask.overallBytes.get()) * 100d);
    }
}
