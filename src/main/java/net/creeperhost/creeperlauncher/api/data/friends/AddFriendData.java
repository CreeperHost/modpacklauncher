package net.creeperhost.creeperlauncher.api.data.friends;

import net.creeperhost.creeperlauncher.api.data.BaseData;

public class AddFriendData extends BaseData {
    public String userHash;
    public String targetHash;
    public static class Reply extends BaseData {
        public Reply(AddFriendData data) {
            this.requestId = data.requestId;
        }
    }
}
