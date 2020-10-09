package net.creeperhost.creeperlauncher.chat;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.IRCEventMessageData;
import net.creeperhost.creeperlauncher.api.data.IRCEventRegisteredData;
import net.creeperhost.creeperlauncher.api.data.IRCEventWhoisData;
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
    private static Thread ircThread;
    private final PircBotX botInstance;

    private Handler(String host, int port, String nick, String realname) throws IOException, IrcException {
        Configuration configuration = new Configuration.Builder()
            .setName(nick)
            .addServer(host, port)
            .setRealName(realname)
            .addListener(new MyListener())
            .buildConfiguration();

            botInstance = new PircBotX(configuration);
    }

    public static boolean init(String host, int port, String nick, String realname) {
        try {
            INSTANCE = new Handler(host, port, nick, realname);
            ircThread = new Thread(() -> {
                try {
                    INSTANCE.botInstance.startBot();
                } catch (IOException | IrcException e) {
                    e.printStackTrace();
                }
            });
            ircThread.start();
        } catch (IOException | IrcException e) {
            return false;
        }
        return true;
    }

    public static void disconnect() {
        System.out.println("Quitting");
        INSTANCE.botInstance.close();
        INSTANCE = null;
        ircThread = null;
    }

    public void doWhois(String nick) {
        System.out.println("Doing whois for " + nick);
        botInstance.sendIRC().whois(nick);
    }

    public void sendMessage(String nick, String message) {
        System.out.println("Sending message " + message + " to " + nick);
        botInstance.sendIRC().message(nick, message);
    }

    public void ctcpRequest(String nick, String message) {
        System.out.println("Sending CTCP request " + message + " to " + nick);
        botInstance.sendIRC().ctcpCommand(nick, message);
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
                    Settings.webSocketAPI.sendMessage(new IRCEventMessageData(privMsg.getMessage(), privMsg.getUser().getNick(), privMsg.getUser().getRealName()));
                }
            } else if (event instanceof WhoisEvent) {
                WhoisEvent whois = (WhoisEvent) event;
                System.out.println("Whois return received for " + whois.getNick());
                Settings.webSocketAPI.sendMessage(new IRCEventWhoisData(whois.getNick(), whois.getRealname(), !whois.isExists()));
            } else if (event instanceof GenericCTCPEvent) {
                GenericCTCPEvent ctcp = (GenericCTCPEvent) event;
                System.out.println("CTCP received from " + ctcp.getUser().getNick());
                //Settings.webSocketAPI.sendMessage(new IRCC(whois.getNick(), whois.getRealname()));
            } else if (event instanceof ConnectEvent) {
                Settings.webSocketAPI.sendMessage(new IRCEventRegisteredData());
            }
        }
    }
}
