package net.creeperhost.creeperlauncher.util.window;

import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.util.window.internal.IWindowUtilImplementation;
import net.creeperhost.creeperlauncher.util.window.internal.NoOpWindowUtilImplementation;
import net.creeperhost.creeperlauncher.util.window.internal.WindowsWindowUtilImplementation;

import java.util.List;

public class WindowUtils {
    static IWindowUtilImplementation IMPLEMENTATION;
    static {
        switch(OS.CURRENT) {
            case WIN:
                IMPLEMENTATION = new WindowsWindowUtilImplementation();
                break;
            case MAC:
            case LINUX:
            default:
                IMPLEMENTATION = new NoOpWindowUtilImplementation();
        }
    }

    public static List<IWindow> getWindows() {
        return IMPLEMENTATION.getWindows();
    }

    public static List<IWindow> getWindows(int pid) {
        return IMPLEMENTATION.getWindows(pid);
    }

    public static boolean isSupported() {
        return IMPLEMENTATION.isSupported();
    }
}
