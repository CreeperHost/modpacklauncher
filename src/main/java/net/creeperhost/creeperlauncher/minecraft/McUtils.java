package net.creeperhost.creeperlauncher.minecraft;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
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
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class McUtils {
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

    public static DownloadableFile getMinecraftDownload(String version, String downloadLoc) {
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

    public static boolean removeProfile(File target, String profileID) {
        CreeperLogger.INSTANCE.info("Attempting to remove" + profileID);
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
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
                _profiles.remove(profileID);
                String jstring = GsonUtils.GSON.toJson(json);
                Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
                CreeperLogger.INSTANCE.info("Removed profile " + profileID);
                return true;
            }
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    public static void verifyJson(File target)
    {
        if(target.exists())
        {
            try (InputStream stream = new FileInputStream(target))
            {
                JsonObject json = null;
                try
                {
                    json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                }
                catch (JsonSyntaxException e)
                {
                    stream.close();
                    CreeperLogger.INSTANCE.error(e.getMessage());
                    //Json is malformed
                    if(target.delete())
                    {
                        CreeperLogger.INSTANCE.info("launcher_profiles.json removed, Attempting to download new launcher_profiles.json");
                        DownloadUtils.downloadFile(target, "https://apps.modpacks.ch/FTB2/launcher_profiles.json");
                    }
                }
            }
            catch (IOException e)
            {
                CreeperLogger.INSTANCE.error(e.getMessage());
            }
        }
    }

    public static boolean clearProfiles(File target) {
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
                json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
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
            Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    @Deprecated
    public static boolean updateProfileLastPlayed(File target, String profileID, String time) {
        CreeperLogger.INSTANCE.info("Attempting to remove default forge profile");
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
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
                Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
                CreeperLogger.INSTANCE.info("Updated lastPlayed time for " + profileID);
                return true;
            }
        } catch (IOException e) {
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return false;
    }

    public static boolean injectProfile(File target, Profile profile, String jrePath) {
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
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

            if (_profiles.getAsJsonObject(profile.getID()) == null) {
                JsonObject jsonProfile = profile.toJsonObject();
                if (jrePath != null && jrePath.length() > 0) {
                    jsonProfile.addProperty("javaDir", jrePath);
                }
                _profiles.add(profile.getID(), jsonProfile);
            }
            String jstring = GsonUtils.GSON.toJson(json);
            Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if(!target.canWrite())
            {
                CreeperLogger.INSTANCE.error(target.getAbsolutePath() + " is write protected to this process! Security configuration on this system is blocking access.");
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
    public static void downloadVanillaLauncher()
    {
        downloadVanillaLauncher(Constants.BIN_LOCATION);
    }
    public static void downloadVanillaLauncher(String destination) {
        String downloadurl = OSUtils.getMinecraftLauncherURL();
        File binfolder = new File(destination);
        File tempFolder = new File(System.getProperty("java.io.tmpdir"));
        if(!binfolder.exists()) {
            if (!binfolder.mkdir()) {
                if(!binfolder.canWrite())
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
        }
        File file = new File(Constants.MINECRAFT_LAUNCHER_LOCATION);
        OS os = OSUtils.getOs();
        if (os == OS.MAC) {
            file = new File(Constants.MINECRAFT_MAC_LAUNCHER_EXECUTABLE);
        }
        if (!file.exists()) {
            CreeperLogger.INSTANCE.info("Starting download of the vanilla launcher");
            DownloadableFile remoteFile = new DownloadableFile("official", "/", downloadurl, new ArrayList<>(), 0, false, false, 0, "Vanilla", "vanilla", String.valueOf(System.currentTimeMillis() / 1000L));

            File destinationDir = new File(destination);
            File destinationFile = new File(destinationDir + File.separator + Constants.MINECRAFT_LAUNCHER_NAME);
            File moveDestination = null;
            if(!destinationDir.canWrite())
            {
                moveDestination = destinationFile;
                destinationFile = new File(tempFolder, UUID.randomUUID().toString());
                CreeperLogger.INSTANCE.error("Cannot write Minecraft launcher to data directory '"+Constants.getDataDir()+"', File '"+moveDestination.getAbsolutePath().toString()+"', trying temporary file '"+destinationFile.getAbsolutePath().toString()+".");
            }
            DownloadTask task = new DownloadTask(remoteFile, destinationFile.toPath());
            task.execute().join();
            if(moveDestination != null)
            {
                if(!destinationFile.renameTo(moveDestination))
                {
                    CreeperLogger.INSTANCE.error("Unable to move temporary file from '"+destinationFile.getAbsolutePath().toString()+"' to '"+moveDestination.getAbsolutePath().toString()+"'.");
                }
                destinationFile = moveDestination;
            }
            if(!destinationFile.exists())
            {
                OpenModalData.openModal("Error", "Failed to download Mojang launcher.", List.of(
                        new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                ));
                return;
            }
            boolean osConfig = false;
            try {
                osConfig = McUtils.prepareVanillaLauncher(destinationFile.toString());
            } catch (Exception err) {
                err.printStackTrace();
            }
            if (!osConfig) CreeperLogger.INSTANCE.error("Failed to configure Vanilla launcher for this OS!");
        }
    }
    public static boolean prepareVanillaLauncher() throws IOException, InterruptedException {
        return prepareVanillaLauncher(Constants.MINECRAFT_LAUNCHER_LOCATION);
    }
    public static boolean prepareVanillaLauncher(String path) throws IOException, InterruptedException {
        OS os = OSUtils.getOs();
        //All OS's are not equal, sometimes we need to unpackage the launcher.
        boolean success = false;
        switch (os) {
            case MAC:
                File launcherFile = new File(path);
                if(launcherFile.exists()) {
                    ZipFile zipFile = new ZipFile(launcherFile);
                    zipFile.stream().map(ZipEntry::getName).forEach((ze) -> {
                        CreeperLogger.INSTANCE.warning("Extracting '"+ze+"'...");
                        ZipEntry entry = zipFile.getEntry(ze);
                        try {
                            InputStream inputStream = zipFile.getInputStream(entry);
                            byte[] bytes = inputStream.readAllBytes();
                            CreeperLogger.INSTANCE.warning("Writing to "+Path.of(Path.of(path).getParent().toString() + File.separator + entry.getName()).toString());
                            Files.write(Path.of(Path.of(path).getParent().toString() + File.separator + entry.getName()), bytes);
                        } catch (Exception e) {
                            CreeperLogger.INSTANCE.error("Failed extracting mac Mojang launcher!", e);
                        }

                    });
                } else {
                    CreeperLogger.INSTANCE.error("Launcher does not exist at '"+(path)+"'...");
                }
                break;
            /*case MAC:
                File installer = new File(path);
                String[] mcommand = {"/usr/bin/hdiutil", "attach", path + File.separator + "launcher.dmg"};
                CreeperLogger.INSTANCE.info("Mounting "+path + File.separator+"launcher.dmg");
                CreeperLogger.INSTANCE.info(String.join(" ", mcommand));
                Process mount = Runtime.getRuntime().exec(mcommand);
                InputStream stdout = mount.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
                String str;
                while ((str = br.readLine()) != null) {
                    CreeperLogger.INSTANCE.info(str);
                }
                int loop = 0;
                while (mount.isAlive() && loop < 5000) {
                    Thread.sleep(500);
                    loop++;
                }

                if (mount.exitValue() == 0) {
                    try {
                        FileUtils.copyDirectory(Path.of(Constants.MINECRAFT_MAC_LAUNCHER_VOLUME + File.separator), Path.of(path));
                        success = true;
                    } catch(Exception er)
                    {
                        CreeperLogger.INSTANCE.error("Error extracting Mojang launcher!", er);
                        success=false;
                    }
                    if(!success) {
                        String[] ccommand = {"/bin/cp", "-R", Constants.MINECRAFT_MAC_LAUNCHER_VOLUME + File.separator + "/Minecraft.app", Constants.BIN_LOCATION + File.separator};
                        Process copy = Runtime.getRuntime().exec(ccommand);
                        stdout = copy.getInputStream();
                        br = new BufferedReader(new InputStreamReader(stdout));
                        while ((str = br.readLine()) != null) {
                            CreeperLogger.INSTANCE.info(str);
                        }
                        loop = 0;
                        while (copy.isAlive() && loop < 30000) {
                            Thread.sleep(500);
                            loop++;
                        }
                        if (copy.exitValue() == 0) {
                            success = true;
                        }
                    }
                } else {
                    System.out.print(stdout);
                    CreeperLogger.INSTANCE.error("Failed to mount the Vanilla installer on MacOS.");
                    success = false;
                }
                String[] ucommand = {"/usr/bin/hdiutil", "unmount", Constants.MINECRAFT_MAC_LAUNCHER_VOLUME};
                CreeperLogger.INSTANCE.info(String.join(" ", ucommand));
                Process unmount = Runtime.getRuntime().exec(ucommand);
                loop = 0;
                while (unmount.isAlive() && loop < 5000) {
                    Thread.sleep(500);
                    loop++;
                }
                if (unmount.exitValue() != 0) {
                    CreeperLogger.INSTANCE.error("Somehow failed to clean up after sorting the Vanilla launcher on MacOS.");
                } else {
                    installer.delete();
                }
                break;*/
            case LINUX:
                File installergzip = new File(path);
                File tar = new File(Path.of(path).getParent().toString() + File.separator + "launcher.tar");
                FileUtils.unGzip(installergzip, tar);
                if (tar.exists()) {
                    try {
                        FileUtils.unTar(tar, new File(path));
                        FileUtils.setFilePermissions(new File(Path.of(path).getParent().toString() + File.separator + Constants.MINECRAFT_LAUNCHER_NAME));
                        installergzip.delete();
                        success = true;
                    } catch (ArchiveException e) {
                        e.printStackTrace();
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
    public static List<LoaderTarget> getTargets(String instanceDir) {
        List<LoaderTarget> targetList = new ArrayList<>();
        JsonReader versionReader = null;
        try {
            versionReader = new JsonReader(new FileReader(new File(instanceDir + File.separator + "version.json")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        JsonElement jElement = new JsonParser().parse(versionReader);
        if (jElement.isJsonObject()) {
            JsonArray targets = jElement.getAsJsonObject().getAsJsonArray("targets");
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
        }
        try
        {
            versionReader.close();
        } catch (IOException e) { e.printStackTrace(); }
        return targetList;
    }
}


