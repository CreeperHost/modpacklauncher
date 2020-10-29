package net.creeperhost.creeperlauncher.pack;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.cloudsaves.CloudSaveManager;
import net.creeperhost.creeperlauncher.cloudsaves.CloudSyncType;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import net.creeperhost.creeperlauncher.*;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.minecraft.GameLauncher;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.minecraft.Profile;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.ForgeUtils;
import net.creeperhost.creeperlauncher.util.MiscUtils;

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
    transient private Runnable postInstall;
    transient private boolean postInstallAsync;
    transient private Runnable prePlay;
    transient private int loadingModPort;
    transient private boolean prePlayAsync;
    transient public boolean hasLoadingMod;
    transient private Runnable preUninstall;
    transient private boolean preUninstallAsync;
    transient private AtomicBoolean inUse = new AtomicBoolean(false);

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
        this.lastPlayed = System.currentTimeMillis() / 1000L;
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
    private boolean checkForLaunchMod()
    {
        File mods = new File(this.path, "mods/");
        for(File mod : mods.listFiles())
        {
            if(mod.getName() == "ClientLaunch.jar") return true;
        }
        return false;
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
            installer.execute().thenRunAsync(() ->
            {
                if (this.postInstall != null)
                {
                    if (this.postInstallAsync)
                    {
                        CompletableFuture.runAsync(this.postInstall).thenRunAsync(() -> {
                           FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                            CreeperLauncher.isInstalling.set(false);
                        });
                    } else
                    {
                        this.postInstall.run();
                        FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                        CreeperLauncher.isInstalling.set(false);
                    }
                } else {
                    FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                    CreeperLauncher.isInstalling.set(false);
                }
            });
        }
        this.hasLoadingMod = checkForLaunchMod();
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
            if (this.postInstall != null)
            {
                if (this.postInstallAsync)
                {
                    CompletableFuture.runAsync(this.postInstall).thenRunAsync(() -> {
                        FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                        CreeperLauncher.isInstalling.set(false);
                    });
                } else
                {
                    this.postInstall.run();
                    FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                    CreeperLauncher.isInstalling.set(false);
                }
            } else {
                FTBModPackInstallerTask.currentStage = FTBModPackInstallerTask.Stage.FINISHED;
                CreeperLauncher.isInstalling.set(false);
            }
        });
        return update;
    }

    public Profile toProfile()
    {
        return new Profile(getUuid().toString(), getName(), getMcVersion(), modLoader, MiscUtils.getDateAndTime(), "custom", dir, art, Settings.settings.getOrDefault("jvmargs", "") + " " + jvmArgs, memory, width, height);
    }

    public GameLauncher play()
    {

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
        }

        this.lastPlayed = System.currentTimeMillis() / 1000L;
        CreeperLogger.INSTANCE.debug("Sending play request to API");
        Analytics.sendPlayRequest(this.getId(), this.getVersionId());
        CreeperLogger.INSTANCE.debug("Clearing existing Mojang launcher profiles");
        McUtils.clearProfiles(new File(Constants.LAUNCHER_PROFILES_JSON));
        Long lastPlay = this.lastPlayed;
        this.lastPlayed = lastPlayed + 9001;
        CreeperLogger.INSTANCE.debug("Injecting profile to Mojang launcher");


        if(this.hasLoadingMod)
        {
            this.loadingModPort = (int)(Math.random() * (65534 - 50000 + 1) + 50000);
            CompletableFuture.runAsync(() -> {
                CreeperLauncher.listenForClient(this.loadingModPort);
            });
            this.jvmArgs += " -Dchtray.port="+this.loadingModPort;
        }


        McUtils.injectProfile(new File(Constants.LAUNCHER_PROFILES_JSON), this.toProfile(), jrePath);
        this.lastPlayed = lastPlay;
        try {
            CreeperLogger.INSTANCE.debug("Saving instance json");
            this.saveJson();
        } catch(Exception ignored) {}

        CreeperLogger.INSTANCE.debug("Starting Mojang launcher");

        GameLauncher launcher = new GameLauncher();
        launcher.launchGame();

        return launcher;
    }

    public void setPostInstall(Runnable hook, boolean async)
    {
        this.postInstall = hook;
        this.postInstallAsync = async;
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
                            List<CompletableFuture> futures = new ArrayList<>();
                            futures.add(CompletableFuture.runAsync(() ->
                            {
                                try
                                {
                                    CloudSaveManager.syncFile(file, CloudSaveManager.fileToLocation(file, baseInstancesPath), true, existingObjects);
                                } catch (Exception e) { e.printStackTrace(); }
                            }, DownloadTask.threadPool));

                            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(
                                    futures.toArray(new CompletableFuture[0])).exceptionally((t) ->
                                    {
                                        t.printStackTrace();
                                        return null;
                                    }
                            );

                            futures.forEach((blah) ->
                            {
                                ((CompletableFuture<Void>) blah).exceptionally((t) ->
                                {
                                    combinedFuture.completeExceptionally(t);
                                    return null;
                                });
                            });

                            combinedFuture.join();
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
