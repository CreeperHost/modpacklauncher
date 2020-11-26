package net.creeperhost.creeperlauncher.api.data.irc;

import net.creeperhost.creeperlauncher.api.data.BaseData;

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
