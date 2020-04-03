package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.util.WebUtils;

public class Analytics
{
    public static void sendInstallRequest(long packID, long packVersion)
    {
        String analytics = Constants.CREEPERHOST_MODPACK_SEARCH2 + "/" + packID + "/" + packVersion + "/install";
        WebUtils.getWebResponse(analytics);
    }

    public static void sendPlayRequest(long packID, long packVersion)
    {
        String analytics = Constants.CREEPERHOST_MODPACK_SEARCH2 + "/" + packID + "/" + packVersion + "/play";
        WebUtils.getWebResponse(analytics);
    }
}
