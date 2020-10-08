package net.creeperhost.creeperlauncher.api.data;

public class IRCEventWhois extends IRCEventBaseUserData {
    public IRCEventWhois(String nick, String realname) {
        super("whois", "", nick, realname);
    }
}
