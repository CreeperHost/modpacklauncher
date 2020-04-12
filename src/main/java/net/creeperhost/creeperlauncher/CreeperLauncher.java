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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
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
    public static boolean defaultWebsocketPort = false;
    public static int websocketPort = WebSocketAPI.generateRandomPort();
    public static String websocketSecret = WebSocketAPI.generateSecret();

    public CreeperLauncher() {}

    public static void main(String[] args)
    {
        //Auto update - will block, kill us and relaunch if necessary
        try
        {
            ApplicationLauncher.launchApplicationInProcess("346", null, null, null, null);

            if (UpdateChecker.isUpdateScheduled())
            {
                UpdateChecker.executeScheduledUpdate(Arrays.asList("-q", "-splash", "\"Updating...\""), true, Arrays.asList(args), null);
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

        boolean startProcess = true;

        if (args.length >= 3)
        {
            if(args[1].equals("--pid")) {
                try {
                    long pid = Long.parseLong(args[2]);
                    Optional<ProcessHandle> electronProc = ProcessHandle.of(pid);
                    if (electronProc.isPresent())
                    {
                        startProcess = false;
                        defaultWebsocketPort = true;
                        ProcessHandle handle = electronProc.get();
                        handle.onExit().thenRun(CreeperLauncher::exit);
                        Runtime.getRuntime().addShutdownHook(new Thread(handle::destroy));
                    }
                } catch (Exception ignored) {
                    CreeperLogger.INSTANCE.error("Error connecting to process", ignored)
                    CreeperLogger.INSTANCE.info("Arguments:", args);
                }
            }
        } else {
            CreeperLogger.INSTANCE.info("No PID args");
            CreeperLogger.INSTANCE.info("Arguments:", args);
        }

        Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), defaultWebsocketPort ? Constants.WEBSOCKET_PORT : websocketPort));
        Settings.webSocketAPI.start();

        if (startProcess) {
            startElectron();
        }
    }

    private static void startElectron() {
        File electron;
        OS os = OSUtils.getOs();

        ArrayList<String> args = new ArrayList<>();


        switch (os)
        {
            case MAC:
                electron = new File(Constants.BIN_LOCATION, "ftbapp.app");
                args.add(0, electron.getAbsolutePath() + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "ftbapp");
                break;
            case LINUX:
                electron = new File(Constants.BIN_LOCATION, "ftb-app");
                FileUtils.setFilePermissions(electron);

                args.add(0, electron.getAbsolutePath());

                try {
                    if (Files.exists(Path.of("/proc/sys/kernel/unprivileged_userns_clone")) && new String(Files.readAllBytes(Path.of("/proc/sys/kernel/unprivileged_userns_clone"))).equals("0"))
                    {
                        args.add(1,  "--no-sandbox");
                    }
                } catch (IOException ignored) {}
                break;
            default:
                electron = new File(Constants.BIN_LOCATION, "ftbapp.exe");
                args.add(0, electron.getAbsolutePath());
        }

        args.add("--ws");
        args.add(websocketPort + ":" + websocketSecret);
        args.add("--pid");
        args.add(String.valueOf(ProcessHandle.current().pid()));

        ProcessBuilder app = new ProcessBuilder(args);

        if (electron.exists())
        {
            try
            {
                CreeperLogger.INSTANCE.info("Starting Electron: " + String.join(" ", args));
                elect = app.start();
                new StreamGobblerLog(elect.getErrorStream(), CreeperLogger.INSTANCE::error);
                new StreamGobblerLog(elect.getInputStream(), CreeperLogger.INSTANCE::info);
            } catch (IOException e)
            {
                CreeperLogger.INSTANCE.error("Error starting Electron: ", e);
            }
            CompletableFuture<Process> completableFuture = elect.onExit();
            completableFuture.thenRun(CreeperLauncher::exit);
            Runtime.getRuntime().addShutdownHook(new Thread(elect::destroy));
        }
    }

    private static void exit() {
        try
        {
            Settings.webSocketAPI.stop();
        } catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
        Settings.saveSettings();
        System.exit(0);
    }
}

