package net.creeperhost.creeperlauncher.api.data;

public class IRCEventCTCPData extends IRCEventBaseUserData {
    boolean error;
    String data;
    public IRCEventCTCPData(String nick, String data, boolean error) {
        super("ctcp", "", nick, "");
        this.error = error;
        this.data = data;
    }
}
