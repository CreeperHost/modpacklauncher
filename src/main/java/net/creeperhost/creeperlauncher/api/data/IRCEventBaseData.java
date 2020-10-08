package net.creeperhost.creeperlauncher.api.data;

public abstract class IRCEventBaseData extends BaseData {
    public String jsEvent;
    public String ircType;
    public IRCEventBaseData(String jsEvent, String type)
    {
        this.jsEvent = jsEvent;
        this.ircType = type;
        this.type = "ircEvent";
    }
}
