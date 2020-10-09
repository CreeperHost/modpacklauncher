package net.creeperhost.creeperlauncher.api.data;

public class IRCEventWhoisData extends IRCEventBaseUserData {
    boolean error;
    public IRCEventWhoisData(String nick, String realname, boolean error) {
        super("whois", "", nick, realname);
        this.error = error;
    }
}
