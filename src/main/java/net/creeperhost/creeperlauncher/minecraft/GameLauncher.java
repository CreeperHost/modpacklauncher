package net.creeperhost.creeperlauncher.minecraft;

import com.sun.jna.platform.KeyboardUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;
import net.creeperhost.creeperlauncher.util.window.IMonitor;
import net.creeperhost.creeperlauncher.util.window.IWindow;
import net.creeperhost.creeperlauncher.util.window.WindowUtils;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GameLauncher
{
    public static Process process;
    public static String prepareGame()
    {
        try {
            Path stored = Path.of(Constants.BIN_LOCATION);
            File test = new File(stored.toString() + File.separator + Constants.MINECRAFT_LAUNCHER_NAME);
            Path exec = Files.createTempDirectory("ftba");
            if(!test.exists())
            {
                //If we don't have it in our bin dir... Let's download it to where we need it, this is terrible but I don't care if it works and we can iterate its design later
                McUtils.downloadVanillaLauncher(exec.toString());
                McUtils.prepareVanillaLauncher(exec.toString() + File.separator + Constants.MINECRAFT_LAUNCHER_NAME);
            } else {
                FileUtils.copyDirectory(stored, exec);
            }
            return exec.toString();
        } catch(Exception err)
        {
            CreeperLogger.INSTANCE.warning("Unable to copy Mojang launcher.", err);
        }
        return null;
    }
    public void launchGame()
    {
        launchGame(Constants.BIN_LOCATION);
    }
    public static Process launchGame(String path)
    {
        String exe = path + File.separator + Constants.MINECRAFT_LAUNCHER_NAME;
        OS os = OSUtils.getOs();
        if (os == OS.MAC)
        {
            exe = path + File.separator + "Minecraft.app" + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "launcher";
        }
        if (os == OS.LINUX)
        {
            exe = path + File.separator + "minecraft-launcher" + File.separator + "minecraft-launcher";
        }
        try
        {
            String command = exe;
            ProcessBuilder builder = new ProcessBuilder(command, "--workDir", path);
            if(os == OS.MAC)
            {
                CreeperLogger.INSTANCE.warning("/usr/bin/open " + path + File.separator + "Minecraft.app" + " --args --workDir " + path);
                builder = new ProcessBuilder("/usr/bin/open", path + File.separator + "Minecraft.app", "--args", "--workDir", path);
            }

            Map<String, String> environment = builder.environment();
            // clear JAVA_OPTIONS so that they don't interfere
            environment.remove("_JAVA_OPTIONS");
            environment.remove("JAVA_TOOL_OPTIONS");
            environment.remove("JAVA_OPTIONS");
            //TODO: This is pointless as this sets our JVM's locale not the system locale
            if(Locale.getDefault() == null)
            {
                Locale.setDefault(Locale.US);
            }
            CreeperLogger.INSTANCE.error(Locale.getDefault().toString());
            process = builder.start();
            process.onExit().thenRunAsync(() -> {
                    CreeperLauncher.mojangProcesses.getAndUpdate((List<Process> processes) -> {
                        if(processes != null) {
                            List<Process> toRemove = new ArrayList<Process>();
                            for (Process loopProcess : processes) {
                                if (loopProcess.pid() == process.pid()) {
                                    toRemove.add(process);
                                }
                            }
                            for (Process remove : toRemove) {
                                processes.remove(remove);
                            }
                        }
                        return processes;
                    });
            });
            if(Settings.settings.getOrDefault("automateMojang", "true").equalsIgnoreCase("true")){
                if(process != null) {
                    tryAutomation(process);
                } else {
                    CreeperLogger.INSTANCE.error("Minecraft Launcher process failed to start, could not automate.");
                }
            }
            return process;
        } catch (IOException e)
        {
            CreeperLogger.INSTANCE.error("Unable to launch vanilla launcher! ", e);
            return null;
        }
    }

    public static void downloadLauncherProfiles()
    {
        downloadLauncherProfiles(Constants.BIN_LOCATION);
    }
    public static void downloadLauncherProfiles(String path)
    {
        try {
            File file = new File(Constants.LAUNCHER_PROFILES_JSON);
            if(!file.exists())
            {
                //Some reason the vanilla launcher is not creating the launcher_profiles.json
                DownloadableFile defaultConfig = new DownloadableFile("", file.getAbsolutePath(), "https://apps.modpacks.ch/FTB2/launcher_profiles.json", new ArrayList<>(), 0, true, false, 0, "config", "launcher_profiles.json", "");
                defaultConfig.prepare();
                defaultConfig.download(file.toPath(), true, false);
            }
        } catch (Throwable ignored) {
        }

    }

    public static void launchGameAndClose()
    {
        launchGameAndClose(Constants.BIN_LOCATION);
    }
    public static void launchGameAndClose(String path)
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
                Process process = launchGame(path);
                File file = new File(Constants.LAUNCHER_PROFILES_JSON);
                int tryCount = 0;
                while (!file.exists())
                {
                    try
                    {
                        Thread.sleep(50);
                        tryCount++;
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    //3 minutes
                    //((3 * 60) * 1000) / 50
                    if(tryCount > 3600)
                    {
                        CreeperLogger.INSTANCE.warning("Timed out waiting for Mojang launcher...");
                        break;
                    }
                }
                if(process != null) {
                    CreeperLogger.INSTANCE.debug("Destroy instance calling");
                    process.destroy();
                    if (process.isAlive()) {
                        CreeperLogger.INSTANCE.debug("Destroy instance forcibly calling");
                        process.destroyForcibly();
                    }
                }
                if(!file.exists())
                {
                    //Some reason the vanilla launcher is not creating the launcher_profiles.json
                    DownloadableFile defaultConfig = new DownloadableFile("", file.getAbsolutePath(), "https://apps.modpacks.ch/FTB2/launcher_profiles.json", new ArrayList<>(), 0, true, false, 0, "config", "launcher_profiles.json", "");
                    defaultConfig.prepare();
                    defaultConfig.download(file.toPath(), true, false);
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
                            CreeperLogger.INSTANCE.debug("Destroy instance calling");
                            processh.destroy();
                            if (processh.isAlive())
                            {
                                CreeperLogger.INSTANCE.debug("Destroy instance forcibly calling");
                                processh.destroyForcibly();
                            }
                            return;
                        }
                    }
                });
            } catch (Throwable e)
            {
                CreeperLogger.INSTANCE.error("Failed ot start the Minecraft launcher " + e.toString());
            }
        }).join();
    }

    private static void tryAutomation(Process start) {
        long pid = start.pid();
        if (WindowUtils.isSupported())
        {
            outer:
            while(start.isAlive())
            {
                List<IWindow> windows = WindowUtils.getWindows((int) pid);
                for(IWindow window : windows) {
                    if (window.getWindowTitle().equals("Minecraft Launcher")) {
                        Rectangle rect = window.getRect();
                        try {
                            Robot robot;
                            IMonitor monitor = window.getMonitor();
                            GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
                            GraphicsDevice device = null;
                            if (monitor != null)
                            {
                                device = screenDevices[monitor.getNumber()];
                            } else {
                                free:
                                for (GraphicsDevice graphicsDevice : screenDevices) {
                                    GraphicsConfiguration[] configurations = graphicsDevice.getConfigurations();
                                    for (GraphicsConfiguration configuration : configurations) {
                                        Rectangle bounds1 = configuration.getBounds();
                                        if (bounds1.contains(rect)) {
                                            device = graphicsDevice;
                                            break free;
                                        }
                                    }
                                }
                            }

                            if(device == null)
                                break outer;

                            robot = new Robot(device);
                            Rectangle bounds;
                            double dpiChange = 1;
                            if (monitor != null)
                            {
                                bounds = monitor.getBounds();
                                dpiChange = monitor.getBounds().getWidth() / device.getDefaultConfiguration().getBounds().getWidth();
                            } else {
                                bounds = device.getDefaultConfiguration().getBounds();
                            }

                            Rectangle DPIBounds = new Rectangle((int)(bounds.x / dpiChange), (int)(bounds.y / dpiChange), (int)(bounds.width / dpiChange), (int)(bounds.height / dpiChange));

                            Rectangle relativeRect = new Rectangle(rect.x - bounds.x, rect.y - bounds.y, rect.width, rect.height);

                            Rectangle adjustedRect = new Rectangle(DPIBounds.x + (int)(relativeRect.x / dpiChange), DPIBounds.y + (int)(relativeRect.y / dpiChange), (int)(relativeRect.width / dpiChange), (int)(relativeRect.height / dpiChange));

                            int rectxDPI = adjustedRect.x;
                            int rectyDPI = adjustedRect.y;
                            int widthDPI = adjustedRect.width;
                            int heightDPI = adjustedRect.height;

                            int sideBarleft = 181;

                            int bottomOffset = 30;
                            int topOffset = heightDPI - bottomOffset;
                            int leftOffset = sideBarleft + ((widthDPI - sideBarleft) / 2);

                            int x = rectxDPI + leftOffset;
                            int y = rectyDPI + topOffset;

                            boolean isGreen = false;
                            int count = 0;

                            for(count = 0; count < 250; count++)
                            {
                                window.bringToFront();
                                Color pixelColor = window.getPixelColour(x, y);
                                int red = pixelColor.getRed();
                                int green = pixelColor.getGreen();
                                int blue = pixelColor.getBlue();
                                isGreen = red == 0 && blue > 50 && blue < 80 && green > 130;
                                if (isGreen) break;
                                try {
                                    Thread.sleep(20);
                                } catch (InterruptedException e) {
                                }
                            }

                            if (!isGreen) break outer; // abort

                            try {
                                Thread.sleep(80);
                            } catch (InterruptedException ignored) {}

                            robot.keyPress(KeyEvent.VK_SHIFT);
                            robot.keyPress(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_SHIFT);

                            robot.keyPress(KeyEvent.VK_SHIFT);
                            robot.keyPress(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_SHIFT);

                            robot.keyPress(KeyEvent.VK_SPACE);
                            robot.keyRelease(KeyEvent.VK_SPACE);
                            robot.keyPress(KeyEvent.VK_SPACE);
                            robot.keyRelease(KeyEvent.VK_SPACE);

                            //window.setPos(buttonCentre, yMove);
                            break outer;
                        } catch (AWTException e) {
                            // shrug
                        }
                    } else {
                        try {
                            Thread.sleep(25);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
    }
}
