package net.creeperhost.creeperlauncher.api.data.irc;

public class IRCEventMessageData extends IRCEventBaseUserData {
    private final String message;

    public IRCEventMessageData(String message, String nick, String realname) {
        super("message", "privmsg", nick, realname);
        this.message = message;
    }
}
