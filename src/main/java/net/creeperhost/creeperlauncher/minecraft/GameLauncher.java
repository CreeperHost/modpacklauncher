package net.creeperhost.creeperlauncher.minecraft;

import com.sun.jna.platform.KeyboardUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;
import net.creeperhost.creeperlauncher.util.window.IMonitor;
import net.creeperhost.creeperlauncher.util.window.IWindow;
import net.creeperhost.creeperlauncher.util.window.WindowUtils;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GameLauncher
{
    public Process process;
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
                ProcessBuilder builder = new ProcessBuilder(exe, "--workDir", Constants.BIN_LOCATION);
                Map<String, String> environment = builder.environment();
                // clear JAVA_OPTIONS so that they don't interfere
                environment.remove("_JAVA_OPTIONS");
                environment.remove("JAVA_TOOL_OPTIONS");
                environment.remove("JAVA_OPTIONS");
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
                tryAutomation(process);

            } catch (IOException e)
            {
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
                ProcessBuilder builder = new ProcessBuilder(exe, "--workDir", Constants.BIN_LOCATION);
                CreeperLogger.INSTANCE.info("Launching Vanilla launcher and closing - path and args: " + exe + " --workDir " + Constants.BIN_LOCATION);
                Process process = builder.start();
                StreamGobblerLog.redirectToLogger(process.getErrorStream(), CreeperLogger.INSTANCE::error);
                StreamGobblerLog.redirectToLogger(process.getInputStream(), CreeperLogger.INSTANCE::info);
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

    private void tryAutomation(Process start) {
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
                            } catch (InterruptedException e) {
                            }

                            robot.keyPress(KeyEvent.VK_SHIFT);
                            robot.keyPress(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_TAB);
                            robot.keyRelease(KeyEvent.VK_SHIFT);
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
