package net.creeperhost.creeperlauncher.api.data.friends;

import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.chat.Friends;

public class GetFriendsData extends BaseData {
    public String hash;
    public static class Reply extends BaseData {
        Friends.ListFriendResponse friends;
        public Reply(GetFriendsData data, Friends.ListFriendResponse friends) {
            this.friends = friends;
            this.requestId = data.requestId;
        }
    }
}
