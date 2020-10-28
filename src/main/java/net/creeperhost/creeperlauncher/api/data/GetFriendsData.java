package net.creeperhost.creeperlauncher.api.data;

import net.creeperhost.creeperlauncher.chat.Friends;

import java.util.HashMap;

public class GetFriendsData extends BaseData{
    public String hash;
    public static class Reply extends BaseData {
        Friends.ListFriendResponse friends;
        public Reply(GetFriendsData data, Friends.ListFriendResponse friends) {
            this.friends = friends;
            this.requestId = data.requestId;
        }
    }
}
