package net.creeperhost.creeperlauncher.install.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.minecraft.EnumForgeInstallType;
import net.creeperhost.creeperlauncher.minecraft.GameLauncher;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.pack.FTBPack;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FTBModPackInstallerTask implements IInstallTask<Void>
{
    public static AtomicLong currentSpeed = new AtomicLong(0);
    public static AtomicLong averageSpeed = new AtomicLong(0);
    public static AtomicLong overallBytes = new AtomicLong(0);
    public static AtomicLong currentBytes = new AtomicLong(0);
    public static AtomicLong startTime = new AtomicLong(0);
    public static AtomicReference<String> lastError = new AtomicReference<String>();
    public String currentUUID = "";
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
        return currentTask = CompletableFuture.runAsync(() ->
        {
            currentStage = Stage.INIT;
            overallBytes.set(0);
            currentBytes.set(0);
            currentSpeed.set(0);
            startTime.set(System.currentTimeMillis());
            lastError.set("");
            CreeperLogger.INSTANCE.info(instance.getName() + " " + instance.getId() + " " + instance.getVersionId());
            File instanceRoot = new File(Constants.INSTANCES_FOLDER_LOC);
            instanceRoot.mkdir();
            currentStage = Stage.VANILLA;
            McUtils.downloadVanillaLauncher();
            File profileJson = new File(Constants.LAUNCHER_PROFILES_JSON);
            if (!profileJson.exists()) GameLauncher.launchGameAndClose();
            File instanceDir = new File(instance.getDir());
            instanceDir.mkdir();
            currentStage = Stage.API;
            downloadJsons(instanceDir);
            currentStage = Stage.FORGE;
            File forgeJson = installForge();
            currentStage = Stage.DOWNLOADS;
            downloadFiles(instanceDir, forgeJson);
            currentStage = Stage.POSTINSTALL;
        });
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

    public boolean downloadJsons(File instanceDir)
    {
        CreeperLogger.INSTANCE.info("Preparing instance folder for " + instanceDir.getAbsolutePath());
        if (!instanceDir.exists()) instanceDir.mkdir();

        File modpackJson = new File(instanceDir + File.separator + "modpack.json");
        if (modpackJson.exists()) modpackJson.delete(); //Need to remove and redownload this each time or updates will have old info
        DownloadUtils.downloadFile(modpackJson, Constants.CREEPERHOST_MODPACK_SEARCH2 + instance.getId());

        File versionJson = new File(instanceDir + File.separator + "version.json");
        if (versionJson.exists())
        {
            versionJson.delete(); //Need to remove and redownload this each time or updates will have old info
            if(versionJson.exists())
            {
                CreeperLogger.INSTANCE.error("version.json must be exclusively locked elsewhere, we can't remove it to put the new one in!");
                return false;
            }
        }
        DownloadUtils.downloadFile(versionJson, Constants.CREEPERHOST_MODPACK_SEARCH2 + instance.getId() + "/" + instance.getVersionId());

        return (modpackJson.exists() && versionJson.exists());
    }

    public static FTBPack getPackFromAPI(long packId, long versionId)
    {
        String modpackURL = Constants.CREEPERHOST_MODPACK_SEARCH2 + packId;
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

        String resp = WebUtils.getWebResponse(modpackURL);
        JsonElement jElement = new JsonParser().parse(resp);

        if (jElement.isJsonObject())
        {
            JsonObject object = jElement.getAsJsonObject();
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

        String ver = WebUtils.getWebResponse(versionURL);
        JsonElement jElement2 = new JsonParser().parse(ver);

        if (jElement2.isJsonObject())
        {
            JsonObject object = jElement2.getAsJsonObject();
            version = object.getAsJsonPrimitive("name").getAsString();
            minMemory = object.getAsJsonObject("specs").getAsJsonPrimitive("minimum").getAsInt();
            recMemory = object.getAsJsonObject("specs").getAsJsonPrimitive("recommended").getAsInt();

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
        }
        return new FTBPack(name, version, Constants.INSTANCES_FOLDER_LOC + File.separator + name, authorList, description, mc_version, url, arturl, id, minMemory, recMemory);
    }

    List<DownloadableFile> getRequiredDownloads(File target, File forgeTarget) throws MalformedURLException
    {
        List<DownloadableFile> downloadableFileList = new ArrayList<>();
        JsonReader versionReader = null;
        try
        {
            versionReader = new JsonReader(new FileReader(target));
        } catch (FileNotFoundException e)
        {
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
                    List<String> sha1 = new ArrayList<>();
                    sha1.add(server.get("sha1").getAsString());
                    long size = server.get("size").getAsInt();
                    boolean clientSideOnly = server.get("clientonly").getAsBoolean();
                    boolean optional = server.get("optional").getAsBoolean();
                    long fileId = server.get("id").getAsLong();
                    String fileType = server.get("type").getAsString();
                    String updated = server.get("updated").getAsString();
                    downloadableFileList.add(new DownloadableFile(version, instance.getDir() + File.separator + path + File.separator + fileName, downloadUrl, sha1, size, clientSideOnly, optional, fileId, fileName, fileType, updated));
                }
            }
        }
        if (forgeTarget != null)
        {
            JsonReader reader = null;
            try
            {
                reader = new JsonReader(new FileReader(forgeTarget));
            } catch (FileNotFoundException ignored)
            {
            }
            jElement = new JsonParser().parse(reader);
            if (jElement.isJsonObject())
            {
                JsonArray targets = jElement.getAsJsonObject().getAsJsonArray("libraries");
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
                                if (ruleTarget == OSUtils.getOs())
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
                        String uri = "https://dist.creeper.host/versions/" + artifact.getPath();
                        File localPath = artifact.getLocalPath(new File(Constants.LIBRARY_LOCATION));
                        if (!ForgeUtils.isUrlValid(uri))
                        {
                            CreeperLogger.INSTANCE.error("Not valid url " + uri);
                        }
                        File parent = localPath.getParentFile();
                        if (!parent.exists()) parent.mkdirs();
                        String version = "unknown";
                        String path = localPath.getPath();
                        String downloadUrl = uri;
                        JsonArray checksums = server.getAsJsonArray("checksums");
                        List<String> sha1 = new ArrayList<>();
                        if (checksums != null)
                        {
                            for (JsonElement checksum : checksums)
                            {
                                sha1.add(checksum.getAsString());
                            }
                        }
                        long size = -1;
                        boolean clientSideOnly = (server.get("clientreq") != null) && server.get("clientreq").getAsBoolean();
                        boolean optional = false;
                        long fileId = -1;
                        String fileName = localPath.getName();
                        String fileType = "library";
                        String updated = String.valueOf(System.currentTimeMillis() / 1000L);
                        downloadableFileList.add(new DownloadableFile(version, path, downloadUrl, sha1, size, clientSideOnly, optional, fileId, fileName, fileType, updated));
                    }
                }
            }
        }
        return downloadableFileList;
    }

    @SuppressWarnings("unchecked")
    void downloadFiles(File instanceDir, File forgeLibs)
    {
        CreeperLogger.INSTANCE.info("Attempting to downloaded required files");

        ArrayList<CompletableFuture> futures = new ArrayList<>();
        overallBytes.set(0);

        FTBModPackInstallerTask.currentBytes.set(0);

        List<DownloadableFile> requiredFiles = null;
        try
        {
            requiredFiles = getRequiredDownloads(new File(instanceDir + File.separator + "version.json"), forgeLibs);
        } catch (MalformedURLException err)
        {
            err.printStackTrace();
            return;
        }
        //Need to loop first for overallBytes or things get weird.
        for (DownloadableFile file : requiredFiles)
        {
            Path path = Paths.get(file.getPath());
            if (!path.toFile().exists())
            {
                if (file.getSize() > 0)
                {
                    overallBytes.addAndGet(file.getSize());
                }
            }
        }
        for (DownloadableFile file : requiredFiles)
        {
            File f = new File(instanceDir + File.separator + file.getPath());
            if (!f.exists()) f.mkdir();
            try
            {
                URI url = new URI(file.getUrl());
                Path path = Paths.get(file.getPath());
                if (!path.toFile().exists())
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

        } catch (Throwable err)
        {
            CreeperLogger.INSTANCE.error(err.getMessage());
            for (CompletableFuture ftr : futures)
            {
                ftr.cancel(true);
            }
            throw err;
        }
    }

    List<LoaderTarget> getTargets()
    {
        List<LoaderTarget> targetList = new ArrayList<>();
        JsonReader versionReader = null;
        try
        {
            versionReader = new JsonReader(new FileReader(new File(instance.getDir() + File.separator + "version.json")));
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }

        JsonElement jElement = new JsonParser().parse(versionReader);
        if (jElement.isJsonObject())
        {
            JsonArray targets = jElement.getAsJsonObject().getAsJsonArray("targets");
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

    public EnumForgeInstallType getInstallerType(String minecraftVersion)
    {
        for (String s : EnumForgeInstallType.INSTALLER.getVersions())
        {
            if (minecraftVersion.equalsIgnoreCase(s))
            {
                CreeperLogger.INSTANCE.info("Installer type found");
                return EnumForgeInstallType.INSTALLER;
            }
        }
        for (String s : EnumForgeInstallType.UNIVERSAL.getVersions())
        {
            if (minecraftVersion.equalsIgnoreCase(s))
            {
                CreeperLogger.INSTANCE.info("Installer type found");
                return EnumForgeInstallType.UNIVERSAL;
            }
        }
        for (String s : EnumForgeInstallType.JAR.getVersions())
        {
            if (minecraftVersion.equalsIgnoreCase(s))
            {
                CreeperLogger.INSTANCE.info("Installer type found");
                return EnumForgeInstallType.JAR;
            }
        }
        return null;
    }

    public File installForge()
    {
        File returnFile = null;
        EnumForgeInstallType installType = getInstallerType(getMinecraftVersion());

        switch (installType)
        {
            case INSTALLER:
            {
                String forgeUrl = "https://dist.creeper.host/versions/net/minecraftforge/forge/" + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.jar";
                String forgeUrlJson = "https://dist.creeper.host/versions/net/minecraftforge/forge/" + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.json";

                CreeperLogger.INSTANCE.info("Attempting to download " + forgeUrl);
                File installerFile = new File(instance.getDir() + File.separator + "installer.jar");
                File installerJson = new File(instance.getDir() + File.separator + "installer.json");

                DownloadUtils.downloadFile(installerFile, forgeUrl);
                DownloadUtils.downloadFile(installerJson, forgeUrlJson);

                if(!installerJson.exists())
                {
                    //If we do not have the file lets extract it from the installer jar
                    ForgeUtils.extractJson(installerFile.getAbsolutePath(), "installer.json");
                }

                instance.setPostInstall(() ->
                {
                    ForgeUtils.runForgeInstaller(installerFile.getAbsolutePath());
                    McUtils.removeProfile(new File(Constants.LAUNCHER_PROFILES_JSON), "forge");
                    installerFile.delete();
                }, false);

                instance.modLoader = getMinecraftVersion() + "-forge-" + getForgeVersion();
                try
                {
                    instance.saveJson();
                } catch (Exception ignored) {}
                return installerJson;
            }
            case UNIVERSAL:
            {
                String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();
                instance.modLoader = newname;
                CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion());
                File file = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname);
                file.mkdir();

                try
                {
                    URI url = ForgeUtils.findForgeDownloadURL(getMinecraftVersion(), getForgeVersion());
                    File forgeFile = new File(file.getAbsolutePath() + File.separator + newname + ".jar");
                    DownloadableFile forge = new DownloadableFile(getForgeVersion(), forgeFile.getAbsolutePath(), url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
                    DownloadTask task = new DownloadTask(forge, forgeFile.toPath());
                    task.execute().join();

                    CreeperLogger.INSTANCE.info("Completed download of " + newname);

                    if (forgeFile.exists())
                    {
                        ForgeUtils.extractJson(forgeFile.getAbsolutePath(), newname + ".json");
                        File forgeJson = new File(file.getAbsolutePath() + File.separator + newname + ".json");
                        if (forgeJson.exists())
                        {
                            ForgeUtils.updateForgeJson(forgeJson, newname, getMinecraftVersion());
                            //Move the forge jar to its home in libs
                            File libForgeDir = new File(Constants.LIBRARY_LOCATION + File.separator + "net" + File.separator + "minecraftforge" + File.separator + "forge" + File.separator + getMinecraftVersion() + "-" + getForgeVersion());
                            if (!libForgeDir.exists()) libForgeDir.mkdirs();
                            File forgeLib = new File(libForgeDir + File.separator + "forge-" + getMinecraftVersion() + "-" + getForgeVersion() + ".jar");
                            if (!forgeLib.exists())
                                Files.copy(forgeFile.toPath(), forgeLib.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            returnFile = forgeJson;
                        } else
                        {
                            CreeperLogger.INSTANCE.error("Failed to get the 'version.json' for '" + newname + "'");
                        }
                    }
                } catch (Throwable e)
                {
                    CreeperLogger.INSTANCE.error(e.toString());
                    e.printStackTrace();
                }
                try
                {
                    instance.saveJson();
                } catch (Exception ignored) {}
                return returnFile;
            }
            case JAR:
            {
                String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();
                CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion());
                File file = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname);
                file.mkdir();
                try
                {
                    URI url = null;
                    try
                    {
                        url = ForgeUtils.findForgeDownloadURL(getMinecraftVersion(), getForgeVersion());
                    } catch (URISyntaxException | MalformedURLException e)
                    {
                        e.printStackTrace();
                    }
                    File instMods = new File(instance.getDir() + File.separator + "instmods");
                    instMods.mkdir();

                    File forgeFile = new File(instMods.getAbsolutePath() + File.separator + newname + ".jar");
                    DownloadableFile forge = new DownloadableFile(getForgeVersion(), forgeFile.getAbsolutePath(), url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
                    DownloadTask task = new DownloadTask(forge, forgeFile.toPath());
                    task.execute();

                    File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");
                    DownloadableFile mc = McUtils.getMinecraftDownload(getMinecraftVersion(), instMods.getAbsolutePath());
                    DownloadTask mcTask = new DownloadTask(mc, mcFile.toPath());
                    mcTask.execute();

                    instance.setPreUninstall(() ->
                    {
                        File versionInstance = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + instance.getUuid());
                        if (versionInstance.exists()) FileUtils.deleteDirectory(versionInstance);
                    }, false);
                    instance.modLoader = newname;
                    try
                    {
                        instance.saveJson();
                    } catch (Exception e)
                    {
                        CreeperLogger.INSTANCE.error("Failed to save instance json");
                        CreeperLogger.INSTANCE.error(e.toString());
                    }
                } catch (Exception ignored) { }
            }
        }
        return returnFile;
    }
}
