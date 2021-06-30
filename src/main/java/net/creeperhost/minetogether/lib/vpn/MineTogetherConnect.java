package net.creeperhost.minetogether.lib.vpn;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import net.covers1624.quack.util.HashUtils;
import net.creeperhost.minetogether.lib.util.BasicOS;
import net.creeperhost.minetogether.lib.util.FileUtils;
import net.creeperhost.minetogether.lib.util.StreamGobblerLog;
import net.creeperhost.minetogether.lib.util.WebUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MineTogetherConnect {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Supplier<Boolean> enabledFunc;

    private Process vpnProcess;
    private boolean connected;
    private Runnable runConnected;
    private Runnable runDisconnected;
    private String binary = "";
    private final String mthash;
    private final Path mtconnectDir;
    private final String sessionString;
    public MineTogetherConnect(String mthash, Supplier<Boolean> enabledFunc, Path mtconnectDir, String sessionString)
    {
        this.enabledFunc = enabledFunc;
        this.mthash = mthash;
        this.mtconnectDir = mtconnectDir;
        this.sessionString = sessionString;
        if(mthash == null || mthash.isEmpty())
        {
            LOGGER.info("Tried to initialize MineTogether Connect before storing MineTogether identifier...");
        }
    }
    public boolean isEnabled()
    {
        return mthash != null && !mthash.isEmpty() && enabledFunc.get();
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
        switch(BasicOS.CURRENT)
        {
            case WIN:
                binary = "MineTogetherConnect.exe";
                executable.add(mtconnectDir.resolve(binary).toAbsolutePath().toString());
                break;
            default:
                LOGGER.warn("Unsupported operating system {}", BasicOS.CURRENT);
                break;
        }
        if(executable.size() == 0 || binary.isEmpty()) return false;
        Path primaryPath = mtconnectDir;
        FileUtils.createDirectories(primaryPath);
        if(!Files.isWritable(primaryPath))
        {
            LOGGER.error("Unable to write to '{}'...", primaryPath.toAbsolutePath());
            return false;
        }
        Path fullPath = mtconnectDir.resolve(binary);
        if(!download(fullPath)) return false;

        String sessionIdent = sessionString;
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
        CompletableFuture<Void> stdoutFuture = StreamGobblerLog.redirectToLogger(vpnProcess.getInputStream(), getlogExaminer(LOGGER::info));
        CompletableFuture<Void> stderrFuture = StreamGobblerLog.redirectToLogger(vpnProcess.getErrorStream(), getlogExaminer(LOGGER::error));
        vpnProcess.onExit().thenRunAsync(() -> {
            if (!stdoutFuture.isDone()) {
                stdoutFuture.cancel(true);
            }
            if (!stderrFuture.isDone()) {
                stderrFuture.cancel(true);
            }
        });
        //TODO: Logic to check if it is actually connected, ideally not reliant on the actual process... we can wait for "Connected to Java Process" on the STDOUT maybe, instead of passing straight to LOGGER::info?
        return true;
    }
    public Consumer<String> getlogExaminer(Consumer<String> logger) {
        return (line) -> {
            logger.accept(line);
            if (connected) return;
            if (line.contains("Connection received from Java")) {
                connected = true;
                if (runConnected != null) CompletableFuture.runAsync(runConnected);
            }
        };
    }
    private static boolean download(Path path)
    {
        HashCode fileHash = null;
        if (!Files.notExists(path)) {
            try {
                fileHash = HashUtils.hash(Hashing.sha256(), path);
            } catch (IOException e) {
            }
        }

        boolean download = true;
        if (fileHash != null) {
            download = false; // if this fails for some reason, we want to not download... we have no proper hash to see if changed, and the file exists by this point
            String hashString = WebUtils.getWebResponse("https://apps.modpacks.ch/MineTogether/GoNATProxyClient.exe.sha256").trim();
            if (!HashUtils.equals(fileHash, hashString)) download = true;
        }

        if (download) {
            LOGGER.info("Downloading MineTogether Connect as hash has changed or doesn't exist");

            try {
                WebUtils.downloadFile("https://apps.modpacks.ch/MineTogether/GoNATProxyClient.exe", path);
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
