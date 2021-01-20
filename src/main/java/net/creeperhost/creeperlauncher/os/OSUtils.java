package net.creeperhost.creeperlauncher.os;

import net.creeperhost.creeperlauncher.Constants;

public class OSUtils
{

    public static String getExtension()
    {
        switch (OS.current())
        {
            case WIN:
                return "exe";
            case MAC:
                return "zip";
                //return "dmg";
            case LINUX:
                return "tar.gz";
            case UNKNOWN:
                return null;
        }
        return null;
    }

    public static String getVersion()
    {
        return System.getProperty("os.version").toLowerCase();
    }

    public static String getMinecraftLauncherURL()
    {
        if(OS.current() == OS.MAC) return "https://apps.modpacks.ch/FTB2/mac.zip";
        return Constants.MC_LAUNCHER + getExtension();
    }
}
