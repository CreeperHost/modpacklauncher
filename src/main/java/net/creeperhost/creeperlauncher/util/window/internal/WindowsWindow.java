package net.creeperhost.creeperlauncher.util.window.internal;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.wince.CoreDLL;
import com.sun.jna.ptr.IntByReference;
import net.creeperhost.creeperlauncher.util.window.IMonitor;
import net.creeperhost.creeperlauncher.util.window.IWindow;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowsWindow implements IWindow {
    private final WinDef.HWND hWnd;
    private int pid;
    public WindowsWindow(WinDef.HWND hWnd)
    {
        this.hWnd = hWnd;
        IntByReference pidPointer = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hWnd, pidPointer);
        pid = pidPointer.getValue();
    }

    private WinDef.HWND gethWnd()
    {
        return hWnd;
    }

    @Override
    public Object getHandle()
    {
        return gethWnd();
    }

    public String getWindowTitle()
    {
        char[] windowText = new char[512];
        User32.INSTANCE.GetWindowText(hWnd, windowText, 512);
        return Native.toString(windowText);
    }

    @Override
    public int getPid()
    {
        return pid;
    }

    @Override
    public Rectangle getRect()
    {
        WinDef.RECT rect = new WinDef.RECT();
        boolean i = User32.INSTANCE.GetWindowRect(hWnd, rect);
        if (!i) return new Rectangle(-1, -1, -1, -1);
        return new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
    }

    @Override
    public IMonitor getMonitor() {
        WinUser.HMONITOR hMonitorWindow = User32.INSTANCE.MonitorFromWindow(hWnd, WinUser.MONITOR_DEFAULTTONEAREST);
        AtomicInteger monitorIndex = new AtomicInteger();
        WinDef.BOOL exists = User32.INSTANCE.EnumDisplayMonitors(null, null, (hMonitor, hdc, rect, lparam) -> {
            if (hMonitor.getPointer().equals(hMonitorWindow.getPointer())) {
                return 0;
            }
            monitorIndex.incrementAndGet();
            return 1;
        }, new WinDef.LPARAM(0));

        if (exists.booleanValue()) return null;

        WinUser.MONITORINFO monitorinfo = new WinUser.MONITORINFO();

        User32.INSTANCE.GetMonitorInfo(hMonitorWindow, monitorinfo);

        int x = monitorinfo.rcMonitor.left;
        int y = monitorinfo.rcMonitor.top;
        int width = monitorinfo.rcMonitor.right - monitorinfo.rcMonitor.left;
        int height = monitorinfo.rcMonitor.bottom - monitorinfo.rcMonitor.top;
        return new WindowsMonitor(monitorIndex.get(), new Rectangle(x, y, width, height));
    }


    @Override
    public Color getPixelColour(int x, int y) {
        WinDef.HWND hwnd = User32.INSTANCE.GetDesktopWindow();

        WinDef.HDC hdc = User32.INSTANCE.GetDC(hwnd);
        int i = WindowsWindowUtilImplementation.OurGDI32.INSTANCE.GetPixel(hdc, x, y);
        User32.INSTANCE.ReleaseDC(hwnd, hdc);
        return new Color(i & 0xff, (i >> 8) & 0xff, (i >> 16) & 0xff);
    }

    @Override
    public void bringToFront() {
        User32.INSTANCE.SetFocus(hWnd);
        User32.INSTANCE.SetForegroundWindow(hWnd);
    }

    @Override
    public boolean hasFocus() {
        return false;
    }

    @Override
    public boolean isForeground() {
        return false;
    }
}
