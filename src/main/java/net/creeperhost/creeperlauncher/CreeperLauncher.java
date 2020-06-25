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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    public static LocalCache localCache = null;
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

        if (!Settings.settings.getOrDefault("migrate", "").isEmpty())
        {
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher." + OSUtils.getExtension()), Path.of(Constants.MINECRAFT_LAUNCHER_LOCATION));
            move(Path.of(Constants.BIN_LOCATION_OURS, "Minecraft.app"), Path.of(Constants.BIN_LOCATION, "Minecraft.app"));
            move(Path.of(Constants.BIN_LOCATION_OURS, "minecraft-launcher"), Path.of(Constants.BIN_LOCATION, "minecraft-launcher"));
            move(Path.of(Constants.BIN_LOCATION_OURS, "versions"), Path.of(Constants.VERSIONS_FOLDER_LOC));
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_profiles.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_settings.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            move(Path.of(Constants.BIN_LOCATION_OURS, "libraries"), Path.of(Constants.LIBRARY_LOCATION));
            move(Path.of(Constants.WORKING_DIR, ".localCache"), Path.of(Constants.CACHE_LOCATION));
            if (!move(Path.of(Constants.WORKING_DIR, "instances"), Path.of(Constants.INSTANCES_FOLDER_LOC))) {
                // Failed migration, not sure how to handle this right now
            }
        }

        localCache = new LocalCache(); // moved to here so that it doesn't exist prior to migrating

        boolean startProcess = true;

        /*
        Borrowed from ModpackServerDownloader project
         */
        HashMap<String, String> Args = new HashMap<String, String>();
        String argName = null;
        for(String arg : args)
        {
            if(arg.length() > 2) {
                if (arg.substring(0, 2).equals("--")) {
                    argName = arg.substring(2);
                    Args.put(argName, "");
                }
                if (argName != null) {
                    if (argName.length() > 2) {
                        if (!argName.equals(arg.substring(2))) {
                            if (Args.containsKey(argName)) {
                                Args.remove(argName);
                            }
                            Args.put(argName, arg);
                            argName = null;
                        }
                    }
                }
            }
        }
        /*
        End
         */

        if(Args.containsKey("pid"))
        {
            try {
                long pid = Long.parseLong(Args.get("pid"));
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
                CreeperLogger.INSTANCE.error("Error connecting to process", ignored);
            }
        } else {
            CreeperLogger.INSTANCE.info("No PID args");
        }

        Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), defaultWebsocketPort ? Constants.WEBSOCKET_PORT : websocketPort));
        Settings.webSocketAPI.start();

        if (startProcess) {
            startElectron();
        }
    }

    private static boolean move(Path in, Path out)
    {
        try {
            Files.move(in, out, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (Exception e) {
            System.out.println("Unable to move " + in);
            e.printStackTrace();
            return false;
        }
    }

    private static void startElectron() {
        File electron;
        OS os = OSUtils.getOs();

        ArrayList<String> args = new ArrayList<>();


        switch (os)
        {
            case MAC:
                electron = new File(Constants.BIN_LOCATION_OURS, "ftbapp.app");
                args.add(0, electron.getAbsolutePath() + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "ftbapp");
                break;
            case LINUX:
                electron = new File(Constants.BIN_LOCATION_OURS, "ftb-app");
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
                electron = new File(Constants.BIN_LOCATION_OURS, "ftbapp.exe");
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

