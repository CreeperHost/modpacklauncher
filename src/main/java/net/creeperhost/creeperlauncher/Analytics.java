package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.util.WebUtils;

public class Analytics
{
    public static void sendInstallRequest(long packID, long packVersion)
    {
        String analytics = "https://modpack-api.ch.tools/public/modpack/" + packID + "/" + packVersion + "/install";
        WebUtils.getWebResponse(analytics);
    }

    public static void sendPlayRequest(long packID, long packVersion)
    {
        String analytics = "https://modpack-api.ch.tools/public/modpack/" + packID + "/" + packVersion + "/play";
        WebUtils.getWebResponse(analytics);
    }
}
