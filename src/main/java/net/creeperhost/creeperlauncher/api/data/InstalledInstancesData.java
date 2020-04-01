package net.creeperhost.creeperlauncher.api.data;

import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.List;

public class InstalledInstancesData extends BaseData
{
    public static class Reply extends BaseData
    {
        List<LocalInstance> instances;

        public Reply(int requestId, List<LocalInstance> instances)
        {
            this.instances = instances;
            this.type = "installedInstancesReply";
            this.requestId = requestId;
        }
    }
}
