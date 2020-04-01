package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.settingsInfoData;

import java.util.HashMap;

public class settingsInfoHandler implements IMessageHandler<settingsInfoData>
{
    @Override
    public void handle(settingsInfoData data)
    {
        HashMap<String, String> settingsInfo = Settings.settings;
        Settings.webSocketAPI.sendMessage(new settingsInfoData.Reply(data, settingsInfo));
    }
}
