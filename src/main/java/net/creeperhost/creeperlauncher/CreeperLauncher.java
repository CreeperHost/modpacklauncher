package net.creeperhost.creeperlauncher;

import com.google.gson.JsonObject;
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.UpdateChecker;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.other.ClientLaunchData;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.api.data.other.PingLauncherData;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.install.tasks.LocalCache;
import net.creeperhost.creeperlauncher.minetogether.vpn.MineTogetherConnect;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.*;

import java.io.*;
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
    public static int missedPings = 0;
    private static boolean failedInitialMigration; // todo: use this to pop up stuff if failed
    public static ServerSocket serverSocket = null;
    public static Socket socket = null;
    public static OutputStream socketWrite = null;
    public static boolean opened = false;

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
    public static boolean websocketDisconnect=false;
    public static AtomicBoolean isSyncing = new AtomicBoolean(false);
    public static AtomicReference<List<Process>> mojangProcesses = new AtomicReference<List<Process>>();
    public static MineTogetherConnect mtConnect;

    private static boolean warnedDevelop = false;

    public static boolean verbose = false;

    public CreeperLauncher() {}

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args)
    {
        Path json = Constants.BIN_LOCATION.resolve("settings.json");
        boolean migrate = false;
        if (Files.notExists(json))
        {
            Path jsonOld = Constants.getDataDirOld().resolve("bin/settings.json");

            if (Files.exists(jsonOld)) {
//                json.getParentFile().mkdirs();
                try {
//                    Files.copy(jsonOld.toPath(), json.toPath());
//                    Files.delete(jsonOld.toPath());
                } catch (Exception e) {
                    // shrug
                }
                migrate = true;
            }
        }

        if (migrate)
        {
            Settings.loadSettings(Constants.getDataDirOld().resolve("bin/settings.json"), false);
        } else {
            Settings.loadSettings();
        }

        verbose = Settings.settings.getOrDefault("verbose", "false").equals("true");

        Path oldInstances = Constants.getDataDirOld().resolve("instances");

        boolean migrateInstances = false;

        if (Files.exists(oldInstances)) {
            String oldInstanceLocation = Settings.settings.getOrDefault("instanceLocation", "");
            if (oldInstanceLocation.equals(oldInstances.toAbsolutePath().toString())) {
                migrateInstances = true;
            }
        }

        FileUtils.deleteDirectory(Constants.WORKING_DIR.resolve(".localCache"));
        FileUtils.deleteDirectory(Constants.OLD_CACHE_LOCATION);

        boolean migrateError = false;

        if (migrate)
        {
            if (migrateInstances)
            {
                // try delete cache as faster move
                FileUtils.deleteDirectory(Constants.getDataDirOld().resolve("instances/.localCache"));
            }
/*            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher." + OSUtils.getExtension()), Path.of(Constants.MINECRAFT_LAUNCHER_LOCATION));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "Minecraft.app"), Path.of(Constants.BIN_LOCATION, "Minecraft.app"));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "minecraft-launcher"), Path.of(Constants.BIN_LOCATION, "minecraft-launcher"));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "versions"), Path.of(Constants.VERSIONS_FOLDER_LOC));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_profiles.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "launcher_settings.json"), Path.of(Constants.LAUNCHER_PROFILES_JSON));
            FileUtils.move(Path.of(Constants.BIN_LOCATION_OURS, "libraries"), Path.of(Constants.LIBRARY_LOCATION));*/
            CreeperLogger.INSTANCE.close(); // close so we can move the existing logs and everything
            HashMap<Pair<Path, Path>, IOException> move = FileUtils.move(Constants.getDataDirOld(), Constants.getDataDir(), false, false);
            CreeperLogger.INSTANCE.reinitialise(); // try re-open logger
            if (move.size() > 0)
            {
                migrateError = true;
                StringBuilder errorString = new StringBuilder("Errors occurred whilst migrating to a new folder structure. It may still be fine, but please contact FTB support if you have issues!\n");
                for(Map.Entry<Pair<Path, Path>, IOException> entry : move.entrySet()) {
                    errorString.append(entry.getKey().getLeft()).append(" ").append(entry.getKey().getRight()).append(":").append(entry.getValue().getMessage()).append("\n");
                }
                CreeperLogger.INSTANCE.warning(errorString.toString());
            }
            if (migrateInstances)
            {
                /*HashMap<Pair<Path, Path>, IOException> moveResult = FileUtils.move(Path.of(Constants.WORKING_DIR, "instanceLocation"), Path.of(Constants.INSTANCES_FOLDER_LOC));
                if (!moveResult.isEmpty()) {
                    CreeperLogger.INSTANCE.error("Error occurred whilst migrating instances to the new location. Errors follow.");
                    moveResult.forEach((key, value) -> {
                        CreeperLogger.INSTANCE.error("Moving " + key.getLeft() + " to " + key.getRight() + " failed:", value);
                    });
                    failedInitialMigration = true;
                }*/
                Settings.settings.remove("instanceLocation");
                Settings.settings.put("instanceLocation", Constants.INSTANCES_FOLDER_LOC.toAbsolutePath().toString());
            }
            Settings.saveSettings();
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
                        Path currentInstanceLoc = Path.of(Settings.settings.getOrDefault(key, Constants.INSTANCES_FOLDER_LOC.toAbsolutePath().toString()));
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
                            Path oldCache = Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC).resolve(".localCache");
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



        /*
        Borrowed from ModpackServerDownloader project
         */
        HashMap<String, String> Args = new HashMap<>();
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

        boolean startProcess = !isDevMode;

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

        try {
            Settings.webSocketAPI = new WebSocketAPI(new InetSocketAddress(InetAddress.getLoopbackAddress(), defaultWebsocketPort || isDevMode ? Constants.WEBSOCKET_PORT : websocketPort));
            Settings.webSocketAPI.setConnectionLostTimeout(0);
            Settings.webSocketAPI.start();
            pingPong();
        } catch(Throwable t)
        {
            websocketDisconnect=true;
            CreeperLogger.INSTANCE.error("Unable to open websocket port or websocket has disconnected...", t);
        }

        if (startProcess) {
            startElectron();
        }
        if(!Files.isWritable(Constants.getDataDir()))
        {
            OpenModalData.openModal("Critical Error", "The FTBApp is unable to write to your selected data directory, this can be caused by file permission errors, anti-virus or any number of other configuration issues.<br />If you continue, the app will not work as intended and you may be unable to install or run any modpacks.", List.of(
                    new OpenModalData.ModalButton( "Exit", "green", CreeperLauncher::exit),
                    new OpenModalData.ModalButton("Continue", "", () -> {
                        Settings.webSocketAPI.sendMessage(new CloseModalData());
                    }))
            );
        }

        if (migrateError)
        {
            OpenModalData.openModal("Warning", "An error occurred whilst migrating your FTB App to a new data structure. Things may still work, but if you have issues, please contact FTB support for assistance.", List.of(
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
    private static void pingPong()
    {
     CompletableFuture.runAsync(() -> {
       while(true)
       {
           try {
               PingLauncherData ping = new PingLauncherData();
               CreeperLauncher.missedPings++;
               Settings.webSocketAPI.sendMessage(ping);
           } catch(Exception ignored) {}
           try {
               Thread.sleep(3000);
           } catch(Exception ignored) {}
           //15 minutes without ping/pong or an explicit disconnect event happened...
           if(missedPings > 300 || websocketDisconnect && missedPings > 3)
           {
               break;
           }
       }
     }).thenRun(() -> {
         if(!websocketDisconnect) {
             CreeperLogger.INSTANCE.error("Closed backend due to no response from frontend for " + (missedPings * 3) + " seconds...");
         } else {
             CreeperLogger.INSTANCE.error("Closed backend due to websocket error! Also no messages from frontend for "+(missedPings * 3) + " seconds.");
         }
         CreeperLauncher.exit();
     });
    }
    public static void listenForClient(int port) throws IOException {
        CreeperLogger.INSTANCE.info("Starting mod socket on port " + port);
        serverSocket = new ServerSocket(port);
        opened = true;
        socket = serverSocket.accept();
        socketWrite = socket.getOutputStream();
        CreeperLogger.INSTANCE.info("Connection received");
        Runtime.getRuntime().addShutdownHook(new Thread(CreeperLauncher::closeOldClient));
        String lastInstance = "";
        ClientLaunchData.Reply reply;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            long lastMessageTime = 0;
            boolean hasStarted = false;
            while (socket.isConnected()) {
                String bufferText = "";
                bufferText = in.readLine();
                if (bufferText.length() == 0) continue;
                JsonObject object = GsonUtils.GSON.fromJson(bufferText, JsonObject.class);
                Object data = new Object();
                if(!hasStarted) hasStarted = (object.has("message") && object.get("message").getAsString().equals("init"));
                if(hasStarted) {
                    if (object.has("data") && object.get("data") != null) {
                        data = object.get("data");
                    }
                    if (object.has("instance") && object.get("instance").getAsString() != null && object.get("instance").getAsString().length() > 0) {
                        lastInstance = object.get("instance").getAsString();
                    }
                    boolean isDone = (object.has("message") && object.get("message").getAsString().equals("done"));
                    if (System.currentTimeMillis() > (lastMessageTime + 200) || isDone) {
                        String type = (object.has("type") && object.get("type").getAsString() != null) ? object.get("type").getAsString() : "";
                        String message = (object.has("message") && object.get("message").getAsString() != null) ? object.get("message").getAsString() : "";
                        reply = new ClientLaunchData.Reply(lastInstance, type, message, data);
                        lastMessageTime = System.currentTimeMillis();
                        try {
                            Settings.webSocketAPI.sendMessage(reply);
                        } catch(Throwable t)
                        {
                            CreeperLogger.INSTANCE.warning("Unable to send MC client loading update to frontend!", t);
                        }
                    }
                    if (isDone) {
                        closeSockets();
                    }
                }
            }
            closeSockets();
        } catch (Throwable e) {
            if(lastInstance.length() > 0) {
                reply = new ClientLaunchData.Reply(lastInstance, "clientDisconnect", new Object());
                Settings.webSocketAPI.sendMessage(reply);
            }

            closeSockets();

            throw e;
        } finally {
            if (in != null) in.close();
        }
    }

    private static void closeSockets() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }

        try {
            if (socketWrite != null) socketWrite.close();
        } catch (IOException ignored) {
        }

        socket = null;
        serverSocket = null;
        socketWrite = null;
    }

    public static void closeOldClient() {
        if(socket != null && socket.isConnected()) {
            try {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("message", "show");
                socket.getOutputStream().write((jsonObject.toString() + "\n").getBytes());
                closeSockets();
            } catch (IOException ignored) {
            }
        }
    }

    private static void startElectron() {
        Path electron;
        OS os = OSUtils.getOs();

        ArrayList<String> args = new ArrayList<>();


        switch (os)
        {
            case MAC:
                electron = Constants.BIN_LOCATION_OURS.resolve("ftbapp.app");
                args.add(0, electron.resolve("Contents/MacOS/ftbapp").toAbsolutePath().toString());
                break;
            case LINUX:
                electron = Constants.BIN_LOCATION_OURS.resolve("ftb-app");
                FileUtils.setFilePermissions(electron);

                args.add(0, electron.toAbsolutePath().toString());

                try {
                    if (Files.exists(Path.of("/proc/sys/kernel/unprivileged_userns_clone")) && new String(Files.readAllBytes(Path.of("/proc/sys/kernel/unprivileged_userns_clone"))).equals("0"))
                    {
                        args.add(1,  "--no-sandbox");
                    }
                } catch (IOException ignored) {}
                break;
            default:
                electron = Constants.BIN_LOCATION_OURS.resolve("ftbapp.exe");
                args.add(0, electron.toAbsolutePath().toString());
        }

        args.add("--ws");
        args.add(websocketPort + ":" + websocketSecret);
        args.add("--pid");
        args.add(String.valueOf(ProcessHandle.current().pid()));

        ProcessBuilder app = new ProcessBuilder(args);

        if (Files.exists(electron))
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
            if(CreeperLauncher.mtConnect != null && CreeperLauncher.mtConnect.isEnabled() && CreeperLauncher.mtConnect.isConnected())
            {
                CreeperLauncher.mtConnect.disconnect();
            }
        } catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
        Settings.saveSettings();
        System.exit(0);
    }
}

