package net.creeperhost.creeperlauncher.minetogether.vpn;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.os.OSUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static net.creeperhost.creeperlauncher.util.WebUtils.mtAPIGet;

public class MineTogetherConnect {
    private static boolean enabled;
    private static String config;
    private static String ipv6;
    private static Process vpnProcess;
    private static boolean connected;
    private static Runnable runConnected;
    private static Runnable runDisconnected;
    void MineTogetherConnect()
    {
        if(Constants.MT_HASH.isEmpty()||Constants.MT_CONNECT_CONFIG.isEmpty())
        {
            enabled = false;
            return;
        }
        this.ipv6 = "2a04:de41:" + String.join(":", Constants.MT_HASH.substring(0,24).split("(?<=\\G....)"));
        this.config = Constants.MT_CONNECT_CONFIG;
        this.enabled = (Settings.settings.getOrDefault("mtConnect", "false") == "true");
    }
    public static String getIPv6()
    {
        if(!enabled) return "";
        return ipv6;
    }
    public static boolean isEnabled()
    {
        return enabled;
    }
    public static boolean isConnected()
    {
        return connected;
    }
    public static void onConnect(Runnable lambda)
    {
        runConnected = lambda;
    }
    public static void onDisconnect(Runnable lambda)
    {
        runDisconnected = lambda;
    }
    public static boolean connect()
    {
        if(!enabled) return false;
        if(vpnProcess != null || vpnProcess.isAlive()) return false;
        String executable = "";
        switch(OSUtils.getOs())
        {
            case WIN:
                executable = "MineTogetherConnect.exe";
                break;
        }
        if(executable.isEmpty()) return false;
        File fullPath = new File(Constants.BIN_LOCATION + executable);
        if(!fullPath.exists())
        {
            if(!download(fullPath)) return false;
        }
        File config = new File(Constants.BIN_LOCATION + "MTConnect.ovpn");
        if(!config.canWrite()) return false;
        try {
            FileWriter fw = new FileWriter(config);
            String sessionIdent = Settings.settings.get("sessionString");
            if(sessionIdent == null || sessionIdent.isEmpty()) return false;
            Constants.MT_CONNECT_CONFIG = mtAPIGet("https://minetogether.io/api/mtConnect");
            fw.write(Constants.MT_CONNECT_CONFIG);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        ProcessBuilder pb = new ProcessBuilder(fullPath.getAbsolutePath().toString(), "MTConnect.ovpn");
        try {
            vpnProcess = pb.start();
        } catch (IOException e) {
            e.printStackTrace();
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
    private static boolean download(File path)
    {
        //TODO: Set proper download path
        DownloadableFile remoteFile = new DownloadableFile("latest", "/", "http://transfer.ch.tools/v2Dxc/MineTogetherConnect.exe", new ArrayList<>(), 0, false, false, 0, "MineTogetherConnect", "MineTogetherConnect", String.valueOf(System.currentTimeMillis() / 1000L));
        try {
            remoteFile.download(path.getAbsoluteFile().toPath(), true, false);
        } catch(Throwable e)
        {
            e.printStackTrace();
            return false;
        }
        if(!path.exists()) return false;
        return true;
    }
    public static void disconnect()
    {
        if(!enabled) return;
        vpnProcess.destroy();
        try {
            File config = new File(Constants.BIN_LOCATION + "MTConnect.ovpn");
            config.delete();
        } catch(Exception ignored) {}
        connected = false;
        if(runDisconnected != null) CompletableFuture.runAsync(runDisconnected);
    }
}
