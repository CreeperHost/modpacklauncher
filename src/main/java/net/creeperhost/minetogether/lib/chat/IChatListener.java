package net.creeperhost.minetogether.lib.chat;

import net.creeperhost.minetogether.lib.chat.data.Message;
import net.creeperhost.minetogether.lib.chat.data.Profile;

public interface IChatListener
{
    void onPartyInvite(Profile profile);

    void onFriendOnline(Profile profile);

    void onFriendAccept(String name);

    String onServerIdRequest();

    void sendMessage(Message message);

    void setHasNewMessage(boolean value);
}
