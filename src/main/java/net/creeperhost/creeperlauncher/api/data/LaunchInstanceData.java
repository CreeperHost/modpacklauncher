package net.creeperhost.creeperlauncher.api.data;


public class LaunchInstanceData extends BaseData
{
    public String uuid;

    public static class Reply extends BaseData
    {
        String status;

        public Reply(LaunchInstanceData data, String status)
        {
            type = "launchInstanceReply";
            requestId = data.requestId;
            this.status = status;
        }
    }
}
