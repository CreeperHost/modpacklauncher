package net.creeperhost.creeperlauncher.minecraft;

import com.google.gson.*;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class McUtils {

    private static final Gson gson = new Gson();

    public static String getMinecraftJsonForVersion(String version) {
        String url = Constants.MC_VERSION_MANIFEST;
        String resp = WebUtils.getAPIResponse(url);

        JsonElement jElement = new JsonParser().parse(resp);
        if (jElement.isJsonObject()) {
            JsonArray jsonArray = jElement.getAsJsonObject().getAsJsonArray("versions");

            if (jsonArray != null) {
                for (JsonElement serverEl : jsonArray) {
                    JsonObject server = (JsonObject) serverEl;
                    String name = server.get("id").getAsString();
                    String url1 = server.get("url").getAsString();

                    if (name.equalsIgnoreCase(version)) {
                        return url1;
                    }
                }
            }
        }
        return null;
    }

    public static DownloadableFile getMinecraftDownload(String version, Path downloadLoc) {
        String jsonURL = getMinecraftJsonForVersion(version);
        String resp = WebUtils.getWebResponse(jsonURL);

        JsonElement jElement = new JsonParser().parse(resp);
        if (jElement.isJsonObject()) {
            JsonObject jsonObject = jElement.getAsJsonObject().getAsJsonObject("downloads").getAsJsonObject("client");
            String sha1 = jsonObject.get("sha1").getAsString();
            long size = jsonObject.get("size").getAsLong();
            String URL = jsonObject.get("url").getAsString();

            List<String> sha1List = new ArrayList<>();
            sha1List.add(sha1);

            return new DownloadableFile(version, downloadLoc, URL, sha1List, size, false, false, 0, version, "", String.valueOf(System.currentTimeMillis() / 1000L));
        }
        return null;
    }

    public static boolean removeProfile(Path target, String profileID) {
        CreeperLogger.INSTANCE.info("Attempting to remove" + profileID);
        try {
            JsonObject json = null;
            try (BufferedReader reader = Files.newBufferedReader(target)) {
                json = gson.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                try {
                    URL url = new URL("https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                    URLConnection urlConnection = url.openConnection();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String buffer;
                    while((buffer = bufferedReader.readLine()) != null)
                    {
                        stringBuilder.append(buffer);
                    }
                    json = new JsonParser().parse(buffer).getAsJsonObject();
                } catch (Throwable t)
                {
                    e.printStackTrace();
                    return false;
                }
            }

            JsonObject _profiles = json.getAsJsonObject("profiles");
            if (_profiles == null) {
                _profiles = new JsonObject();
                json.add("profiles", _profiles);
            }

            JsonObject _profile = _profiles.getAsJsonObject(profileID);
            if (_profile != null) {
                _profiles.remove(profileID);
                String jstring = GsonUtils.GSON.toJson(json);
                Files.write(target, jstring.getBytes(StandardCharsets.UTF_8));
                CreeperLogger.INSTANCE.info("Removed profile " + profileID);
                return true;
            }
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    public static void verifyJson(Path target)
    {
        if(Files.exists(target))
        {
            try (BufferedReader reader = Files.newBufferedReader(target))
            {
                JsonObject json = null;
                try
                {
                    gson.fromJson(reader, JsonObject.class);
                }
                catch (JsonSyntaxException e)
                {
                    CreeperLogger.INSTANCE.error(e.getMessage());
                    //Json is malformed
                    Files.delete(target);
                    CreeperLogger.INSTANCE.info("launcher_profiles.json removed, Attempting to download new launcher_profiles.json");
                    DownloadUtils.downloadFile(target, "https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                }
            }
            catch (IOException e)
            {
                CreeperLogger.INSTANCE.error(e.getMessage());
            }
        }
    }

    public static boolean clearProfiles(Path target) {
        try {
            JsonObject json = null;
            try (BufferedReader reader = Files.newBufferedReader(target)) {
                json = gson.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                try
                {
                    URL url = new URL("https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                    URLConnection urlConnection = url.openConnection();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String buffer;
                    while((buffer = bufferedReader.readLine()) != null)
                    {
                        stringBuilder.append(buffer);
                    }
                    json = new JsonParser().parse(buffer).getAsJsonObject();
                } catch (Throwable t)
                {
                    CreeperLogger.INSTANCE.error(e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            json.remove("profiles");
            JsonObject _profiles = new JsonObject();
            json.add("profiles", _profiles);
            String jstring = GsonUtils.GSON.toJson(json);
            Files.write(target, jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    @Deprecated
    public static boolean updateProfileLastPlayed(Path target, String profileID, String time) {
        CreeperLogger.INSTANCE.info("Attempting to remove default forge profile");
        try {
            JsonObject json = null;
            try (InputStream stream = Files.newInputStream(target)) {
                json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                try {
                    URL url = new URL("https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                    URLConnection urlConnection = url.openConnection();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String buffer;
                    while((buffer = bufferedReader.readLine()) != null)
                    {
                        stringBuilder.append(buffer);
                    }
                    json = new JsonParser().parse(buffer).getAsJsonObject();
                } catch (Throwable t)
                {
                    e.printStackTrace();
                    return false;
                }
            }

            JsonObject _profiles = json.getAsJsonObject("profiles");
            if (_profiles == null) {
                _profiles = new JsonObject();
                json.add("profiles", _profiles);
            }

            JsonObject _profile = _profiles.getAsJsonObject(profileID);
            if (_profile != null) {
                _profile = new JsonObject();

                _profile.remove("lastUsed");
                _profile.addProperty("lastUsed", time);
                String jstring = GsonUtils.GSON.toJson(json);
                Files.write(target, jstring.getBytes(StandardCharsets.UTF_8));
                CreeperLogger.INSTANCE.info("Updated lastPlayed time for " + profileID);
                return true;
            }
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    public static boolean injectProfile(Path target, Profile profile, Path jrePath) {
        try {
            JsonObject json = null;
            try (BufferedReader reader = Files.newBufferedReader(target)) {
                json = gson.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                try {
                    URL url = new URL("https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                    URLConnection urlConnection = url.openConnection();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String buffer;
                    while((buffer = bufferedReader.readLine()) != null)
                    {
                        stringBuilder.append(buffer);
                    }
                    json = new JsonParser().parse(buffer).getAsJsonObject();
                } catch (Throwable t)
                {
                    e.printStackTrace();
                    return false;
                }
            }

            JsonObject _profiles = json.getAsJsonObject("profiles");
            if (_profiles == null) {
                _profiles = new JsonObject();
                json.add("profiles", _profiles);
            }

            if (_profiles.getAsJsonObject(profile.getID()) == null) {
                JsonObject jsonProfile = profile.toJsonObject();
                if (jrePath != null) {
                    jsonProfile.addProperty("javaDir", jrePath.toAbsolutePath().toString());
                }
                _profiles.add(profile.getID(), jsonProfile);
            }
            String jstring = GsonUtils.GSON.toJson(json);
            Files.write(target, jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if(!Files.isWritable(target))
            {
                CreeperLogger.INSTANCE.error(target.toAbsolutePath() + " is write protected to this process! Security configuration on this system is blocking access.");
                if(OSUtils.getOs() == OS.WIN)
                {
                    try {
                        CreeperLogger.INSTANCE.warning("=== Process list ===");
                        Process p = Runtime.getRuntime().exec("tasklist.exe /fo csv /nh");
                        BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String line;
                        while ((line = input.readLine()) != null) {
                            if (!line.trim().equals("")) {
                                line = line.substring(1);
                                CreeperLogger.INSTANCE.warning(line.substring(0, line.indexOf("\"")));
                            }
                        }
                        CreeperLogger.INSTANCE.warning("===================");
                    } catch(Throwable t)
                    {
                        CreeperLogger.INSTANCE.error(t.getMessage());
                    }
                }
            } else {
                CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            }
            return false;
        }
        return true;
    }

    public static void downloadVanillaLauncher() {
        downloadVanillaLauncher(Constants.BIN_LOCATION);
    }
    public static void downloadVanillaLauncher(Path binFolder) {
        CreeperLogger.INSTANCE.info("Downloading vanilla launcher.");
        String downloadurl = OSUtils.getMinecraftLauncherURL();
        FileUtils.createDirectories(binFolder);
        if (Files.notExists(binFolder)) {
            if(!Files.isWritable(binFolder))
            {
                CreeperLogger.INSTANCE.error("Cannot write to data directory "+Constants.getDataDir()+".");
                return;
            } else {
                OpenModalData.openModal("Error", "Data directory does not exist.", List.of(
                        new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                ));
                CreeperLogger.INSTANCE.error("Data directory " + Constants.getDataDir() + " does not exist.");
                return;
            }
        }
        Path file = binFolder.resolve(Constants.MINECRAFT_LAUNCHER_NAME);
        Path destinationFile = Constants.MINECRAFT_LAUNCHER_LOCATION;
        OS os = OSUtils.getOs();
        if (os == OS.MAC) {
            file = binFolder.resolve(Constants.MINECRAFT_MAC_LAUNCHER_EXECUTABLE_NAME);
        }
        if (Files.notExists(file)) {
            CreeperLogger.INSTANCE.info("Starting download of the vanilla launcher");
            DownloadableFile remoteFile = new DownloadableFile("official", destinationFile, downloadurl, new ArrayList<>(), 0, false, false, 0, "Vanilla", "vanilla", String.valueOf(System.currentTimeMillis() / 1000L));
            Path destinationDir = Constants.BIN_LOCATION;
            Path moveDestination = null;
            if(!Files.isWritable(destinationDir))
            {
                moveDestination = destinationFile;
                CreeperLogger.INSTANCE.error("Cannot write Minecraft launcher to data directory '"+Constants.getDataDir()+"', File '"+moveDestination.toAbsolutePath().toString()+"', trying temporary file '"+destinationFile.toAbsolutePath().toString()+".");
                try {
                    destinationFile = Files.createTempFile("launcher", null);
                } catch (IOException e) {
                    CreeperLogger.INSTANCE.error("Unable to create Temp file.", e);
                    return;
                }
            }
            DownloadTask task = new DownloadTask(remoteFile, destinationFile);
            task.execute().join();
            if(moveDestination != null)
            {
                try {
                    Files.move(destinationFile, moveDestination);
                } catch (IOException e) {
                    CreeperLogger.INSTANCE.error("Unable to move temporary file from '"+destinationFile.toAbsolutePath().toString()+"' to '"+moveDestination.toAbsolutePath().toString()+"'.");
                }
                destinationFile = moveDestination;
            }
            if(Files.notExists(destinationFile))
            {
                OpenModalData.openModal("Error", "Failed to download Mojang launcher.", List.of(
                        new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                ));
                return;
            }
        }
        if (Files.exists(destinationFile)) {
            boolean osConfig = false;
            try {
                osConfig = McUtils.prepareVanillaLauncher(destinationFile);
            } catch (Exception err) {
                err.printStackTrace();
            }
            if (!osConfig) CreeperLogger.INSTANCE.error("Failed to configure Vanilla launcher for this OS!");
        }
    }
    public static boolean prepareVanillaLauncher() throws IOException, InterruptedException {
        return prepareVanillaLauncher(Constants.MINECRAFT_LAUNCHER_LOCATION);
    }
    public static boolean prepareVanillaLauncher(Path path) throws IOException, InterruptedException {
        CreeperLogger.INSTANCE.info("Preparing Vanilla Launcher");
        OS os = OSUtils.getOs();
        //All OS's are not equal, sometimes we need to unpackage the launcher.
        boolean success = false;
        switch (os) {
            case MAC:
                if (Files.exists(path)) {
                    HashMap<String, Exception> errors = FileUtils.extractZip2ElectricBoogaloo(path, path.getParent());
                    if (!errors.isEmpty())
                    {
                        Set<String> strings = errors.keySet();
                        StringBuilder builder = new StringBuilder();
                        strings.forEach((str) -> builder.append(str).append("\n"));
                        CreeperLogger.INSTANCE.error("Errors extracting these files from zip: \n" + builder.toString());
                        success = false;
                    }
                    Files.deleteIfExists(path);
                    String[] executableFiles = new String[] {"Minecraft.app/Contents/MacOS/launcher", "Minecraft.app/Contents/Minecraft Updater.app/Contents/MacOS/nativeUpdater"};
                    for(String filePath: executableFiles) {
                        Path executableFilePath = path.getParent().resolve(filePath);
                        boolean b = executableFilePath.toFile().setExecutable(true);
                        if (!b) CreeperLogger.INSTANCE.warning("Unable to set \"" + executableFilePath + "\" to executable");
                    }
                    success = true;
                } else {
                    CreeperLogger.INSTANCE.error("Launcher does not exist at '"+(path)+"'...");
                    success = false;
                }
                break;
            case LINUX:
                Path installergzip = Constants.MINECRAFT_LAUNCHER_LOCATION;
                if (Files.exists(installergzip)) {
                    try {
                        FileUtils.unTar(new GzipCompressorInputStream(Files.newInputStream(installergzip)), Constants.BIN_LOCATION);
                        FileUtils.setFilePermissions(Constants.MINECRAFT_LINUX_LAUNCHER_EXECUTABLE);
                        Files.delete(installergzip);
                        success = true;
                    } catch (Exception e) {
                        CreeperLogger.INSTANCE.error("Failed to extract tarball " + installergzip, e);
                        success = false;
                    }
                }
                break;
            default:
                success = true;
        }
        return success;
    }

    public static int parseMinorVersion(String minecraftVersion) {
        String[] split = minecraftVersion.split("\\.");
        if (split.length >= 2) {
            return Integer.parseInt(split[1]);
        }
        return -1;
    }

    //Could be any modloader
    public static List<LoaderTarget> getTargets(Path instanceDir) {
        List<LoaderTarget> targetList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(instanceDir.resolve("version.json"))) {
            JsonObject obj = GsonUtils.GSON.fromJson(reader, JsonObject.class);
            JsonArray targets = obj.getAsJsonArray("targets");
            if (targets != null) {
                for (JsonElement serverEl : targets) {
                    JsonObject server = (JsonObject) serverEl;
                    String targetVersion = server.get("version").getAsString();
                    long targetId = server.get("id").getAsLong();
                    String targetName = server.get("name").getAsString();
                    String targetType = server.get("type").getAsString();

                    targetList.add(new LoaderTarget(targetName, targetVersion, targetId, targetType));
                }
            }
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("Failed to load version json.", e);
        }
        return targetList;
    }
}


