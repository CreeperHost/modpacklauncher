package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.irc.IRCConnectData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.minetogether.lib.chat.Friends;
import net.creeperhost.minetogether.lib.chat.Handler;

import net.creeperhost.creeperlauncher.api.data.friends.NewFriendData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventCTCPData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventMessageData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventRegisteredData;
import net.creeperhost.creeperlauncher.api.data.irc.IRCEventWhoisData;
import net.creeperhost.minetogether.lib.util.Consumers;

import java.util.function.Consumer;

public class IRCConnectHandler implements IMessageHandler<IRCConnectData>
{
    private static final Consumers.TriConsumer<String, String, String> messageHandler = (message, nick, realName) -> Settings.webSocketAPI.sendMessage(new IRCEventMessageData(message, nick, realName));
    private static final Consumers.TriConsumer<String, String, Boolean> whoisHandler = (nick, realname, offline) -> Settings.webSocketAPI.sendMessage(new IRCEventWhoisData(nick, realname, offline));
    private static final Runnable registerHandler = () -> Settings.webSocketAPI.sendMessage(new IRCEventRegisteredData());
    private static final Consumer<Friends.UserProfile> friendHandler = (friend) -> Settings.webSocketAPI.sendMessage(new NewFriendData(friend));
    private static final Consumers.TriConsumer<String, String, Boolean> ctcpHandler = (nick, data, error) -> Settings.webSocketAPI.sendMessage(new IRCEventCTCPData(nick, data, error));

    @Override
    public void handle(IRCConnectData data) {
        if(!Handler.isConnected()) {
            Handler.init(data.host, data.port, data.nick, data.realname, messageHandler, whoisHandler, registerHandler, friendHandler, ctcpHandler);
        }
    }
}
