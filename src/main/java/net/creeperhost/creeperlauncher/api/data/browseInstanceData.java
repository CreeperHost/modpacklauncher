package net.creeperhost.creeperlauncher.api.data;


public class browseInstanceData extends BaseData
{
    public String uuid;

    public static class Reply extends BaseData
    {
        String status;

        public Reply(browseInstanceData data, String status)
        {
            type = "browseInstanceReply";
            requestId = data.requestId;
            this.status = status;
        }
    }
}
