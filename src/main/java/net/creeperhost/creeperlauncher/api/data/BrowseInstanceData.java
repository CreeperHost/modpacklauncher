package net.creeperhost.creeperlauncher.api.data;


public class BrowseInstanceData extends BaseData
{
    public String uuid;

    public static class Reply extends BaseData
    {
        String status;

        public Reply(BrowseInstanceData data, String status)
        {
            type = "browseInstanceReply";
            requestId = data.requestId;
            this.status = status;
        }
    }
}
