package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.api.data.irc.IRCCtcpRequestData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCCtcpRequestHandler implements IMessageHandler<IRCCtcpRequestData> {
    @Override
    public void handle(IRCCtcpRequestData data) {
        Handler.INSTANCE.ctcpRequest(data.nick, data.message);
    }
}
