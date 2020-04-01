package net.creeperhost.creeperlauncher;

import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.UpdateChecker;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.LocalCache;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CreeperLauncher
{
    static
    {
        System.setProperty("apple.awt.UIElement", "true");
    }

    public static Process elect = null;
    public static AtomicBoolean isInstalling = new AtomicBoolean(false);
    public static AtomicReference<FTBModPackInstallerTask> currentInstall = new AtomicReference<>();
    public static LocalCache localCache = new LocalCache();

    public CreeperLauncher() {}

    public static void main(String[] args)
    {
        //Auto update - will block, kill us and relaunch if necessary
        try
        {
            ApplicationLauncher.launchApplicationInProcess("346", null, null, null, null);

            if (UpdateChecker.isUpdateScheduled())
            {
                UpdateChecker.executeScheduledUpdate(Arrays.asList("-q", "-splash", "\"Updating...\""), true, null);
            }
        } catch (Throwable ignored)
        {
        }

        try {
            Files.newDirectoryStream(Paths.get("."), path -> (path.toString().endsWith(".jar") && !path.toString().contains(Constants.APPVERSION))).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {}

        Instances.refreshInstances();
        CompletableFuture.runAsync(() ->
        {
            localCache.clean();
        });
        Settings.loadSettings();
        Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), Constants.WEBSOCKET_PORT));
        Settings.webSocketAPI.start();
        File electron = null;
        OS os = OSUtils.getOs();
        ProcessBuilder app = null;
        switch (os)
        {
            case MAC:
                electron = new File(Constants.BIN_LOCATION, "ftbapp.app");
                app = new ProcessBuilder(electron.getAbsolutePath() + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "ftbapp");
                break;
            case LINUX:
                electron = new File(Constants.BIN_LOCATION, "ftb-app");
                FileUtils.setFilePermissions(electron);

                app = new ProcessBuilder(electron.getAbsolutePath());

                try {
                    if (Files.exists(Path.of("/proc/sys/kernel/unprivileged_userns_clone")) && new String(Files.readAllBytes(Path.of("/proc/sys/kernel/unprivileged_userns_clone"))).equals("0"))
                    {
                        app = new ProcessBuilder(electron.getAbsolutePath(), "--no-sandbox");
                    }
                } catch (IOException ignored) {}
                break;
            default:
                electron = new File(Constants.BIN_LOCATION, "ftbapp.exe");
                app = new ProcessBuilder(electron.getAbsolutePath());
        }
        if (electron.exists())
        {
            try
            {
                elect = app.start();
                new StreamGobblerLog(elect.getErrorStream(), CreeperLogger.INSTANCE::error);
                new StreamGobblerLog(elect.getInputStream(), CreeperLogger.INSTANCE::info);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            CompletableFuture<Process> completableFuture = elect.onExit();
            completableFuture.thenRun(() ->
            {
                try
                {
                    Settings.webSocketAPI.stop();
                } catch (IOException | InterruptedException e)
                {
                    e.printStackTrace();
                }
                Settings.saveSettings();
                System.exit(0);
            });
            Runtime.getRuntime().addShutdownHook(new Thread(elect::destroy));
        }
    }
}

