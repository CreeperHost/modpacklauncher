package net.creeperhost.minetogether.lib.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChatConnectionHandler
{
    public static final ChatConnectionHandler INSTANCE = new ChatConnectionHandler();
    public long timeout = 0;
    public boolean banned;
    public String banReason = "";
    public Logger logger = LogManager.getLogger();

    public synchronized void setup(String nickIn, String realNameIn, boolean onlineIn)
    {
        ChatHandler.online = onlineIn;
        ChatHandler.realName = realNameIn;
        ChatHandler.initedString = nickIn;
        ChatHandler.nick = nickIn;
        ChatHandler.IRC_SERVER = ChatCallbacks.getIRCServerDetails();
        ChatHandler.CHANNEL = ChatHandler.online ? ChatHandler.IRC_SERVER.channel : "#SuperSpecialPirateClub";
        ChatHandler.tries.set(0);
    }

    public synchronized void connect()
    {
        IrcHandler.start(ChatCallbacks.getIRCServerDetails());
        logger.info("Attempting to connect to irc channel " + ChatHandler.CHANNEL);
    }

    public boolean canConnect()
    {
        return !banned && timeout < System.currentTimeMillis() || !ChatHandler.connectionStatus.equals(ChatConnectionStatus.DISCONNECTED) || ChatHandler.inited.get() || ChatHandler.isInitting.get();// || Config.getInstance().isChatEnabled();
    }

    public void nextConnectAllow(int timeout)
    {
        this.timeout = timeout;
    }
}
