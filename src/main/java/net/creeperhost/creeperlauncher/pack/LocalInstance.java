package net.creeperhost.creeperlauncher.pack;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private String mcVersion;
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
    transient private Runnable postInstall;
    transient private boolean postInstallAsync;
    transient private Runnable prePlay;
    transient private boolean prePlayAsync;
    transient private Runnable preUninstall;
    transient private boolean preUninstallAsync;

    public LocalInstance(FTBPack pack, long versionId)
    {
        //We're making an instance!
        String tmpArt = "";
        UUID uuid = UUID.randomUUID();
        this.uuid = uuid;
        this.versionId = versionId;
        this.path = Settings.settings.getOrDefault("instanceDir", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
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
        if (Settings.settings.containsKey("memory"))
        {
            this.memory = Integer.parseInt(Settings.settings.get("memory"));
        } else
        {
            this.memory = pack.getMinMemory();
        }
        this.recMemory = pack.getRecMemory();
        this.minMemory = pack.getMinMemory();
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
        this.path = Settings.settings.getOrDefault("instanceDir", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
        File json = new File(this.path, "instance.json");
        if (!json.exists()) throw new FileNotFoundException("Instance does not exist!");
        Gson gson = new Gson();

        //This won't work, but my intent is clear so hopefully someone else can show me how?
        JsonReader jr = new JsonReader(new BufferedReader(new FileReader(json.getAbsoluteFile())));
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
        try
        {
            jr.close();
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
        this.path = Settings.settings.getOrDefault("instanceDir", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
    }

    public LocalInstance(LocalInstance originalInstance)
    {
        //this = originalInstance;
        UUID uuid = UUID.randomUUID();
        this.uuid = uuid;
        this.path = Settings.settings.getOrDefault("instanceDir", Constants.INSTANCES_FOLDER_LOC) + File.separator + this.uuid;
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
        return installer;
    }

    public FTBModPackInstallerTask update(long versionId)
    {
        /*File mods = new File(this.path, "mods/");
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
        FileUtils.deleteDirectory(scripts);*/ // Commented out to add new cleanup code



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
                CompletableFuture.runAsync(this.prePlay);
            } else
            {
                this.prePlay.run();
            }
        }
        this.lastPlayed = System.currentTimeMillis() / 1000L;
        Analytics.sendPlayRequest(this.getId(), this.getVersionId());
        McUtils.clearProfiles(new File(Constants.LAUNCHER_PROFILES_JSON));
        Long lastPlay = this.lastPlayed;
        this.lastPlayed = lastPlayed + 9001;
        McUtils.injectProfile(new File(Constants.LAUNCHER_PROFILES_JSON), this.toProfile(), jrePath);
        this.lastPlayed = lastPlay;
        try {
            this.saveJson();
        } catch(Exception ignored) {}
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
        if (javaExec.exists())
        {
            jrePath = javaExec.getAbsolutePath();
        } else
        {
            embeddedJre = true;
            return false;
        }
        return true;
    }
}
