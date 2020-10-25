package net.creeperhost.creeperlauncher.api.data;

import java.util.HashMap;

public class InstalledFileEventData extends BaseData {
    public static class Reply extends BaseData {
        String fileName;
        public Reply(String fileName) {
            this.fileName = fileName;
            this.type = "installedFileEventDataReply";
        }
    }
}
