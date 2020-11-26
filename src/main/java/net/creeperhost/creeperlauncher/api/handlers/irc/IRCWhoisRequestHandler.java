package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.api.data.irc.IRCWhoisRequestData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCWhoisRequestHandler implements IMessageHandler<IRCWhoisRequestData> {
    @Override
    public void handle(IRCWhoisRequestData data) {
        Handler.INSTANCE.doWhois(data.nick);
    }
}
