package net.creeperhost.creeperlauncher.api.data.other;

import net.creeperhost.creeperlauncher.api.data.BaseData;

import java.util.HashMap;

public class InstalledFileEventData extends BaseData {
    public static class Reply extends BaseData {
        long fileName;
        String status;
        public Reply(long fileName, String status) {
            this.fileName = fileName;
            this.status = status;
            this.type = "installedFileEventDataReply";
        }
    }
}
