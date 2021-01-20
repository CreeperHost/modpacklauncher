package net.creeperhost.creeperlauncher.minetogether.vpn;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.creeperhost.creeperlauncher.util.WebUtils.mtAPIGet;

public class MineTogetherConnect {
    private static final Logger LOGGER = LogManager.getLogger();

    private boolean enabled;
    private String config;
    private String ipv6;
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
            this.enabled = false;
            return;
        }
        this.ipv6 = "2a04:de41:" + String.join(":", Constants.MT_HASH.substring(0,24).split("(?<=\\G....)"));
        Settings.loadSettings();
        this.enabled = (Settings.settings.getOrDefault("mtConnect", "false").equalsIgnoreCase("true"));
    }
    public String getIPv6()
    {
        if(!enabled) return "";
        return ipv6;
    }
    public boolean isEnabled()
    {
        return enabled;
    }
    public boolean isConnected()
    {
        return connected;
    }
    public void onConnect(Runnable lambda)
    {
        runConnected = lambda;
    }
    public void onDisconnect(Runnable lambda)
    {
        runDisconnected = lambda;
    }
    public boolean connect()
    {
        if(!enabled) return false;
        if(vpnProcess != null && vpnProcess.isAlive()) return false;
        List<String> executable = new ArrayList<>();
        OS os = OS.current();
        switch(os)
        {
            case WIN:
                //executable.add(System.getenv("WINDIR") + "\\system32\\rundll32.exe");
                //executable.add("url.dll,FileProtocolHandler");
                executable.add(System.getenv("WINDIR") + "\\system32\\cmd.exe");
                executable.add("/c");
                binary = "MineTogetherConnect.exe";
                executable.add(Constants.MTCONNECT_DIR.resolve(binary).toAbsolutePath().toString());
                break;
            default:
                LOGGER.warn("Unsupported operating system {}", os);
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
        if(Files.notExists(fullPath))
        {
            LOGGER.info("First run... Downloading binaries...");
            if(!download(fullPath)) return false;
        }
        Path config = Constants.MTCONNECT_DIR.resolve("MTConnect.ovpn");
        if(Files.exists(config) && !Files.isWritable(config))
        {
            LOGGER.error("Unable to write to '{}'...", config.toAbsolutePath());
            return false;
        }
        try(BufferedWriter fw = Files.newBufferedWriter(config)) {
            String sessionIdent = Settings.settings.get("sessionString");
            if(sessionIdent == null || sessionIdent.isEmpty()) return false;
            this.config = mtAPIGet("https://minetogether.io/api/mtConnect");
            if(this.config.equals("error") || this.config.length() == 0)
            {
                LOGGER.error("Unable to grab configuration file... Not high enough supporter tier?");
                return false;
            }
            fw.write(this.config);
        } catch (IOException e) {
            LOGGER.error("Unable to grab configuration file...", e);
            return false;
        }
        executable.add(config.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(executable);
        try {
            vpnProcess = pb.start();
        } catch (IOException e) {
            LOGGER.error("Unable to launch VPN elevation process...", e);
            return false;
        }
        //TODO: Add code to connect to named pipe, and get new process id, then replace vpnProcess with the handle of the new process. (Yay, elevation!)
        /*
        vpnProcess.onExit().thenRunAsync(() -> {
            try {
                config.delete();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        });*/
        //TODO: Logic to check if it is actually connected, ideally not reliant on the actual process... Perhaps we have a device on 2a04:de41::/32 that can always reply to pings.
        connected = true;
        if(runConnected != null) CompletableFuture.runAsync(runConnected);
        return true;
    }
    private static boolean download(Path path)
    {
        DownloadableFile remoteFile = new DownloadableFile("latest", path, "https://apps.modpacks.ch/MineTogether/MineTogetherConnect.exe", new ArrayList<>(), 0, false, false, 0, "MineTogetherConnect", "MineTogetherConnect", String.valueOf(System.currentTimeMillis() / 1000L));
        try {
            remoteFile.prepare();
            remoteFile.download(path, true, false);
        } catch(Throwable e)
        {
            LOGGER.error("Unable to grab binaries...", e);
            return false;
        }
        if(!Files.exists(path)) return false;
        return true;
    }
    public void disconnect()
    {
        if(!enabled) return;
        if(vpnProcess != null) vpnProcess.destroy();
        try {
            Path config = Constants.MTCONNECT_DIR.resolve("MTConnect.ovpn");
            //Act of removing this config shuts down the VPN
            Files.delete(config);
        } catch(Exception ignored) {}
        connected = false;
        if(runDisconnected != null) CompletableFuture.runAsync(runDisconnected);
    }
}
