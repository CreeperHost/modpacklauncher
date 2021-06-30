package net.creeperhost.minetogether.lib.vpn;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import net.covers1624.quack.util.HashUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MineTogetherConnect {
    private static final Logger LOGGER = LogManager.getLogger();

    private Process vpnProcess;
    private boolean connected;
    private Runnable runConnected;
    private Runnable runDisconnected;
    private String binary = "";
    public MineTogetherConnect()
    {
        if(Constants.MT_HASH == null || Constants.MT_HASH.isEmpty())
        {
            LOGGER.info("Tried to initialize MineTogether Connect before storing MineTogether identifier...");
            return;
        }
        // For now, if logged in as the front end checks plan type before enabling UI
        //this.enabled = !Settings.settings.getOrDefault("sessionString", "").isEmpty();
        //Settings.settings.put("mtConnect", "true");
        //Settings.saveSettings();
        // Update it in settings so it at least shows as on. TODO: Remove when front end has proper support for checking the enabled features rather than plan
    }
    public boolean isEnabled()
    {
        return Constants.MT_HASH != null && !Constants.MT_HASH.isEmpty() && Settings.settings.getOrDefault("mtConnect", "false").equalsIgnoreCase("true");
    }
    public boolean isConnected()
    {
        return connected;
    }
    public boolean connect()
    {
        if(!isEnabled()) return false;
        if(vpnProcess != null && vpnProcess.isAlive()) return false;
        List<String> executable = new ArrayList<>();
        switch(OS.CURRENT)
        {
            case WIN:
                binary = "MineTogetherConnect.exe";
                executable.add(Constants.MTCONNECT_DIR.resolve(binary).toAbsolutePath().toString());
                break;
            default:
                LOGGER.warn("Unsupported operating system {}", OS.CURRENT);
                break;
        }
        if(executable.size() == 0 || binary.isEmpty()) return false;
        Path primaryPath = Constants.MTCONNECT_DIR;
        FileUtils.createDirectories(primaryPath);
        if(!Files.isWritable(primaryPath))
        {
            LOGGER.error("Unable to write to '{}'...", primaryPath.toAbsolutePath());
            return false;
        }
        Path fullPath = Constants.MTCONNECT_DIR.resolve(binary);
        if(!download(fullPath)) return false;

        String sessionIdent = Settings.settings.get("sessionString");
        if(sessionIdent == null || sessionIdent.isEmpty()) {
            LOGGER.error("Unable to launch MineTogether Connect as not logged in...");
            return false;
        }
        executable.add(sessionIdent);
        //if(Settings.settings.getOrDefault("verbose", "false").equals("true")) {
        if (true) { // whilst during open test, always
            executable.add("true"); // enable debug output
        }
        ProcessBuilder pb = new ProcessBuilder(executable);
        try {
            vpnProcess = pb.start();
        } catch (IOException e) {
            LOGGER.error("Unable to launch MineTogether Connect...", e);
            return false;
        }
        CompletableFuture<Void> stdoutFuture = StreamGobblerLog.redirectToLogger(vpnProcess.getInputStream(), LOGGER::info);
        CompletableFuture<Void> stderrFuture = StreamGobblerLog.redirectToLogger(vpnProcess.getErrorStream(), LOGGER::error);
        vpnProcess.onExit().thenRunAsync(() -> {
            if (!stdoutFuture.isDone()) {
                stdoutFuture.cancel(true);
            }
            if (!stderrFuture.isDone()) {
                stderrFuture.cancel(true);
            }
        });
        //TODO: Logic to check if it is actually connected, ideally not reliant on the actual process... we can wait for "Connected to Java Process" on the STDOUT maybe, instead of passing straight to LOGGER::info?
        connected = true;
        return true;
    }
    public void logExaminer(String line) {
        if (connected) return;
        LOGGER.info(line);
        if (line.contains("Connection received from Java")) {
            connected = true;
            if(runConnected != null) CompletableFuture.runAsync(runConnected);
        }
    }
    private static boolean download(Path path)
    {
        HashCode fileHash = null;
        if (!Files.notExists(path)) {
            try {
                fileHash = HashUtils.hash(Hashing.sha256(), path);
                String hashString = fileHash.toString();
            } catch (IOException e) {
            }
        }

        HashCode webHash = null;
        boolean download = true;
        if (fileHash != null) {
            try {
                download = false; // if this fails for some reason, we want to not download... we have no proper hash to see if changed, and the file exists by this point
                String hashString = DownloadUtils.urlToString(new URL("https://apps.modpacks.ch/MineTogether/GoNATProxyClient.exe.sha256")).trim();
                webHash = HashCode.fromString(hashString);
                if (!HashUtils.equals(fileHash, hashString)) download = true;
            } catch (IOException e) {
            }
        }

        if (download) {
            LOGGER.info("Downloading MineTogether Connect as hash has changed or doesn't exist");
            DownloadableFile remoteFile = new DownloadableFile("latest", path, "https://apps.modpacks.ch/MineTogether/GoNATProxyClient.exe", webHash == null ? Collections.emptyList() : Collections.singletonList(webHash), 0, 0, "MineTogetherConnect", "MineTogetherConnect", String.valueOf(System.currentTimeMillis() / 1000L));
            try {
                remoteFile.prepare();
                remoteFile.download(path, true, false, null);
            } catch (Throwable e) {
                LOGGER.error("Unable to grab binaries...", e);
                return false;
            }
            if (!Files.exists(path)) return false;
            return true;
        }
        return true;
    }
    public void disconnect()
    {
        if(!isEnabled()) return;
        if (connected) {
            // If we are connected, it will close itself on game close - we don't want to end it early when the FTB Launcher closes
            LOGGER.info("Not closing MineTogether Connect as might be in use by the game");
        }
        if(vpnProcess != null) vpnProcess.destroy();
        connected = false;
        if(runDisconnected != null) CompletableFuture.runAsync(runDisconnected);
    }
}
