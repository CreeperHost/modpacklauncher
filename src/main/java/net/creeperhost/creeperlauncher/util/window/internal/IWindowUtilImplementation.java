package net.creeperhost.creeperlauncher.util.window.internal;

import net.creeperhost.creeperlauncher.util.window.IMonitor;
import net.creeperhost.creeperlauncher.util.window.IWindow;

import java.awt.*;
import java.util.List;

public interface IWindowUtilImplementation {
    List<IWindow> getWindows();
    List<IWindow> getWindows(int pid);
    void setPos(int x, int y);
    default boolean isSupported()
    {
        return false;
    }
}
