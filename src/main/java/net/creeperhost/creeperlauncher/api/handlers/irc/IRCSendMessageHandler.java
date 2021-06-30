package net.creeperhost.creeperlauncher.api.handlers.irc;

import net.creeperhost.creeperlauncher.api.data.irc.IRCSendMessageData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.minetogether.lib.chat.Handler;

public class IRCSendMessageHandler implements IMessageHandler<IRCSendMessageData> {
    @Override
    public void handle(IRCSendMessageData data) {
        Handler.INSTANCE.sendMessage(data.nick, data.message);
    }
}
