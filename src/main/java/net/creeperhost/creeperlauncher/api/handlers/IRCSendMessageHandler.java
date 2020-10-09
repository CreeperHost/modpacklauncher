package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.IRCSendMessageData;
import net.creeperhost.creeperlauncher.chat.Handler;

public class IRCSendMessageHandler implements IMessageHandler<IRCSendMessageData> {
    @Override
    public void handle(IRCSendMessageData data) {
        Handler.INSTANCE.sendMessage(data.nick, data.message);
    }
}
