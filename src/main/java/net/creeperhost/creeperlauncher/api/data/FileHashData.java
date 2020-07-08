package net.creeperhost.creeperlauncher.api.data;

import net.creeperhost.creeperlauncher.Settings;

public class FileHashData extends BaseData {
    public String uuid;
    public String filePath;

    public static class Reply extends BaseData {
        String md5Hash;
        String shaHash;
        public Reply(FileHashData data, String md5Hash, String shaHash)
        {
            requestId = data.requestId;
            this.md5Hash = md5Hash;
            this.shaHash = shaHash;
        }
    }
}
