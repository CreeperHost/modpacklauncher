package net.creeperhost.creeperlauncher.minecraft;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class GameLauncher
{
    public void launchGame()
    {
        CompletableFuture.runAsync(() ->
        {
            String exe = Constants.MINECRAFT_LAUNCHER_LOCATION;
            OS os = OSUtils.getOs();
            if (os == OS.MAC)
            {
                exe = Constants.MINECRAFT_MAC_LAUNCHER_EXECUTABLE;
            }
            if (os == OS.LINUX)
            {
                exe = Constants.MINECRAFT_LINUX_LAUNCHER_EXECUTABLE;
            }
            try
            {
                new ProcessBuilder(exe, "--workDir", Constants.BIN_LOCATION).start();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }).join();
    }

    public static void launchGameAndClose()
    {
        CompletableFuture.runAsync(() ->
        {
            String exe = Constants.MINECRAFT_LAUNCHER_LOCATION;
            OS os = OSUtils.getOs();
            if (os == OS.MAC)
            {
                exe = Constants.MINECRAFT_MAC_LAUNCHER_EXECUTABLE;
            }
            if (os == OS.LINUX)
            {
                exe = Constants.MINECRAFT_LINUX_LAUNCHER_EXECUTABLE;
            }
            try
            {
                Process process = new ProcessBuilder(exe, "--workDir", Constants.BIN_LOCATION).start();
                File file = new File(Constants.LAUNCHER_PROFILES_JSON);
                while (!file.exists())
                {
                    try
                    {
                        Thread.sleep(50);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                process.destroy();
                if (process.isAlive())
                {
                    process.destroyForcibly();
                }
                String finalExe = exe;
                //Now we have to do horrible stuff because if the launcher binary we downloaded is older than the latest (Don't know why they do this), the auto updater closes and reopens the launcher thus meaning our process handle is wrong.
                ProcessHandle.allProcesses().forEach((processh) ->
                {
                    if (processh.info().commandLine().toString().contains(Constants.BIN_LOCATION))
                    {
                        //It is one of our processes...
                        if (processh.info().commandLine().toString().contains(finalExe))
                        {
                            //It's the process we're looking for...
                            processh.destroy();
                            if (processh.isAlive())
                            {
                                processh.destroyForcibly();
                            }
                            return;
                        }
                    }
                });
            } catch (Exception e)
            {
                CreeperLogger.INSTANCE.error("Failed ot start the Minecraft launcher " + e.toString());
            }
        }).join();
    }
}
