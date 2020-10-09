package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.IRCQuitRequestData;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCQuitRequestHandler implements IMessageHandler<IRCQuitRequestData> {
    @Override
    public void handle(IRCQuitRequestData data) {
        Handler.disconnect();
    }
}
