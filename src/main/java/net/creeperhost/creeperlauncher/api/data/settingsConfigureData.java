package net.creeperhost.creeperlauncher.api.data;

import java.util.HashMap;

public class settingsConfigureData extends BaseData
{

    public HashMap<String, String> settingsInfo; //TODO: second parameter should be something other than String maybe

    public static class Reply extends BaseData
    {
        String status;

        public Reply(settingsConfigureData data, String status)
        {
            type = "saveSettingsReply";
            this.requestId = data.requestId;
            this.status = "success";
        }
    }
}
