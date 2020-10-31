package net.creeperhost.creeperlauncher;

import com.google.gson.JsonObject;
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.UpdateChecker;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.other.ClientLaunchData;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.LocalCache;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CreeperLauncher
{
    public static HashMap<String, String> javaVersions;
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
    public static AtomicBoolean isSyncing = new AtomicBoolean(false);
    public static AtomicReference<List<Process>> mojangProcesses = new AtomicReference<List<Process>>();

    private static boolean warnedDevelop = false;

    public static boolean verbose = false;

    public CreeperLauncher() {}

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args)
    {
        File json = new File(Constants.BIN_LOCATION, "settings.json");
        boolean migrate = false;
        if (!json.exists())
        {
            try {
                Files.createDirectories(Path.of(Constants.BIN_LOCATION));
            } catch (IOException ignored) {
                // shrug
            }
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

        verbose = Settings.settings.getOrDefault("verbose", "false").equals("true");

        File oldInstances = new File(Constants.WORKING_DIR, "instances");

        boolean migrateInstances = false;

        if (oldInstances.exists()) {
            if (Settings.settings.getOrDefault("instanceLocation", "").isBlank()) {
                File[] files = oldInstances.listFiles();
                migrate = migrate && files != null && files.length > 0;
                migrateInstances = migrate;
            }
        }

        FileUtils.deleteDirectory(Path.of(Constants.WORKING_DIR, ".localCache"));
        FileUtils.deleteDirectory(Path.of(Constants.OLD_CACHE_LOCATION));

        if (migrate)
        {
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher." + OSUtils.getExtension()), Path.of(Constants.MINECRAFT_LAUNCHER_LOCATION));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "Minecraft.app"), Path.of(Constants.BIN_LOCATION, "Minecraft.app"));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "minecraft-launcher"), Path.of(Constants.BIN_LOCATION, "minecraft-launcher"));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "versions"), Path.of(Constants.VERSIONS_FOLDER_LOC));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_profiles.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_settings.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "libraries"), Path.of(Constants.LIBRARY_LOCATION));
            if (migrateInstances)
            {
                HashMap<Pair<Path, Path>, IOException> moveResult = FileUtils.move(Path.of(Constants.WORKING_DIR, "instanceLocation"), Path.of(Constants.INSTANCES_FOLDER_LOC));
                if (!moveResult.isEmpty()) {
                    CreeperLogger.INSTANCE.error("Error occurred whilst migrating instances to the new location. Errors follow.");
                    moveResult.forEach((key, value) -> {
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
            OpenModalData.openModal("Confirmation", "Are you sure you wish to move your instances to this location? <br tag='haha line break go brr'> All content in your current instance location will be moved, and if content exists with the same name in the destination it will be replaced.", List.of(
                    new OpenModalData.ModalButton( "Yes", "green", () -> {
                        OpenModalData.openModal("Please wait", "Your instances are now moving", List.of());
                        Path currentInstanceLoc = Path.of(Settings.settings.getOrDefault(key, Constants.INSTANCES_FOLDER_LOC));
                        File currentInstanceDir = currentInstanceLoc.toFile();
                        File[] subFiles = currentInstanceDir.listFiles();
                        Path newInstanceDir = Path.of(value);
                        boolean failed = false;
                        HashMap<Pair<Path, Path>, IOException> lastError = new HashMap<>();
                        CreeperLogger.INSTANCE.info("Moving instances from " + currentInstanceLoc + " to " + value);
                        if (subFiles != null) {
                            for (File file : subFiles) {
                                String fileName = file.getName();
                                if(fileName.length() == 36) {
                                    try {
                                        UUID.fromString(fileName);
                                    } catch (Throwable ignored) {
                                        continue;
                                    }
                                } else if (!fileName.equals(".localCache")) {
                                    continue;
                                }
                                Path srcPath = Path.of(file.getAbsolutePath());
                                Path dstPath = Path.of(value, file.getName());
                                lastError = FileUtils.move(srcPath, dstPath, true, true);
                                failed = !lastError.isEmpty() && !srcPath.toFile().getName().equals(".localCache");
                                if (failed) break;
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
                                    FileUtils.move(Path.of(file.getAbsolutePath()), currentInstanceLoc.resolve(file.getName()));
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
                            OpenModalData.openModal("Success", "Moved instance folder location successfully", List.of(
                                    new OpenModalData.ModalButton( "Yay!", "green", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                            ));
                        }
                    }),
                    new OpenModalData.ModalButton("No", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
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

        SettingsChangeUtil.registerListener("verbose", (key, value) -> {
            verbose = value.equals("true");
            return true;
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
                    handle.onExit().thenRun(() ->
                    {
                        while (isSyncing.get()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) { e.printStackTrace(); }
                        }
                        CreeperLauncher.exit();
                    });
                    Runtime.getRuntime().addShutdownHook(new Thread(handle::destroy));
                }
            } catch (Exception exception) {
                CreeperLogger.INSTANCE.error("Error connecting to process", exception);
            }
        } else {
            CreeperLogger.INSTANCE.info("No PID args");
        }

        if(isDevMode){
            startProcess = false;
        }

        Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), defaultWebsocketPort || isDevMode ? Constants.WEBSOCKET_PORT : websocketPort));
        Settings.webSocketAPI.setConnectionLostTimeout(0);
        Settings.webSocketAPI.start();

        if (startProcess) {
            startElectron();
        }
        File dataDirectory = new File(Constants.DATA_DIR);
        if(!dataDirectory.canWrite())
        {
            OpenModalData.openModal("Critical Error", "The FTBApp is unable to write to your selected data directory, this can be caused by file permission errors, anti-virus or any number of other configuration issues.<br />If you continue, the app will not work as intended and you may be unable to install or run any modpacks.", List.of(
                    new OpenModalData.ModalButton( "Exit", "green", CreeperLauncher::exit),
                    new OpenModalData.ModalButton("Continue", "", () -> {
                        Settings.webSocketAPI.sendMessage(new CloseModalData());
                    }))
            );
        }
        MiscUtils.updateJavaVersions();
    }

    @SuppressWarnings("ConstantConditions")
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
    public static long unixtimestamp()
    {
        return System.currentTimeMillis() / 1000L;
    }
    public static Socket listenForClient(int port)
    {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            Socket socket = serverSocket.accept();
            CompletableFuture.runAsync(() -> {
                String lastInstance = "";
                ClientLaunchData.Reply reply;
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    long lastMessageTime = 0;
                    while (socket.isConnected()) {
                        String bufferText = "";
                        try {
                            bufferText = in.readLine();
                            if (bufferText.length() == 0) continue;
                            JsonObject object = GsonUtils.GSON.fromJson(bufferText, JsonObject.class);
                            Object data = new Object();
                            boolean hasStarted = (object.has("message") && object.get("message").getAsString().equals("init"));
                            if(hasStarted) {
                                if (object.has("data")) {
                                    data = object.get("data");
                                }
                                if (object.get("instance").getAsString() != null && object.get("instance").getAsString().length() > 0) {
                                    lastInstance = object.get("instance").getAsString();
                                }
                                boolean isDone = (object.has("message") && object.get("message").getAsString().equals("done"));
                                if (System.currentTimeMillis() > (lastMessageTime + 200) || isDone) {
                                    reply = new ClientLaunchData.Reply(object.get("instance").getAsString(), object.get("type").getAsString(), data);
                                    lastMessageTime = System.currentTimeMillis();
                                    Settings.webSocketAPI.sendMessage(reply);
                                }
                                if (isDone) {
                                    socket.close();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            CreeperLogger.INSTANCE.error("Error whilst sending message on to websocket", e);
                            socket.close();
                            break;
                        }
                    }
                    if(socket.isConnected()){
                        socket.close();
                    }
                    if(serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
                } catch (Throwable e) {
                }
                if(lastInstance.length() > 0) {
                    reply = new ClientLaunchData.Reply(lastInstance, "clientDisconnect", new Object());
                    Settings.webSocketAPI.sendMessage(reply);
                }
            });
            return socket;
        } catch (Throwable e)
        {
            CreeperLogger.INSTANCE.error("Error whilst sending message on to websocket", e);
        }
        return null;
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
                StreamGobblerLog.redirectToLogger(elect.getErrorStream(), CreeperLogger.INSTANCE::error);
                StreamGobblerLog.redirectToLogger(elect.getInputStream(), CreeperLogger.INSTANCE::info);
            } catch (IOException e)
            {
                CreeperLogger.INSTANCE.error("Error starting Electron: ", e);
            }
            CompletableFuture<Process> completableFuture = elect.onExit();
            completableFuture.thenRun(CreeperLauncher::exit);
            Runtime.getRuntime().addShutdownHook(new Thread(elect::destroy));
        }
    }

    public static void exit() {
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

