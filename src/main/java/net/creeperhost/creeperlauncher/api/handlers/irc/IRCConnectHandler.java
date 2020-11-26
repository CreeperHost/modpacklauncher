package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.api.data.irc.IRCConnectData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCConnectHandler implements IMessageHandler<IRCConnectData>
{
    @Override
    public void handle(IRCConnectData data) {
        if(!Handler.isConnected()) {
            Handler.init(data.host, data.port, data.nick, data.realname);
        }
    }
}
