package net.creeperhost.creeperlauncher.api.data.instances;

import net.creeperhost.creeperlauncher.api.SimpleDownloadableFile;
import net.creeperhost.creeperlauncher.api.data.BaseData;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class InstanceModsData extends BaseData
{
    public String uuid;
    public boolean _private = false;
    public static class Reply extends InstanceModsData
    {
        public List<SimpleDownloadableFile> files;

        public Reply(InstanceModsData data, List<SimpleDownloadableFile> simpleDownloadableFiles)
        {
            type = "instanceModsReply";
            uuid = data.uuid;
            requestId = data.requestId;
            this.files = simpleDownloadableFiles;
        }
    }
}
