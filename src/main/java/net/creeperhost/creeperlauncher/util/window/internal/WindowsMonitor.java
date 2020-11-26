package net.creeperhost.creeperlauncher.util.window.internal;

import net.creeperhost.creeperlauncher.util.window.IMonitor;

import java.awt.*;

public class WindowsMonitor implements IMonitor {
    private final int number;
    private final Rectangle rect;

    public WindowsMonitor(int i, Rectangle rect) {
        this.number = i;
        this.rect = rect;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public Rectangle getBounds() {
        return rect;
    }
}
