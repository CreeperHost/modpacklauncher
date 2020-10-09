package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.api.data.IRCWhoisRequestData;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCWhoisRequestHandler implements IMessageHandler<IRCWhoisRequestData> {
    @Override
    public void handle(IRCWhoisRequestData data) {
        Handler.INSTANCE.doWhois(data.nick);
    }
}
