package net.creeperhost.creeperlauncher.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import net.creeperhost.creeperlauncher.util.LoaderTarget;
import net.creeperhost.creeperlauncher.util.WebUtils;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

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
                return false;
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

    public static boolean clearProfiles(File target) {
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
                json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                return false;
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
                return false;
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
                return false;
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
            CreeperLogger.INSTANCE.error("There was a problem writing the launch profile, is it write protected?");
            return false;
        }
        return true;
    }

    public static void downloadVanillaLauncher() {
        String downloadurl = OSUtils.getMinecraftLauncherURL();
        File binfolder = new File(Constants.BIN_LOCATION);
        if(!binfolder.exists()) {
            if (!binfolder.mkdir()) {
                if(!binfolder.canWrite())
                {
                    OpenModalData.openModal("Error", "Cannot write to data directory, please check file permissions.", List.of(
                            new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                    ));
                    CreeperLogger.INSTANCE.error("Cannot write to data directory "+Constants.DATA_DIR+".");
                    return;
                } else {
                    OpenModalData.openModal("Error", "Data directory does not exist.", List.of(
                            new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                    ));
                    CreeperLogger.INSTANCE.error("Data directory " + Constants.DATA_DIR + " does not exist.");
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
            File destinationFile = new File(Constants.MINECRAFT_LAUNCHER_LOCATION);
            if(!destinationFile.canWrite())
            {
                OpenModalData.openModal("Error", "Unable to download Minecraft launcher due to file permissions.", List.of(
                        new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
                ));
                CreeperLogger.INSTANCE.error("Cannot write Minecraft launcher to data directory "+Constants.DATA_DIR+".");
            } else {
                DownloadTask task = new DownloadTask(remoteFile, destinationFile.toPath());
                task.execute().join();
                boolean osConfig = false;
                try {
                    osConfig = McUtils.prepareVanillaLauncher();
                } catch (Exception err) {
                    err.printStackTrace();
                }
                if (!osConfig) CreeperLogger.INSTANCE.error("Failed to configure Vanilla launcher for this OS!");
            }
        }
    }

    public static boolean prepareVanillaLauncher() throws IOException, InterruptedException {
        OS os = OSUtils.getOs();
        //All OS's are not equal, sometimes we need to unpackage the launcher.
        boolean success = false;
        switch (os) {
            case MAC:
                File installer = new File(Constants.MINECRAFT_LAUNCHER_LOCATION);
                String[] mcommand = {"/usr/bin/hdiutil", "attach", Constants.MINECRAFT_LAUNCHER_LOCATION};
                Process mount = Runtime.getRuntime().exec(mcommand);
                OutputStream stdout = mount.getOutputStream();
                while (mount.isAlive()) {
                    Thread.sleep(500);
                }
                if (mount.exitValue() == 0) {
                    String[] ccommand = {"/bin/cp", "-R", Constants.MINECRAFT_MAC_LAUNCHER_VOLUME + File.separator + "/Minecraft.app", Constants.BIN_LOCATION + File.separator};
                    Process copy = Runtime.getRuntime().exec(ccommand);
                    while (copy.isAlive()) {
                        Thread.sleep(500);
                    }
                    if (copy.exitValue() == 0) {
                        success = true;
                    }
                } else {
                    System.out.print(stdout);
                    CreeperLogger.INSTANCE.error("Failed to mount the Vanilla installer on MacOS.");
                    success = false;
                }
                String[] ucommand = {"/usr/bin/hdiutil", "unmount", Constants.MINECRAFT_MAC_LAUNCHER_VOLUME};
                Process unmount = Runtime.getRuntime().exec(ucommand);
                while (unmount.isAlive()) {
                    Thread.sleep(500);
                }
                if (unmount.exitValue() != 0) {
                    CreeperLogger.INSTANCE.error("Somehow failed to clean up after sorting the Vanilla launcher on MacOS.");
                } else {
                    installer.delete();
                }
                break;
            case LINUX:
                File installergzip = new File(Constants.MINECRAFT_LAUNCHER_LOCATION);
                File tar = new File(Constants.BIN_LOCATION + File.separator + "launcher.tar");
                FileUtils.unGzip(installergzip, tar);
                if (tar.exists()) {
                    try {
                        FileUtils.unTar(tar, new File(Constants.BIN_LOCATION));
                        FileUtils.setFilePermissions(new File(Constants.MINECRAFT_LINUX_LAUNCHER_EXECUTABLE));
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
        return targetList;
    }
}


