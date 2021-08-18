package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.friends.OnlineFriendData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCConnectData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.minetogether.lib.chat.ChatHandler;

import net.creeperhost.creeperlauncher.api.data.friends.AcceptedFriendData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventMessageData;
import net.creeperhost.minetogether.lib.chat.IChatListener;
import net.creeperhost.minetogether.lib.chat.data.Message;
import net.creeperhost.minetogether.lib.chat.data.Profile;
import net.creeperhost.minetogether.lib.util.Consumers;

public class IRCConnectHandler implements IMessageHandler<IRCConnectData>
{
    private static final Consumers.DuoConsumer<String, String> messageHandler = (message, nick) -> Settings.webSocketAPI.sendMessage(new IRCEventMessageData(message, nick));

    @Override
    public void handle(IRCConnectData data) {
        if(!ChatHandler.isOnline()) {
            ChatHandler.init(data.nick, data.realname, new IChatListener() {
                @Override
                public void onPartyInvite(Profile profile) {
                    Settings.webSocketAPI.sendMessage(new PartyInviteData(profile));
                }

                @Override
                public void onFriendOnline(Profile profile) {
                    Settings.webSocketAPI.sendMessage(new OnlineFriendData(profile));
                }

                @Override
                public void onFriendAccept(String name) {
                    Settings.webSocketAPI.sendMessage(new AcceptedFriendData(name));
                }

                // We're being asked what server we're on - we shouldn't reply
                @Override
                public String onServerIdRequest() {
                    return null;
                }

                // message received
                @Override
                public void sendMessage(Message message) {
                    messageHandler.accept(message.sender, message.messageStr);
                }

                @Override
                public void setHasNewMessage(boolean value) {
                    // nothing done with this yet, mainly used for banned users in the main chat which won't be in the app
                }
            }, true);
        }
    }
}
