package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.IRCConnectData;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCConnectHandler implements IMessageHandler<IRCConnectData>
{
    @Override
    public void handle(IRCConnectData data) {
        Handler.init(data.host, data.port, data.nick, data.realname);
    }
}
