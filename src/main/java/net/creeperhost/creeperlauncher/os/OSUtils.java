package net.creeperhost.creeperlauncher.os;

import net.creeperhost.creeperlauncher.Constants;

public class OSUtils
{
    public static OS getOs()
    {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win"))
        {
            return OS.WIN;
        } else if (osName.contains("mac"))
        {
            return OS.MAC;
        } else if (osName.contains("linux"))
        {
            return OS.LINUX;
        } else
        {
            return OS.UNKNOWN;
        }
    }

    public static String getExtension()
    {
        OS os = getOs();
        switch (os)
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
        if(getOs() == OS.MAC) return "https://apps.modpacks.ch/FTB2/mac.zip";
        return Constants.MC_LAUNCHER + getExtension();
    }
}
