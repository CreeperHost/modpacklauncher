package net.creeperhost.creeperlauncher.util;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;

import java.io.File;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static net.creeperhost.creeperlauncher.os.OS.WIN;

public class MiscUtils
{
    public static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static String byteArrayToHex(byte[] a)
    {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static long unixtime()
    {
        return System.currentTimeMillis() / 1000L;
    }

    public static String getDateAndTime()
    {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
        LocalDateTime now = LocalDateTime.now();

        return dateTimeFormatter.format(now);
    }

    private static String[] javaRegLocations = new String[] {"Java Development Kit", "Java Runtime Environment"};
    private static HashMap<Integer, String> archLocations = new HashMap<>() {{
        put(64, "SOFTWARE\\JavaSoft\\");
        put(32, "SOFTWARE\\Wow6432Node\\JavaSoft\\");
    }};

    public static void updateJavaVersions()
    {
        HashMap<String, String> versions = new HashMap<>();
        switch(OSUtils.getOs()) {
            case WIN:
                versions.put("Mojang Built-in", "");
                for(Map.Entry<Integer, String> arch: archLocations.entrySet()) {
                    for (String location : javaRegLocations) {
                        String fullPath = arch.getValue() + location;
                        if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, fullPath))
                        {
                            WinReg.HKEYByReference key = Advapi32Util.registryGetKey(WinReg.HKEY_LOCAL_MACHINE, fullPath, WinNT.KEY_READ);
                            String[] children = Advapi32Util.registryGetKeys(key.getValue());
                            for (String child : children) {
                                System.out.println(fullPath + "\\" + child + "\\JavaHome");
                                String javaHome = null;
                                try {
                                    javaHome = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, fullPath + "\\" + child, "JavaHome");
                                } catch (Win32Exception ignored) {
                                }
                                if (javaHome != null) {
                                    versions.put(child + " (" + arch.getKey() + "bit)", Path.of(javaHome, "bin" + File.separator + "java.exe").toString());
                                }
                            }
                        }
                    }
                }
                break;
            case MAC:
                break;
            case LINUX:
                break;
            case UNKNOWN:
                break;
        }

        CreeperLauncher.javaVersions = versions;

    }
}
