package net.creeperhost.creeperlauncher.chat;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.IRCEventMessage;
import net.creeperhost.creeperlauncher.api.data.IRCEventWhois;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.Listener;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.WhoisEvent;
import org.pircbotx.hooks.types.GenericCTCPEvent;

import java.io.IOException;

public class Handler {
    public static Handler INSTANCE;
    private final PircBotX botInstance;

    private Handler(String host, int port, String nick, String realname) throws IOException, IrcException {
        Configuration configuration = new Configuration.Builder()
            .setName(nick)
            .addServer(host, port)
            .setRealName(realname)
            .addListener(new MyListener())
            .buildConfiguration();

            botInstance = new PircBotX(configuration);
        botInstance.startBot();
    }

    public static boolean init(String host, int port, String nick, String realname) {
        try {
            INSTANCE = new Handler(host, port, nick, realname);
        } catch (IOException | IrcException e) {
            return false;
        }
        return true;
    }

    public static void disconnect() {
        INSTANCE.botInstance.close();
    }

    private static class MyListener implements Listener {
        @Override
        public void onEvent(Event event) {
            if (event instanceof PrivateMessageEvent)
            {
                PrivateMessageEvent privMsg = (PrivateMessageEvent) event;
                User user = privMsg.getUser();
                if (user != null)
                {
                    Settings.webSocketAPI.sendMessage(new IRCEventMessage(privMsg.getMessage(), privMsg.getUser().getNick(), privMsg.getUser().getRealName()));
                }
            } else if (event instanceof WhoisEvent) {
                WhoisEvent whois = (WhoisEvent) event;
                Settings.webSocketAPI.sendMessage(new IRCEventWhois(whois.getNick(), whois.getRealname()));
            } else if (event instanceof GenericCTCPEvent) {
                GenericCTCPEvent ctcp = (GenericCTCPEvent) event;
            } else if (event instanceof ConnectEvent) {
                ConnectEvent connect = (ConnectEvent) event;
            }
        }
    }
}
