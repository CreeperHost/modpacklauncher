package net.creeperhost.creeperlauncher;

import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.UpdateChecker;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.OpenModalData;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.LocalCache;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.Pair;
import net.creeperhost.creeperlauncher.util.SettingsChangeUtil;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CreeperLauncher
{
    private static boolean failedInitialMigration; // todo: use this to pop up stuff if failed

    static
    {
        System.setProperty("apple.awt.UIElement", "true");
    }

    public static Process elect = null;
    public static boolean isDevMode = false;
    public static AtomicBoolean isInstalling = new AtomicBoolean(false);
    public static AtomicReference<FTBModPackInstallerTask> currentInstall = new AtomicReference<>();
    public static LocalCache localCache = null;
    public static boolean defaultWebsocketPort = false;
    public static int websocketPort = WebSocketAPI.generateRandomPort();
    public static final String websocketSecret = WebSocketAPI.generateSecret();

    private static boolean warnedDevelop = false;

    public CreeperLauncher() {}

    public static void main(String[] args)
    {
        File json = new File(Constants.BIN_LOCATION, "settings.json");
        boolean migrate = false;
        if (!json.exists())
        {
            File jsonOld = new File(Constants.BIN_LOCATION_OURS, "settings.json");

            if (jsonOld.exists()) {
                json.getParentFile().mkdirs();
                try {
                    Files.copy(jsonOld.toPath(), json.toPath());
                    Files.delete(jsonOld.toPath());
                } catch (Exception e) {
                    // shrug
                }
                migrate = true;
            }
        }

        Settings.loadSettings();

        File oldInstances = new File(Constants.WORKING_DIR, "instances");

        boolean migrateInstances = false;

        if (oldInstances.exists()) {
            if (Settings.settings.getOrDefault("instanceLocation", "").isBlank()) {
                File[] files = oldInstances.listFiles();
                migrate = migrate && files != null && files.length > 0;
                migrateInstances = migrate;
            }
        }

        deleteDirectory(Path.of(Constants.WORKING_DIR, ".localCache"));
        deleteDirectory(Path.of(Constants.OLD_CACHE_LOCATION));

        if (migrate)
        {
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher." + OSUtils.getExtension()), Path.of(Constants.MINECRAFT_LAUNCHER_LOCATION));
            move(Path.of(Constants.BIN_LOCATION_OURS, "Minecraft.app"), Path.of(Constants.BIN_LOCATION, "Minecraft.app"));
            move(Path.of(Constants.BIN_LOCATION_OURS, "minecraft-launcher"), Path.of(Constants.BIN_LOCATION, "minecraft-launcher"));
            move(Path.of(Constants.BIN_LOCATION_OURS, "versions"), Path.of(Constants.VERSIONS_FOLDER_LOC));
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_profiles.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_settings.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            move(Path.of(Constants.BIN_LOCATION_OURS, "libraries"), Path.of(Constants.LIBRARY_LOCATION));
            if (migrateInstances)
            {
                Optional<HashMap<Pair<Path, Path>, IOException>> moveResult = move(Path.of(Constants.WORKING_DIR, "instances"), Path.of(Constants.INSTANCES_FOLDER_LOC));
                if (moveResult.isPresent()) {
                    CreeperLogger.INSTANCE.error("Error occurred whilst migrating instances to the new location. Errors follow.");
                    HashMap<Pair<Path, Path>, IOException> moveResultReal = moveResult.get();
                    moveResultReal.forEach((key, value) -> {
                        CreeperLogger.INSTANCE.error("Moving " + key.getLeft() + " to " + key.getRight() + " failed:", value);
                    });
                    failedInitialMigration = true;
                }
            }
        }

        doUpdate(args);

        try {
            Files.newDirectoryStream(Paths.get("."), path -> (path.toString().endsWith(".jar") && !path.toString().contains(Constants.APPVERSION))).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {}

        Instances.refreshInstances();

        SettingsChangeUtil.registerListener("instanceLocation", (key, value) -> {
            OpenModalData.openModal("Confirmation", "Are you sure you wish to move your instances to this location? If folders exist in the destination location which match the name of folders in the current location, they will be replaced.", List.of(
                    new OpenModalData.ModalButton( "Yes", "green", () -> {
                        Path currentInstanceLoc = Path.of(Settings.settings.getOrDefault(key, Constants.INSTANCES_FOLDER_LOC));
                        File currentInstanceDir = currentInstanceLoc.toFile();
                        File[] subFiles = currentInstanceDir.listFiles();
                        Path newInstanceDir = Path.of(value);
                        boolean failed = false;
                        HashMap<Pair<Path, Path>, IOException> lastError = new HashMap<>();
                        CreeperLogger.INSTANCE.info("Moving instances from " + currentInstanceLoc + " to " + value);
                        if (subFiles != null) {
                            for (File file : subFiles) {
                                Path srcPath = Path.of(file.getAbsolutePath());
                                Path dstPath = Path.of(value, file.getName());
                                FileUtils.deleteDirectory(dstPath.toFile()); // if fails, shrug
                                Optional<HashMap<Pair<Path, Path>, IOException>> moveResult = move(srcPath, dstPath);
                                if (moveResult.isPresent()) {
                                    if (file.getName().equals(".localCache"))
                                    {
                                        continue; // I don't really care do u
                                    }
                                    failed = true;
                                    lastError = moveResult.get();
                                    break;
                                }
                                CreeperLogger.INSTANCE.info("Moved " + srcPath + " to " + dstPath + " successfully");
                            }
                        }
                        if (failed) {
                            CreeperLogger.INSTANCE.error("Error occurred whilst migrating instances to the new location. Errors follow.");
                            lastError.forEach((moveKey, moveValue) -> {
                                CreeperLogger.INSTANCE.error("Moving " + moveKey.getLeft() + " to " + moveKey.getRight() + " failed:", moveValue);
                            });
                            CreeperLogger.INSTANCE.error("Moving any successful instance moves back");
                            File[] newInstanceDirFiles = newInstanceDir.toFile().listFiles();
                            if (newInstanceDirFiles != null) {
                                for (File file : newInstanceDirFiles) {
                                    move(Path.of(file.getAbsolutePath()), currentInstanceLoc.resolve(file.getName()));
                                }
                            }
                            OpenModalData.openModal("Error", "Unable to move instances. Please ensure you have permission to create files and folders in this location.", List.of(
                                    new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                            ));
                        } else {
                            Path oldCache = Path.of(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC), ".localCache");
                            oldCache.toFile().deleteOnExit();
                            Settings.settings.remove("instanceLocation");
                            Settings.settings.put("instanceLocation", value);
                            Settings.saveSettings();
                            Instances.refreshInstances();
                            localCache = new LocalCache();
                            OpenModalData.openModal("Success", "Moved instance folder successfully", List.of(
                                    new OpenModalData.ModalButton( "Yay!", "green", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                            ));
                        }
                    }),
                    new OpenModalData.ModalButton("No", "red", () -> {})
            ));
            return false;
        });


        SettingsChangeUtil.registerListener("enablePreview", (key, value) -> {
            if (Settings.settings.getOrDefault("enablePreview", "").isEmpty() && value.equals("false")) return true;
            if (Constants.BRANCH.equals("release") || Constants.BRANCH.equals("preview"))
            {
                OpenModalData.openModal("Update", "Do you wish to change to this branch now?", List.of(
                        new OpenModalData.ModalButton( "Yes", "green", () -> {
                            doUpdate(args);
                        }),
                        new OpenModalData.ModalButton( "No", "red", () -> {
                            Settings.webSocketAPI.sendMessage(new CloseModalData());
                        })
                ));
                return true;
            } else {
                if (!warnedDevelop)
                {
                    warnedDevelop = true;
                    OpenModalData.openModal("Update", "Unable to switch from branch " + Constants.BRANCH + " via this toggle.", List.of(
                            new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                    ));
                }
                return false;
            }
        });

        Instances.refreshInstances();

        localCache = new LocalCache(); // moved to here so that it doesn't exist prior to migrating

        CompletableFuture.runAsync(() ->
        {
            localCache.clean();
        });

        boolean startProcess = !isDevMode;

        /*
        Borrowed from ModpackServerDownloader project
         */
        HashMap<String, String> Args = new HashMap<String, String>();
        String argName = null;
        for(String arg : args)
        {
            if(arg.length() > 2) {
                if (arg.startsWith("--")) {
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

        isDevMode = Args.containsKey("dev");

        if(Args.containsKey("pid") && !isDevMode)
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
            } catch (Exception exception) {
                CreeperLogger.INSTANCE.error("Error connecting to process", exception);
            }
        } else {
            CreeperLogger.INSTANCE.info("No PID args");
        }

        Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), defaultWebsocketPort || isDevMode ? Constants.WEBSOCKET_PORT : websocketPort));
        Settings.webSocketAPI.start();

        if (startProcess) {
            startElectron();
        }
    }

    private static void deleteDirectory(Path directory)
    {

        if (Files.exists(directory))
        {
            try
            {
                Files.walkFileTree(directory, new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException
                    {
                        Files.delete(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException ioException) throws IOException
                    {
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch (Exception ignored)
            {

            }
        }
    }

    private static void doUpdate(String[] args) {
        String preview = Settings.settings.getOrDefault("enablePreview", "");
        String[] updaterArgs = new String[]{};
        if (Constants.BRANCH.equals("release") && preview.equals("true"))
        {
            updaterArgs = new String[] {"-VupdatesUrl=https://apps.modpacks.ch/FTBApp/preview.xml", "-VforceUpdate=true"};
        } else if (Constants.BRANCH.equals("preview") && !preview.isEmpty() && !preview.equals("true")) {
            updaterArgs = new String[] {"-VupdatesUrl=https://apps.modpacks.ch/FTBApp/release.xml", "-VforceUpdate=true"};
        }
        //Auto update - will block, kill us and relaunch if necessary
        try
        {
            ApplicationLauncher.launchApplicationInProcess("346", updaterArgs, null, null, null);

            if (UpdateChecker.isUpdateScheduled())
            {
                UpdateChecker.executeScheduledUpdate(Arrays.asList("-q", "-splash", "\"Updating...\""), true, Arrays.asList(args), null);
            }
        } catch (Throwable ignored)
        {
        }
    }

    private static Optional<HashMap<Pair<Path, Path>, IOException>> move(Path in, Path out)
    {
            File outFile = out.toFile();
            HashMap<Pair<Path, Path>, IOException> errors = new HashMap<>();
            if (outFile.exists() && outFile.isDirectory())
            {
                File[] files = outFile.listFiles();
                if (files != null) {
                    for (File file : files) {
                        Path path = file.toPath();
                        Path destPath = out.resolve(file.getName());
                        try {

                            Files.move(path, destPath);
                        } catch (IOException e) {
                            errors.put(new Pair<>(path, destPath), e);
                        }
                    }
                }
            } else {
                try {
                    Files.move(in, out);
                } catch (IOException e) {
                    errors.put(new Pair<>(in, out), e);
                }
            }
            if (errors.isEmpty()) return Optional.empty();
            return Optional.of(errors);
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

