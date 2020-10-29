package net.creeperhost.creeperlauncher.util.window;

import java.awt.*;

public interface IWindow {
    Object getHandle();
    String getWindowTitle();
    int getPid();
    Rectangle getRect();
    IMonitor getMonitor();
    Color getPixelColour(int x, int y);
    void bringToFront();
}
