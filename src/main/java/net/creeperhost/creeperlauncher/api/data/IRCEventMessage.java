package net.creeperhost.creeperlauncher.api.data;

public class IRCEventMessage extends IRCEventBaseUserData {
    private final String message;

    public IRCEventMessage(String message, String nick, String realname) {
        super("message", "privmsg", nick, realname);
        this.message = message;
    }
}
