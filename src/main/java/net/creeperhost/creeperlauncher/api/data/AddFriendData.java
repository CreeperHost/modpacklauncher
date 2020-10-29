package net.creeperhost.creeperlauncher.api.data;

import net.creeperhost.creeperlauncher.chat.Friends;

public class AddFriendData extends BaseData{
    public String userHash;
    public String targetHash;
    public static class Reply extends BaseData {
        public Reply(AddFriendData data) {
            this.requestId = data.requestId;
        }
    }
}
