package net.creeperhost.creeperlauncher.util.window.internal;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import net.creeperhost.creeperlauncher.util.window.IWindow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WindowsWindowUtilImplementation implements IWindowUtilImplementation {
    @Override
    public List<IWindow> getWindows() {
        return getWindows(-1);
    }

    @Override
    public List<IWindow> getWindows(int pid) {
        ArrayList<IWindow> tempList = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hWnd, arg) -> {
            WindowsWindow windowsWindow = new WindowsWindow(hWnd);
            if (pid != -1 && windowsWindow.getPid() != pid) return true;
            tempList.add(windowsWindow);
            return true;
        }, null);
        return tempList;
    }

    @Override
    public void setPos(int x, int y) {
        com.sun.jna.platform.win32.User32.INSTANCE.SetCursorPos(x, y);
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    public interface OurGDI32 extends GDI32 {
        OurGDI32 INSTANCE = Native.load("gdi32", OurGDI32.class, W32APIOptions.DEFAULT_OPTIONS);
        int GetPixel(WinDef.HDC hdc, int x, int y);
    }
}
