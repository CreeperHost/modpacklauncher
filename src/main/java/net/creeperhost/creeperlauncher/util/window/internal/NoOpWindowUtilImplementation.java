package net.creeperhost.creeperlauncher.util.window.internal;

import net.creeperhost.creeperlauncher.util.window.IWindow;

import java.awt.*;
import java.util.List;

public class NoOpWindowUtilImplementation implements IWindowUtilImplementation {
    @Override
    public List<IWindow> getWindows() {
        return List.of();
    }

    @Override
    public List<IWindow> getWindows(int pid) {
        return List.of();
    }

    @Override
    public void setPos(int x, int y) {

    }
}
