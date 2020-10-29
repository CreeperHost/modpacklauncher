package net.creeperhost.creeperlauncher.chat;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.IRCEventCTCPData;
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
import org.pircbotx.hooks.events.UnknownEvent;
import org.pircbotx.hooks.events.WhoisEvent;
import org.pircbotx.hooks.types.GenericCTCPEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Handler {
    public static Handler INSTANCE;
    private static Thread ircThread;
    private final PircBotX botInstance;
    private HashMap<String, CompletableFuture<WhoisEvent>> whoisRegisters;

    private Handler(String host, int port, String nick, String realname) throws IOException, IrcException {
        Configuration configuration = new Configuration.Builder()
            .setName(nick)
            .addServer(host, port)
            .setRealName(realname)
            .addListener(new MyListener())
            .buildConfiguration();

            botInstance = new PircBotX(configuration);
        whoisRegisters = new HashMap<>();
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

    public static boolean isConnected(){
        return INSTANCE != null && INSTANCE.botInstance.isConnected() && ircThread != null;
    }

    public static void disconnect() {
        System.out.println("Quitting");
        INSTANCE.botInstance.close();
        INSTANCE.whoisRegisters.clear();
        INSTANCE = null;
        ircThread = null;
    }

    public void doWhois(String nick) {
        botInstance.sendIRC().whois(nick);
    }

    public CompletableFuture<WhoisEvent> doWhoisWithFuture(String nick){
        CompletableFuture<WhoisEvent> completableFuture = new CompletableFuture<>();
        whoisRegisters.put(nick, completableFuture);
        doWhois(nick);
        return completableFuture;
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
                if(Handler.INSTANCE.whoisRegisters.containsKey(whois.getNick())){
                    Handler.INSTANCE.whoisRegisters.get(whois.getNick()).complete(whois);
                    Handler.INSTANCE.whoisRegisters.remove(whois.getNick());
                } else {
                    Settings.webSocketAPI.sendMessage(new IRCEventWhoisData(whois.getNick(), whois.getRealname(), !whois.isExists()));
                }
            } else if (event instanceof GenericCTCPEvent) {
                GenericCTCPEvent ctcp = (GenericCTCPEvent) event;
                System.out.println("CTCP received from " + ctcp.getUser().getNick());
            } else if (event instanceof ConnectEvent) {
                Settings.webSocketAPI.sendMessage(new IRCEventRegisteredData());
            } else if(event instanceof UnknownEvent){
                UnknownEvent unknownEvent = (UnknownEvent) event;
                List<String> parsedLine = unknownEvent.getParsedLine();
                String message = parsedLine.size() >= 2 ? parsedLine.get(1) : "";
                if(unknownEvent.getCommand().equals("PRIVMSG") && message.startsWith("\u0001") && message.endsWith("\u0001")){
                    String request = message.substring(1, message.length() - 1);
                    if(request.startsWith("FRIENDREQ ") || request.startsWith("FRIENDACC ") || request.startsWith("SERVERID ")){
                        Settings.webSocketAPI.sendMessage(new IRCEventCTCPData(unknownEvent.getNick(), request, false));
                    }
                }
            }
        }
    }
}
