package net.creeperhost.creeperlauncher.api.data.friends;

import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventBaseUserData;
import net.creeperhost.creeperlauncher.chat.Friends;

public class NewFriendData extends IRCEventBaseUserData {
    Friends.UserProfile profile;
    public NewFriendData(Friends.UserProfile profile){
        super("ctcp", "newFriend", profile.chat.hash.medium, "");
        this.profile = profile;
    }
}
