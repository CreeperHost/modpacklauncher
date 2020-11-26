package net.creeperhost.creeperlauncher.api.data.irc;

public abstract class IRCEventBaseUserData extends IRCEventBaseData {
    private final String nick;
    private final String real_name;

    public IRCEventBaseUserData(String jsEvent, String type, String nick, String realname) {
        super(jsEvent, type);
        this.nick = nick;
        this.real_name = realname;
    }
}
