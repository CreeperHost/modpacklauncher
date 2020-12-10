package net.creeperhost.creeperlauncher.pack;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.minetogether.cloudsaves.CloudSaveManager;
import net.creeperhost.creeperlauncher.minetogether.cloudsaves.CloudSyncType;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.util.*;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import net.creeperhost.creeperlauncher.*;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.minecraft.GameLauncher;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.minecraft.Profile;
import net.creeperhost.creeperlauncher.os.OSUtils;
import oshi.util.FileUtil;

import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static net.creeperhost.creeperlauncher.util.MiscUtils.allFutures;

//TODO: Turn LocalInstance into a package somehow and split out the individual parts for easier navigation.
//TODO: Switch prePlay events, postInstall(Semi-Completed), events etc into the same setup as gameClose to allow multiple code blocks.
public class LocalInstance implements IPack
{
    private UUID uuid;
    private long id;
    private String art;
    private String path;
    private long versionId;
    public String name;
    private int minMemory = 2048;
    private int recMemory = 4096;
    public int memory = Integer.parseInt(Settings.settings.getOrDefault("memory", "2048"));
    private String version;
    private String dir;
    private List<String> authors;
    private String description;
    public String mcVersion;
    public String jvmArgs = Settings.settings.getOrDefault("jvmArgs", "");
    public boolean embeddedJre = Boolean.parseBoolean(Settings.settings.getOrDefault("embeddedjre", "true"));
    public String jrePath = Settings.settings.getOrDefault("jrepath", "");
    private String url;
    private String artUrl;
    public int width = Integer.parseInt(Settings.settings.getOrDefault("width", String.valueOf((int) Toolkit.getDefaultToolkit().getScreenSize().getWidth() / 2)));
    public int height = Integer.parseInt(Settings.settings.getOrDefault("height", String.valueOf((int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() / 2)));
    public String modLoader = "";
    private long lastPlayed;
    private boolean isImport = false;
    public boolean cloudSaves = false;
    transient private HashMap<String, instanceEvent> postInstall = new HashMap<>();
    transient private Runnable prePlay;
    transient private int loadingModPort;
    transient private boolean prePlayAsync;
    transient public boolean hasLoadingMod;
    transient private Runnable preUninstall;
    transient private boolean preUninstallAsync;
    transient private AtomicBoolean inUse = new AtomicBoolean(false);
    transient private HashMap<String, instanceEvent> gameCloseEvents = new HashMap<>();
    transient private String tempLauncherPath = null;

    public LocalInstance(FTBPack pack, long versionId)
    {
        //We're making an instance!
        String tmpArt = "";
        UUID uuid = UUID.randomUUID();
        this.uuid = uuid;
        this.versionId = versionId;
        this.path = Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
        this.cloudSaves = Boolean.getBoolean(Settings.settings.getOrDefault("cloudSaves", "false"));
        this.name = pack.getName();
        this.version = pack.getVersion();
        this.dir = this.path;
        this.authors = pack.getAuthors();
        this.description = pack.getDescription();
        this.mcVersion = pack.getMcVersion();
        this.url = pack.getUrl();
        this.artUrl = pack.getArtURL();
        this.id = pack.getId();
        if (Settings.settings.containsKey("jvmargs"))
        {
            this.jvmArgs = Settings.settings.get("jvmargs");
        }
        this.recMemory = pack.getRecMemory();
        this.minMemory = pack.getMinMemory();
        this.memory = this.recMemory;
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        long totalMemory = hal.getMemory().getTotal() / 1024 / 1024;
        if(this.recMemory > (totalMemory-2048))
        {
            this.memory = this.minMemory;
        }
        this.lastPlayed = CreeperLauncher.unixtimestamp();
        Boolean dir = new File(this.path).mkdirs();
        String artPath = this.path + File.separator + "/art.png";
        File artFile = new File(artPath);
        if (!artFile.exists())
        {
            try
            {
                if (ForgeUtils.isUrlValid(this.getArtURL()))
                {
                    artFile.createNewFile();
                    DownloadUtils.downloadFile(artFile, this.getArtURL());
                } else
                {
                    CreeperLogger.INSTANCE.error("The url '" + this.getArtURL() + "' is not valid.");
                }
            } catch (Exception err)
            {
                CreeperLogger.INSTANCE.error("Unable to download and save artwork.");
                err.printStackTrace();
            }
        }
        try
        {
            Base64.Encoder en = Base64.getEncoder();
            String fileLoc = artFile.getAbsoluteFile().toString();
            tmpArt = "data:image/png;base64," + en.encodeToString(Files.readAllBytes(Paths.get(fileLoc)));
//            CreeperLogger.INSTANCE.info(tmpArt);
        } catch (Exception err)
        {
            CreeperLogger.INSTANCE.error("Unable to encode artwork for embedding.");
            err.printStackTrace();
        }
        this.art = tmpArt;
        try
        {
            this.saveJson();
        } catch (Exception err)
        {
            CreeperLogger.INSTANCE.error("Unable to write instance configuration.");
            err.printStackTrace();
        }
    }

    public LocalInstance(UUID uuid) throws FileNotFoundException
    {
        //We're loading an existing instance
        this.uuid = uuid;
        this.path = Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
        File json = new File(this.path, "instance.json");
        if (!json.exists()) throw new FileNotFoundException("Instance does not exist!");
        Gson gson = new Gson();

        //This won't work, but my intent is clear so hopefully someone else can show me how?
        try {
            FileReader fileReader = new FileReader(json.getAbsoluteFile());
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            JsonReader jr = new JsonReader(bufferedReader);
            LocalInstance jsonOutput = (LocalInstance) gson.fromJson(jr, LocalInstance.class);
            this.id = jsonOutput.id;
            this.name = jsonOutput.name;
            this.artUrl = jsonOutput.artUrl;
            this.mcVersion = jsonOutput.mcVersion;
            this.authors = jsonOutput.authors;
            this.art = jsonOutput.art;
            this.memory = jsonOutput.memory;
            this.version = jsonOutput.version;
            this.versionId = jsonOutput.versionId;
            this.width = jsonOutput.width;
            this.height = jsonOutput.height;
            this.url = jsonOutput.url;
            this.minMemory = jsonOutput.minMemory;
            this.recMemory = jsonOutput.recMemory;
            this.lastPlayed = jsonOutput.lastPlayed;
            this.jvmArgs = jsonOutput.jvmArgs;
            this.modLoader = jsonOutput.modLoader;
            this.jrePath = jsonOutput.jrePath;
            this.dir = this.path;
            this.cloudSaves = jsonOutput.cloudSaves;
            this.hasLoadingMod = checkForLaunchMod();
            try
            {
                jr.close();
                bufferedReader.close();
                fileReader.close();
            } catch (IOException ignored)
            {
            }
        } catch(Exception e)
        {
            throw new FileNotFoundException("Instance is corrupted!");
        }
    }

    public LocalInstance(String LauncherImportPath, Boolean isTwitch)
    {
        //Import a pack from the FTB or Twitch local packs
        UUID uuid = UUID.randomUUID();
        this.uuid = uuid;
        this.isImport = true;
        this.path = Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
        this.hasLoadingMod = checkForLaunchMod();
    }

    public LocalInstance(LocalInstance originalInstance)
    {
        //this = originalInstance;
        UUID uuid = UUID.randomUUID();
        this.uuid = uuid;
        this.path = Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
        this.hasLoadingMod = checkForLaunchMod();
    }

    private static String[] candidates = new String[] {"net/creeperhost/traylauncher/TrayLauncher.class", "net/creeperhost/launchertray/LauncherTray.class", "net/creeperhost/launchertray/transformer/HookLoader.class"};

    private boolean checkForLaunchMod()
    {
        JarFile jarFile = null;
        try {
            File[] modsDir = new File(this.path, "mods/").listFiles();
            if (modsDir == null) return false;


            for (File file : modsDir) {
                try {
                    jarFile = new JarFile(file);
                    if (jarFile == null) continue;

                    if(jarFile.getManifest() == null)
                    {
                        jarFile.close();
                        continue;
                    }

                    Enumeration<JarEntry> entries = jarFile.entries();


                    boolean found = false;
                    for(String candidate: candidates) {
                        if (jarFile.getEntry(candidate) != null) {
                            found = true;
                            break;
                        }
                    }

                    if(!found)
                    {
                        jarFile.close();
                        continue;
                    }

                    Map<String, Attributes> attributesMap = jarFile.getManifest().getEntries();

                    jarFile.close();

                    if (attributesMap == null) {
                        continue;
                    }
                    return true;
                } catch (IOException e) {
                    if(jarFile != null) {
                        jarFile.close();
                    }
                }
            }
            if (jarFile != null) jarFile.close();
            return false;
        } catch (Throwable e)
        {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private LocalInstance()
    {
    }
    //Does java have a deconstructor? I wanna save the json on deconstruct to make sure

    public FTBModPackInstallerTask install()
    {
        // Can't reinstall an import...
        //Download everything! wget --mirror http://thewholeinternet.kthxbai
        FTBModPackInstallerTask installer = new FTBModPackInstallerTask(this);
        if (!this.isImport)
        {
            CreeperLauncher.isInstalling.set(true);
            Analytics.sendInstallRequest(this.getId(), this.getVersionId());
            CreeperLogger.INSTANCE.debug("Running installer async task");
            installer.execute().thenRunAsync(() ->
            {
                CreeperLogger.INSTANCE.debug("Running after installer task");
                if (this.postInstall != null && this.postInstall.size() > 0)
                {
                    ArrayList<CompletableFuture> futures = new ArrayList<>();
                    for(Map.Entry<String, instanceEvent> event : this.postInstall.entrySet())
                    {
                        futures.add(event.getValue().Run());
                    }
                    allFutures(futures).thenRunAsync(() -> {
                        FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                        CreeperLauncher.isInstalling.set(false);
                    });
                } else {
                    FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                    CreeperLauncher.isInstalling.set(false);
                }
                this.hasLoadingMod = checkForLaunchMod();
            });
        }
        return installer;
    }

    public FTBModPackInstallerTask update(long versionId)
    {
        this.versionId = versionId;

        FTBModPackInstallerTask update = new FTBModPackInstallerTask(this);

        try {
            List<DownloadableFile> requiredDownloads = update.getRequiredDownloads(new File(this.path, "version.json"), null);
            requiredDownloads.forEach(e -> {
                File file = new File(e.getPath());
                file.delete();
            });
        } catch (MalformedURLException e) {
            // fall back to old delete
            File mods = new File(this.path, "mods/");
            File coremods = new File(this.path, "coremods/");
            File instmods = new File(this.path, "instmods/");

            File config = new File(this.path, "config/");
            File resources = new File(this.path, "resources/");
            File scripts = new File(this.path, "scripts/");

            FileUtils.deleteDirectory(mods);
            FileUtils.deleteDirectory(coremods);
            FileUtils.deleteDirectory(instmods);
            FileUtils.deleteDirectory(config);
            FileUtils.deleteDirectory(resources);
            FileUtils.deleteDirectory(scripts);
        }
        this.hasLoadingMod = checkForLaunchMod();
        update.execute().thenRunAsync(() ->
        {
            this.updateVersionFromFile();
            try {
                this.saveJson();
            } catch (IOException ignored){}
            if (this.postInstall != null && this.postInstall.size() > 0)
            {
                ArrayList<CompletableFuture> futures = new ArrayList<>();
                for(Map.Entry<String, instanceEvent> event : this.postInstall.entrySet())
                {
                    futures.add(event.getValue().Run());
                }
                allFutures(futures).thenRunAsync(() -> {
                    FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                    CreeperLauncher.isInstalling.set(false);
                });
            } else {
                FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                CreeperLauncher.isInstalling.set(false);
            }
        });
        return update;
    }
    public Profile toProfile()
    {
        return toProfile("");
    }
    public Profile toProfile(String extraArgs)
    {
        String totalArgs = Settings.settings.getOrDefault("jvmargs", "") + " " + jvmArgs;
        if(totalArgs.length() > 0 && extraArgs.length() > 0) totalArgs = totalArgs.trim() + " " + extraArgs.trim();
        return new Profile(getUuid().toString(), getName(), getMcVersion(), modLoader, MiscUtils.getDateAndTime(), "custom", dir, art, totalArgs, memory, width, height);
    }
    public Process play()
    {
        return play("");
    }
    public Process play(String extraArgs)
    {
        List<Process> processes = CreeperLauncher.mojangProcesses.get();
        if(processes != null) {
            for (Process mojang : processes) {
                if (mojang.isAlive()) {
                    CreeperLogger.INSTANCE.error("Mojang launcher, started by us is still running with PID " + mojang.pid());
                    try {
                        mojang.destroyForcibly().waitFor();
                        //No need to clean up here as onExit() is fired.
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (this.prePlay != null)
        {
            if (this.prePlayAsync)
            {
                CreeperLogger.INSTANCE.debug("Doing pre-play tasks async");
                CompletableFuture.runAsync(this.prePlay);
            } else {
                CreeperLogger.INSTANCE.debug("Doing pre-play tasks non async");
                this.prePlay.run();
            }
        }
        if(!Constants.S3_SECRET.isEmpty() && !Constants.S3_KEY.isEmpty() && !Constants.S3_HOST.isEmpty() && !Constants.S3_BUCKET.isEmpty()) {
            CreeperLogger.INSTANCE.debug("Doing cloud sync");
            CompletableFuture.runAsync(() -> this.cloudSync(false)).join();
            onGameClose("cloudSync", () -> {
                if(cloudSaves) {
                    this.cloudSync(false);
                }
            });
        }
        McUtils.verifyJson(new File(Constants.LAUNCHER_PROFILES_JSON));
        this.lastPlayed = CreeperLauncher.unixtimestamp();
        CreeperLogger.INSTANCE.debug("Sending play request to API");
        Analytics.sendPlayRequest(this.getId(), this.getVersionId());
        CreeperLogger.INSTANCE.debug("Clearing existing Mojang launcher profiles");
        McUtils.clearProfiles(new File(Constants.LAUNCHER_PROFILES_JSON));
        Long lastPlay = this.lastPlayed;
        this.lastPlayed = lastPlayed + 9001;
        CreeperLogger.INSTANCE.debug("Injecting profile to Mojang launcher");


        this.hasLoadingMod = checkForLaunchMod();
        //TODO: THIS IS FOR TESTING ONLY, PLEASE REMOVE ME IN FUTURE
        if(OSUtils.getOs() == OS.WIN)
        {
            if (!this.hasLoadingMod) {
                if (modLoader.startsWith("1.7.10")) {
                    DownloadUtils.downloadFile(new File(dir, "mods" + File.separator + "launchertray-1.0.jar"), "https://dist.creeper.host/modpacks/maven/com/sun/jna/1.7.10-1.0.0/d4c2da853f1dbc80ab15b128701001fd3af6718f");
                    this.hasLoadingMod = checkForLaunchMod();
                } else if (modLoader.startsWith("1.12.2")) {
                    DownloadUtils.downloadFile(new File(dir, "mods" + File.separator + "launchertray-1.0.jar"), "https://dist.creeper.host/modpacks/maven/net/creeperhost/launchertray/transformer/1.0/381778e244181cc2bb7dd02f03fb745164e87ee0");
                    this.hasLoadingMod = checkForLaunchMod();
                } else if (modLoader.startsWith("1.15") || modLoader.startsWith("1.16")) {
                    DownloadUtils.downloadFile(new File(dir, "mods" + File.separator + "launchertray-1.0.jar"), "https://dist.creeper.host/modpacks/maven/net/creeperhost/traylauncher/1.0/134dd1944e04224ce53ff18750e81f5517704c8e");
                    DownloadUtils.downloadFile(new File(dir, "mods" + File.separator + "launchertray-progress-1.0.jar"), "https://dist.creeper.host/modpacks/maven/net/creeperhost/traylauncher/unknown/74ced30ca35e88b583969b6d74efa0f7c2470e8b");
                    this.hasLoadingMod = checkForLaunchMod();
                }
            }
            //END TESTING CODE
            if (this.hasLoadingMod) {
                CreeperLauncher.closeOldClient();
                int retries = 0;
                AtomicBoolean hasErrored = new AtomicBoolean(true);
                while (hasErrored.get()) {
                    //Retry ports...
                    hasErrored.set(false);
                    this.loadingModPort = MiscUtils.getRandomNumber(50001, 52000);
                    CompletableFuture.runAsync(() -> {
                        try {
                            CreeperLauncher.listenForClient(this.loadingModPort);
                        } catch (Exception err) {
                            if (!CreeperLauncher.opened)
                            {
                                CreeperLogger.INSTANCE.error("Error whilst starting mod socket on port '" + this.loadingModPort + "'...", err);
                                hasErrored.set(true);
                            } else {
                                CreeperLogger.INSTANCE.warning("Error whilst handling message from mod socket - probably nothing!", err);
                                CreeperLauncher.opened = false;
                            }

                        }
                    });
                    try {
                        Thread.sleep(100);
                        if (retries >= 5) break;
                        retries++;
                    } catch (Exception ignored) {
                    }
                }
                if (!hasErrored.get()) {
                    if (extraArgs.length() > 0) extraArgs = extraArgs + " ";
                    extraArgs += "-Dchtray.port=" + this.loadingModPort + " -Dchtray.instance=" + this.uuid.toString() + " ";
                } else {
                    CreeperLogger.INSTANCE.error("Unable to open loading mod listener port... Tried " + retries + " times.");
                }
            }
        }

        Profile profile = (extraArgs.length() > 0) ? this.toProfile(extraArgs) : this.toProfile();
        tempLauncherPath = Constants.BIN_LOCATION;
        if(!McUtils.injectProfile(new File(tempLauncherPath + File.separator + "launcher_profiles.json"), profile, jrePath))
        {
            //Can't write to our normal directory, so we'll copy the launcher to a temporary directory and try there!
            tempLauncherPath = GameLauncher.prepareGame();
            if(!McUtils.injectProfile(new File(tempLauncherPath + File.separator + "launcher_profiles.json"), profile, jrePath))
            {

                CreeperLogger.INSTANCE.error("Unable to inject Mojang launcher profile...");
                OpenModalData.openModal("Error", "Unable to create Mojang launcher profile. Please ensure you do not have any security software blocking access to the FTB App data directories.", List.of(
                        new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                ));
                return null;
            }
        }
        CreeperLogger.INSTANCE.warning("Starting launcher at "+tempLauncherPath);

        this.lastPlayed = lastPlay;
        try {
            CreeperLogger.INSTANCE.debug("Saving instance json");
            this.saveJson();
        } catch(Exception e) {
            CreeperLogger.INSTANCE.error("Failed to save instance!", e);
        }

        CreeperLogger.INSTANCE.debug("Starting Mojang launcher");
        AtomicReference<Process> launcher = new AtomicReference<>();
        CompletableFuture.runAsync(() -> {
            launcher.set(GameLauncher.launchGame(tempLauncherPath));
            CreeperLauncher.mojangProcesses.getAndUpdate((List<Process> _processes) -> {
                if (_processes == null) _processes = new ArrayList<Process>();
                if (launcher != null) _processes.add(launcher.get());
                return _processes;
            });
        }).join();
        if(CreeperLauncher.mtConnect != null) {
            if (CreeperLauncher.mtConnect.isEnabled()) {
                try {
                    Thread.sleep(20000);
                } catch(Exception ignored) {} //Just a small sleep so we're not messing with routing and NIC's just as the Vanilla launcher opens.
                CreeperLogger.INSTANCE.info("MineTogether Connect is enabled... Connecting...");
                CreeperLauncher.mtConnect.connect();
                onGameClose("CleanTempLauncherLoc", () -> {
                    if(!tempLauncherPath.equalsIgnoreCase(Constants.BIN_LOCATION))
                    {
                        try {
                            CreeperLogger.INSTANCE.warning("Cleaning up temporary launcher at "+tempLauncherPath);
                            FileUtils.deleteDirectory(Path.of(tempLauncherPath));
                            Files.deleteIfExists(Path.of(tempLauncherPath));
                            CreeperLogger.INSTANCE.warning("Cleaned up temporary launcher at "+tempLauncherPath);
                        } catch (IOException e) {
                            CreeperLogger.INSTANCE.warning("Error cleaning up temporary launcher!", e);
                        }
                    }
                });
                onGameClose("MTC-Disconnect", () -> {
                    if (CreeperLauncher.mtConnect.isConnected()) {
                        CreeperLogger.INSTANCE.info("MineTogether Connect is enabled... Disconnecting...");
                        CreeperLauncher.mtConnect.disconnect();
                    }
                });
            } else {
                CreeperLogger.INSTANCE.info("MineTogether Connect is not enabled...");
            }
        } else {
            CreeperLogger.INSTANCE.error("Unable to initialize MineTogether Connect!");
        }
        if (launcherWait != null && (!launcherWait.isDone())) launcherWait.cancel(true);
        launcherWait = CompletableFuture.runAsync(() -> {
           inUseCheck(launcher.get());
        });

        return launcher.get();
    }
    private transient CompletableFuture launcherWait;
    public void setPostInstall(Runnable lambda, boolean async)
    {
        this.postInstall.put("postInstall", new instanceEvent(lambda, !async));
    }

    public void setPrePlay(Runnable hook, boolean async)
    {
        this.prePlay = hook;
        this.prePlayAsync = async;
    }

    public void setPreUninstall(Runnable hook, boolean async)
    {
        this.preUninstall = hook;
        this.preUninstallAsync = async;
    }

    public boolean uninstall() throws IOException
    {
        if (this.preUninstall != null)
        {
            if (this.preUninstallAsync)
            {
                CompletableFuture.runAsync(this.preUninstall);
            } else
            {
                this.preUninstall.run();
            }
        }
        File instancedir = new File(this.path);
        Files.walk(instancedir.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        instancedir.delete();
        Instances.refreshInstances();
        return true;
    }

    public LocalInstance clone()
    {
        //Clone the instance on the file system and return the new instance
        return this;
    }

    public boolean browse() throws IOException
    {
        if (Desktop.isDesktopSupported())
        {
            Desktop.getDesktop().open(new File(this.path));
            return true;
        }
        return false;
    }

    public boolean saveJson() throws IOException
    {
        File json = new File(this.path, "instance.json");
        if (!json.exists()) json.createNewFile();
        BufferedWriter fileWriter = new BufferedWriter(new FileWriter(json.getAbsolutePath()));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        fileWriter.write(gson.toJson(this));
        fileWriter.close();
        return true;
    }

    @Override
    public long getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getVersion()
    {
        return version;
    }

    private void updateVersionFromFile()
    {
        JsonReader versionReader = null;
        try
        {
            versionReader = new JsonReader(new FileReader(new File(this.getDir() + File.separator + "version.json")));
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }

        JsonElement jElement = new JsonParser().parse(versionReader);
        if (jElement.isJsonObject()) {
            JsonObject version = (JsonObject) jElement;
            this.version = version.get("name").getAsString();
        }
    }
    @Override
    public String getDir()
    {
        return dir;
    }

    @Override
    public List<String> getAuthors()
    {
        return authors;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String getMcVersion()
    {
        return mcVersion;
    }

    @Override
    public String getUrl()
    {
        return url;
    }

    @Override
    public String getArtURL()
    {
        return artUrl;
    }

    @Override
    public int getMinMemory()
    {
        return minMemory;
    } // Not needed but oh well, may as well return a value.

    @Override
    public int getRecMemory()
    {
        return recMemory;
    }

    public long getVersionId()
    {
        return versionId;
    }

    public UUID getUuid()
    {
        return uuid;
    }

    public String getModLoader()
    {
        return modLoader;
    }

    public boolean setJre(boolean autoDetect, String path)
    {
        File javaExec = null;
        String javaBinary = "javaw.exe";
        switch (OSUtils.getOs())
        {
            case LINUX:
            case MAC:
                javaBinary = "java";
                break;
        }
        if (autoDetect)
        {
            if (!this.embeddedJre)
            {
                String javaHome = System.getProperty("java.home");
                javaExec = new File(new File(new File(javaHome), "bin"), javaBinary);
            }
        } else
        {
            javaExec = new File(path, javaBinary);
        }
        if (javaExec != null && javaExec.exists())
        {
            jrePath = javaExec.getAbsolutePath();
        } else
        {
            embeddedJre = true;
            return false;
        }
        return true;
    }
    private transient CompletableFuture inUseThread;
    private void inUseCheck(Process vanillaLauncher)
    {
        if(inUseThread != null && !inUseThread.isDone()) return;
        inUseThread = CompletableFuture.runAsync(() -> {
            boolean fireEvents = false;
            while(true)
            {
                if(!vanillaLauncher.isAlive()) {
                    boolean inUse = isInUse(true);
                    if (!fireEvents) fireEvents = inUse;

                    if (fireEvents && !inUse) {

                        for (Map.Entry<String, instanceEvent> event : gameCloseEvents.entrySet()) {
                            CreeperLogger.INSTANCE.info("Running game close event '" + event.getKey() + "'...");
                            event.getValue().Run();
                        }
                        fireEvents = false;
                    } else {
                        if (!fireEvents) {
                            break;
                        }
                    }
                } else {
                    if(!fireEvents) fireEvents = true;
                }
                try {
                    if(vanillaLauncher.isAlive()) {
                        Thread.sleep(250);
                    } else {
                        //Expensive file checking should happen less often...
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException ignored) {}
            }
            CreeperLogger.INSTANCE.debug("Game close event listener stopped...");
        });
    }
    public void onGameClose(String name, Runnable lambda)
    {
        if(gameCloseEvents.containsKey(name)) return;
        gameCloseEvents.put(name, new instanceEvent(lambda, name));
    }
    public boolean isInUse(boolean checkFiles)
    {
        if (inUse.get()) return true;
        if (checkFiles)
        {
            String dir = getDir();
            File file = new File(dir);
            if (!file.exists()) return false;
            File modsFile = new File(dir, "mods");
            if (modsFile.exists() && modsFile.canWrite())
            {
                File[] files = modsFile.listFiles();
                if (files != null) {
                    try(FileLock ignored = new RandomAccessFile(files[files.length - 1], "rw").getChannel().tryLock()) {} catch (Throwable t) {
                        return true;
                    }
                }
            }

            File savesFile = new File(dir, "saves");
            if (savesFile.exists() && savesFile.isDirectory())
            {
                File[] files = savesFile.listFiles();
                if (files != null) {
                    for(File savesDirectory : files) {
                        if (savesDirectory.isDirectory())
                        {
                            File lockFile = new File(savesDirectory, "session.lock");
                            if (lockFile.exists()) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void setInUse(boolean var)
    {
        inUse.set(var);
    }

    public void cloudSync(boolean forceCloud)
    {
        if(!cloudSaves || !Boolean.parseBoolean(Settings.settings.getOrDefault("cloudSaves", "false"))) return;
        OpenModalData.openModal("Please wait", "Checking cloud save synchronization <br>", List.of());

        if(isInUse(true)) return;

        AtomicInteger progress = new AtomicInteger(0);

        setInUse(true);
        CreeperLauncher.isSyncing.set(true);

        HashMap<String, S3ObjectSummary> s3ObjectSummaries = CloudSaveManager.listObjects(this.uuid.toString());
        AtomicBoolean syncConflict = new AtomicBoolean(false);

        for(S3ObjectSummary s3ObjectSummary : s3ObjectSummaries.values())
        {
            File file = new File(Constants.INSTANCES_FOLDER_LOC + File.separator + s3ObjectSummary.getKey());
            CreeperLogger.INSTANCE.debug(s3ObjectSummary.getKey() + " " + file.getAbsolutePath());

            if(s3ObjectSummary.getKey().contains("/saves/"))
            {
                try
                {
                    CloudSaveManager.downloadFile(s3ObjectSummary.getKey(), file, true, s3ObjectSummary.getETag());
                } catch (Exception e)
                {
                    syncConflict.set(true);
                    e.printStackTrace();
                    break;
                }
                continue;
            }

            if(!file.exists())
            {
                syncConflict.set(true);
                break;
            }
        }

        Runnable fromCloud = () ->
        {
            OpenModalData.openModal("Please wait", "Synchronizing", List.of());

            int localProgress = 0;
            int localTotal = s3ObjectSummaries.size();

            for(S3ObjectSummary s3ObjectSummary : s3ObjectSummaries.values())
            {
                localProgress++;

                float percent = Math.round(((float)((float)localProgress / (float)localTotal) * 100) * 100F) / 100F;

                OpenModalData.openModal("Please wait", "Synchronizing <br>" + percent + "%", List.of());

                if(s3ObjectSummary.getKey().contains(this.uuid.toString()))
                {
                    File file = new File(Constants.INSTANCES_FOLDER_LOC + File.separator + s3ObjectSummary.getKey());
                    if(!file.exists())
                    {
                        try
                        {
                            CloudSaveManager.downloadFile(s3ObjectSummary.getKey(), file, true, null);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }}
            cloudSyncLoop(this.path, false, CloudSyncType.SYNC_MANUAL_SERVER, s3ObjectSummaries);
            syncConflict.set(false);
            Settings.webSocketAPI.sendMessage(new CloseModalData());
        };
        if(forceCloud)
        {
            fromCloud.run();
        }
        else if(syncConflict.get())
        {
            //Open UI
            OpenModalData.openModal("Cloud Sync Conflict", "We have detected a synchronization error between your saves, How would you like to resolve?", List.of
            ( new OpenModalData.ModalButton("Use Cloud", "green", fromCloud), new OpenModalData.ModalButton("Use Local", "red", () ->
            {
                OpenModalData.openModal("Please wait", "Synchronizing", List.of());

                int localProgress = 0;
                int localTotal = s3ObjectSummaries.size();

                for(S3ObjectSummary s3ObjectSummary : s3ObjectSummaries.values())
                {
                    localProgress++;

                    float percent = Math.round(((float)((float)localProgress / (float)localTotal) * 100) * 100F) / 100F;

                    OpenModalData.openModal("Please wait", "Synchronizing <br>" + percent + "%", List.of());

                    File file = new File(Constants.INSTANCES_FOLDER_LOC + File.separator + s3ObjectSummary.getKey());
                    if(!file.exists())
                    {
                        try
                        {
                            CloudSaveManager.deleteFile(s3ObjectSummary.getKey());
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
                cloudSyncLoop(this.path, false, CloudSyncType.SYNC_MANUAL_CLIENT, s3ObjectSummaries);
                syncConflict.set(false);
                Settings.webSocketAPI.sendMessage(new CloseModalData());
            }), new OpenModalData.ModalButton("Ignore", "orange", () ->
            {
                cloudSaves = false;
                try {
                    saveJson();
                } catch (IOException e) { e.printStackTrace(); }
                syncConflict.set(false);
                Settings.webSocketAPI.sendMessage(new CloseModalData());
            })));
            while (syncConflict.get())
            {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
        else
        {
            cloudSyncLoop(this.path, false, CloudSyncType.SYNC_NORMAL, s3ObjectSummaries);
            Settings.webSocketAPI.sendMessage(new CloseModalData());
        }
        setInUse(false);
        CreeperLauncher.isSyncing.set(false);
    }

    public void cloudSyncLoop(String path, boolean ignoreInUse, CloudSyncType cloudSyncType, HashMap<String, S3ObjectSummary> existingObjects)
    {
        final String host = Constants.S3_HOST;
        final int port = 8080;
        final String accessKeyId = Constants.S3_KEY;
        final String secretAccessKey = Constants.S3_SECRET;
        final String bucketName = Constants.S3_BUCKET;

        Path baseInstancesPath = Path.of(Settings.settings.getOrDefault("instancesLocation", Constants.INSTANCES_FOLDER_LOC));

        File file = new File(path);
        CloudSaveManager.setup(host, port, accessKeyId, secretAccessKey, bucketName);
        if(file.isDirectory())
        {
            File[] dirContents = file.listFiles();
            if(dirContents != null && dirContents.length > 0) {
                for (File innerFile : dirContents) {
                    cloudSyncLoop(innerFile.getAbsolutePath(), true, cloudSyncType, existingObjects);
                }
            } else {
                try {
                    //Add a / to allow upload of empty directories

                    File file1 = new File(file.getAbsoluteFile() + File.separator);
                    CloudSaveManager.syncFile(file1, CloudSaveManager.fileToLocation(file1, baseInstancesPath), true, existingObjects);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
        else
        {
            try
            {
                CreeperLogger.INSTANCE.debug("Uploading file " + file.getAbsolutePath());
                switch (cloudSyncType)
                {
                    case SYNC_NORMAL:
                        try
                        {
                            ArrayList<CompletableFuture> futures = new ArrayList<>();
                            futures.add(CompletableFuture.runAsync(() ->
                            {
                                try
                                {
                                    CloudSaveManager.syncFile(file, CloudSaveManager.fileToLocation(file, baseInstancesPath), true, existingObjects);
                                } catch (Exception e) { e.printStackTrace(); }
                            }, DownloadTask.threadPool));

                            allFutures(futures).join();
                        } catch (Throwable t)
                        {
                            t.printStackTrace();
                        }
                        break;
                    case SYNC_MANUAL_CLIENT:
                        CloudSaveManager.syncManual(file, CloudSaveManager.fileToLocation(file, Path.of(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC))), true, true, existingObjects);
                        break;
                    case SYNC_MANUAL_SERVER:
                        CloudSaveManager.syncManual(file, CloudSaveManager.fileToLocation(file, Path.of(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC))), true, false, existingObjects);
                        break;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
