package net.creeperhost.creeperlauncher.install.tasks;

import com.google.common.hash.HashCode;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.util.concurrent.ConcurrentHashMap;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.SimpleDownloadableFile;
import net.creeperhost.creeperlauncher.minecraft.GameLauncher;
import net.creeperhost.creeperlauncher.minecraft.modloader.ModLoader;
import net.creeperhost.creeperlauncher.minecraft.modloader.ModLoaderManager;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.pack.FTBPack;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.creeperhost.creeperlauncher.util.MiscUtils.allFutures;

public class FTBModPackInstallerTask implements IInstallTask<Void>
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Gson gson = new Gson();
    public static AtomicLong currentSpeed = new AtomicLong(0);
    public static AtomicLong averageSpeed = new AtomicLong(0);
    public static AtomicLong overallBytes = new AtomicLong(0);
    public static AtomicLong currentBytes = new AtomicLong(0);
    public static AtomicLong startTime = new AtomicLong(0);
    public static AtomicReference<String> lastError = new AtomicReference<String>();
    public static ConcurrentHashMap<Long, String> batchedFiles = new ConcurrentHashMap<>();
    public String currentUUID = "";
    public boolean _private = false;
    public CompletableFuture<Void> currentTask = null;
    public static Stage currentStage = Stage.INIT;
    LocalInstance instance;

    public enum Stage
    {
        INIT,
        VANILLA,
        API,
        FORGE,
        DOWNLOADS,
        POSTINSTALL,
        FINISHED
    }

    public FTBModPackInstallerTask(LocalInstance instance)
    {
        this.instance = instance;
        try
        {
            this.currentUUID = instance.getUuid().toString();
        } catch (Exception ignored)
        {
        }
    }

    @Override
    public CompletableFuture<Void> execute()
    {
        LOGGER.debug("Running install execute");
        return currentTask = CompletableFuture.runAsync(() ->
        {
            LOGGER.debug("Actually running install execute");
            currentStage = Stage.INIT;
            overallBytes.set(0);
            currentBytes.set(0);
            currentSpeed.set(0);
            startTime.set(System.currentTimeMillis());
            lastError.set("");
            LOGGER.info("{} {} {}", instance.getName(), instance.getId(), instance.getVersionId());
            Path instanceRoot = Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC);
            FileUtils.createDirectories(instanceRoot);
            LOGGER.debug("Setting stage to VANILLA");
            currentStage = Stage.VANILLA;
            LOGGER.debug("About to download launcher");
            OS.CURRENT.getPlatform().installLauncher();
            Path profileJson = Constants.LAUNCHER_PROFILES_JSON;
            LOGGER.debug("Launching game and close");
            if (Files.notExists(profileJson)) GameLauncher.downloadLauncherProfiles();
            Path instanceDir = instance.getDir();
            FileUtils.createDirectories(instanceDir);
            currentStage = Stage.API;
            downloadJsons(instanceDir, this._private, this.instance.packType);
            currentStage = Stage.FORGE;
            Path forgeJson = installModLoaders();
            currentStage = Stage.DOWNLOADS;
            downloadFiles(instanceDir, forgeJson);
            currentStage = Stage.POSTINSTALL;
        }, CreeperLauncher.taskExeggutor);
    }

    public boolean cancel()
    {
        return currentTask.cancel(true);
    }

    @Override
    public Double getProgress()
    {
        if (FTBModPackInstallerTask.currentBytes.get() == 0 || overallBytes.get() == 0) return 0.00d;
        double initPercent = FTBModPackInstallerTask.currentBytes.get() / (double) overallBytes.get();
        Double returnVal = Math.round((initPercent * 100d) * 100d) / 100d;
        return (returnVal > 100.00d) ? 100.00d : returnVal;
    }

    public boolean downloadJsons(Path instanceDir, boolean _private, byte packType)
    {
        LOGGER.info("Preparing instance folder for {}", instanceDir.toAbsolutePath());
        FileUtils.createDirectories(instanceDir);

        Path modpackJson = instanceDir.resolve("modpack.json");
        //Need to remove and redownload this each time or updates will have old info
        try {
            Files.deleteIfExists(modpackJson);
        } catch (IOException ignored) {
        }
        DownloadUtils.downloadFile(modpackJson, Constants.getCreeperhostModpackSearch2(_private, packType) + instance.getId());

        Path versionJson = instanceDir.resolve("version.json");
        if (Files.exists(versionJson))
        {
            try {
                Files.delete(versionJson);
            } catch (IOException e)
            {
                LOGGER.error("version.json must be exclusively locked elsewhere, we can't remove it to put the new one in!", e);
                return false;
            }
        }
        DownloadUtils.downloadFile(versionJson, Constants.getCreeperhostModpackSearch2(_private, packType) + instance.getId() + "/" + instance.getVersionId());

        return (Files.exists(modpackJson) && Files.exists(versionJson));
    }

    public static FTBPack getPackFromAPI(long packId, long versionId, boolean _private, byte packType)
    {
        LOGGER.info("Getting pack from api.");
        String modpackURL = Constants.getCreeperhostModpackSearch2(_private, packType) + packId;
        String versionURL = modpackURL + "/" + versionId;
        String name = "";
        String version = "";
        List<String> authorList = new ArrayList<>();
        String description = "";
        String mc_version = "";
        String url = "";
        String arturl = "";
        int minMemory = 2048;
        int recMemory = 4096;
        long id = -1;
        List<SimpleDownloadableFile> downloadableFileList = new ArrayList<>();

        String resp = WebUtils.getAPIResponse(modpackURL);
        JsonElement jElement = new JsonParser().parse(resp);

        if (jElement.isJsonObject())
        {
            JsonObject object = jElement.getAsJsonObject();
            if(object.getAsJsonPrimitive("status").getAsString().equalsIgnoreCase("error"))
            {
                LOGGER.error("Unable to load modpack from '" + modpackURL + "'...");
                return null;
            }
            description = object.getAsJsonPrimitive("description").getAsString();
            name = object.getAsJsonPrimitive("name").getAsString();
            id = object.getAsJsonPrimitive("id").getAsLong();

            JsonArray authors = jElement.getAsJsonObject().getAsJsonArray("authors");

            if (authors != null)
            {
                for (JsonElement element : authors)
                {
                    JsonObject jsonObject = (JsonObject) element;
                    String authorName = jsonObject.get("name").getAsString();
                    authorList.add(authorName);
                }
            }
            JsonArray artwork = jElement.getAsJsonObject().getAsJsonArray("art");

            if (artwork != null)
            {
                for (JsonElement element : artwork)
                {
                    //TODO: better handling later plz, we can have more than one art.
                    JsonObject jsonObject = (JsonObject) element;
                    if (jsonObject.get("type").getAsString().equals("square"))
                    {
                        arturl = jsonObject.get("url").getAsString();
                        break;
                    }
                }
            }
        }

        String ver = WebUtils.getAPIResponse(versionURL);
        JsonElement jElement2 = new JsonParser().parse(ver);

        if (jElement2.isJsonObject())
        {
            JsonObject object = jElement2.getAsJsonObject();
            version = object.getAsJsonPrimitive("name").getAsString();
            if(object.has("specs") && object.get("specs").isJsonObject())
            {
                minMemory = object.getAsJsonObject("specs").getAsJsonPrimitive("minimum").getAsInt();
                recMemory = object.getAsJsonObject("specs").getAsJsonPrimitive("recommended").getAsInt();
            }
            else
            {
                minMemory = 4096;
                recMemory = 6132;
            }

            JsonArray targets = jElement.getAsJsonObject().getAsJsonArray("targets");

            if (targets != null)
            {
                for (JsonElement serverEl : targets)
                {
                    JsonObject server = (JsonObject) serverEl;
                    String targetVersion = server.get("version").toString();
                    String targetName = server.get("name").getAsString();
                    if (targetName.equalsIgnoreCase("minecraft"))
                    {
                        mc_version = targetVersion;
                    }
                }
            }
            JsonArray filesArray = object.getAsJsonArray("files");
            if (filesArray != null)
            {
                for (JsonElement serverEl : filesArray)
                {
                    JsonObject server = (JsonObject) serverEl;
                    String fileType = server.get("type").getAsString();
                    String fileName = server.get("name").getAsString();
                    String fileVersion = server.get("version").getAsString();
                    String path = server.get("path").getAsString();
                    long size = server.get("size").getAsInt();
                    boolean clientSideOnly = server.get("clientonly").getAsBoolean();
                    boolean optional = server.get("optional").getAsBoolean();
                    long fileId = server.get("id").getAsLong();
                    if(fileName == null) continue;
                    downloadableFileList.add(new SimpleDownloadableFile(fileVersion, Path.of(path).resolve(fileName), size, clientSideOnly, optional, fileId, fileName, fileType));
                }
            }
        }
        return new FTBPack(name, version, Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC).resolve("name"), authorList, description, mc_version, url, arturl, id, minMemory, recMemory, downloadableFileList);
    }

    public List<DownloadableFile> getModList(File target) {
        List<DownloadableFile> downloadableFileList = new ArrayList<>();
        JsonReader versionReader = null;
        try
        {
            versionReader = new JsonReader(new FileReader(target));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        JsonElement jElement = new JsonParser().parse(versionReader);
        if (jElement.isJsonObject())
        {
            JsonArray filesArray = jElement.getAsJsonObject().getAsJsonArray("files");

            if (filesArray != null)
            {
                for (JsonElement serverEl : filesArray)
                {
                    JsonObject server = (JsonObject) serverEl;
                    String fileName = server.get("name").getAsString();
                    String version = server.get("version").getAsString();
                    String path = server.get("path").getAsString();
                    String downloadUrl = server.get("url").getAsString().replaceAll(" ", "%20");
                    List<HashCode> sha1 = new ArrayList<>();
                    sha1.add(HashCode.fromString(server.get("sha1").getAsString()));
                    long size = server.get("size").getAsInt();
                    boolean clientSideOnly = server.get("clientonly").getAsBoolean();
                    boolean optional = server.get("optional").getAsBoolean();
                    long fileId = server.get("id").getAsLong();
                    String fileType = server.get("type").getAsString();
                    String updated = server.get("updated").getAsString();
                    downloadableFileList.add(new DownloadableFile(version, instance.getDir().resolve(path).resolve(fileName), downloadUrl, sha1, size, fileId, fileName, fileType, updated));
                }
            }
        }
        try {
            versionReader.close();
        } catch (IOException e) { e.printStackTrace(); }
        return downloadableFileList;
    }


    public List<DownloadableFile> getRequiredDownloads(Path target, Path forgeTarget) throws MalformedURLException
    {
        List<DownloadableFile> downloadableFileList = new ArrayList<>();
        JsonObject jElement = null;
        try (BufferedReader reader = Files.newBufferedReader(target))
        {
            jElement = gson.fromJson(reader, JsonObject.class);
        } catch (IOException ignored) {
        }

        if (jElement.isJsonObject())
        {
            JsonArray filesArray = jElement.getAsJsonObject().getAsJsonArray("files");

            if (filesArray != null)
            {
                for (JsonElement serverEl : filesArray)
                {
                    JsonObject server = (JsonObject) serverEl;
                    String fileName = server.get("name").getAsString();
                    String version = server.get("version").getAsString();
                    String path = server.get("path").getAsString();
                    String downloadUrl = server.get("url").getAsString().replaceAll(" ", "%20");
                    List<HashCode> sha1 = new ArrayList<>();
                    String sha1val = server.get("sha1").getAsString();
                    if(sha1val.length() > 2) sha1.add(HashCode.fromString(sha1val));
                    long size = server.get("size").getAsInt();
                    boolean clientSideOnly = server.get("clientonly").getAsBoolean();
                    boolean optional = server.get("optional").getAsBoolean();
                    long fileId = server.get("id").getAsLong();
                    String fileType = server.get("type").getAsString();
                    String updated = server.get("updated").getAsString();
                    downloadableFileList.add(new DownloadableFile(version, instance.getDir().resolve(path).resolve(fileName), downloadUrl, sha1, size, fileId, fileName, fileType, updated));
                }
            }
        }

        if (forgeTarget != null && !forgeTarget.toString().isEmpty())
        {
            JsonObject forgeElement = null;
            try (BufferedReader reader = Files.newBufferedReader(forgeTarget))
            {
                forgeElement = gson.fromJson(reader, JsonObject.class);
            } catch (IOException ignored) {
            }
            if (forgeElement.isJsonObject())
            {
                JsonArray targets = forgeElement.getAsJsonObject().getAsJsonArray("libraries");
                if (targets != null)
                {
                    for (JsonElement serverEl : targets)
                    {
                        JsonObject server = (JsonObject) serverEl;
                        String name = server.get("name").getAsString();
                        Artifact artifact = Artifact.from(name);
                        if (server.has("rules"))
                        {
                            for (JsonElement _rule : server.getAsJsonArray("rules"))
                            {
                                JsonObject rule = (JsonObject) _rule;
                                boolean allowed = false;
                                JsonObject _ruleTarget = null;
                                OS ruleTarget = null;
                                if (rule.has("action"))
                                {
                                    switch (rule.get("action").getAsString().toLowerCase())
                                    {
                                        case "accept":
                                        case "allow":
                                            if (rule.has("os"))
                                            {
                                                // May have more logic to put in these later
                                                allowed = true;
                                                _ruleTarget = rule.getAsJsonObject("os");
                                            }
                                            break;
                                        case "disallow":
                                        case "deny":
                                        case "yeet":
                                            if (rule.has("os"))
                                            {
                                                // May have more logic to put in these later
                                                allowed = false;
                                                _ruleTarget = rule.getAsJsonObject("os");
                                            }
                                            break;
                                    }

                                    if (_ruleTarget != null && _ruleTarget.has("name"))
                                    {
                                        switch (_ruleTarget.get("name").getAsString().toLowerCase())
                                        {
                                            case "osx":
                                                ruleTarget = OS.MAC;
                                                break;
                                            case "win":
                                                ruleTarget = OS.WIN;
                                                break;
                                            case "linux":
                                                ruleTarget = OS.LINUX;
                                                break;
                                        }
                                    }
                                }
                                if (ruleTarget == OS.CURRENT)
                                {
                                    Pattern versRegex = Pattern.compile(_ruleTarget.get("version").getAsString());
                                    Matcher versMatch = versRegex.matcher(OSUtils.getVersion());
                                    if (allowed)
                                    {
                                        if (!versMatch.matches()) continue;
                                    } else
                                    {
                                        if (versMatch.matches()) continue;
                                    }
                                }
                            }
                        }
                        String uri = "https://apps.modpacks.ch/versions/" + artifact.getPath();
                        Path localPath = artifact.getLocalPath(Constants.LIBRARY_LOCATION);
                        if (!ForgeUtils.isUrlValid(uri))
                        {
                            LOGGER.error("Not valid url {}", uri);
                        }
                        FileUtils.createDirectories(localPath.toAbsolutePath().getParent());
                        String version = "unknown";
                        String downloadUrl = uri;
                        JsonArray checksums = server.getAsJsonArray("checksums");
                        List<HashCode> sha1 = new ArrayList<>();
                        if (checksums != null)
                        {
                            for (JsonElement checksum : checksums)
                            {
                                sha1.add(HashCode.fromString(checksum.getAsString()));
                            }
                        }
                        long size = -1;
                        boolean clientSideOnly = (server.get("clientreq") != null) && server.get("clientreq").getAsBoolean();
                        boolean optional = false;
                        long fileId = -1;
                        String fileName = localPath.getFileName().toString();
                        String fileType = "library";
                        String updated = String.valueOf(System.currentTimeMillis() / 1000L);
                        downloadableFileList.add(new DownloadableFile(version, localPath, downloadUrl, sha1, size, fileId, fileName, fileType, updated));
                    }
                }
            }
        }
        return downloadableFileList;
    }

    void downloadFiles(Path instanceDir, Path forgeLibs)
    {
        LOGGER.info("Attempting to downloaded required files");

        ArrayList<CompletableFuture<?>> futures = new ArrayList<>();
        overallBytes.set(0);

        FTBModPackInstallerTask.currentBytes.set(0);

        List<DownloadableFile> requiredFiles = null;
        try
        {
            requiredFiles = getRequiredDownloads(instanceDir.resolve("version.json"), forgeLibs);
        } catch (MalformedURLException err)
        {
            err.printStackTrace();
            return;
        }
        //Need to loop first for overallBytes or things get weird.
        for (DownloadableFile file : requiredFiles)
        {
            Path path = file.getPath();
            if (!Files.exists(path))
            {
                if (file.getSize() > 0)
                {
                    overallBytes.addAndGet(file.getSize());
                }
            }
        }
        for (DownloadableFile file : requiredFiles)
        {
            FileUtils.createDirectories(file.getPath().toAbsolutePath().getParent());
            try
            {
                Path path = file.getPath();
                if (!Files.exists(path))
                {
                    DownloadTask task = new DownloadTask(file, path);//url, path, file.getSize(), false, file.getSha1() );
                    futures.add(task.execute());
                }
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            allFutures(futures).join();
        } catch (Throwable err)
        {
            for (CompletableFuture<?> ftr : futures)
            {
                ftr.cancel(true);
            }
            throw err;
        }
    }

    List<LoaderTarget> getTargets()
    {
        List<LoaderTarget> targetList = new ArrayList<>();
        try(BufferedReader reader = Files.newBufferedReader(instance.getDir().resolve("version.json")))
        {
            JsonObject jObject = GsonUtils.GSON.fromJson(reader, JsonObject.class);
            JsonArray targets = jObject.getAsJsonArray("targets");
            if (targets != null)
            {
                for (JsonElement serverEl : targets)
                {
                    JsonObject server = (JsonObject) serverEl;
                    String targetVersion = server.get("version").getAsString();
                    long targetId = server.get("id").getAsLong();
                    String targetName = server.get("name").getAsString();
                    String targetType = server.get("type").getAsString();

                    targetList.add(new LoaderTarget(targetName, targetVersion, targetId, targetType));
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return targetList;
    }

    String getMinecraftVersion()
    {
        for (LoaderTarget target : getTargets())
        {
            if (target.getName().equalsIgnoreCase("minecraft"))
            {
                return target.getVersion();
            }
        }
        return null;
    }

    String getForgeVersion()
    {
        for (LoaderTarget target : getTargets())
        {
            if (target.getName().equalsIgnoreCase("forge"))
            {
                String version = target.getVersion();
                if (version.contains("recommended"))
                {
                    version = ForgeUtils.getRecommended(getMinecraftVersion());
                    return version;
                }
                if (version.contains("latest"))
                {
                    version = ForgeUtils.getLatest(getMinecraftVersion());
                    return version;
                }
                return target.getVersion();
            }
        }
        return null;
    }

    public Path installModLoaders()
    {
        List<ModLoader> modLoaders = ModLoaderManager.getModLoaders(getTargets());
        if (modLoaders.size() > 1)
        {
            throw new RuntimeException("Only one mod loader is currently supported!");
        }
        else if(modLoaders.size() == 1)
        {
            ModLoader modLoader = modLoaders.get(0);
            return modLoader.install(instance);
        }
        return null;
    }
}
