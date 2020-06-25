package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.settingsConfigureData;

import java.util.Map;

public class SettingsConfigureHandler implements IMessageHandler<settingsConfigureData>
{
    @Override
    public void handle(settingsConfigureData data)
    {
        for (Map.Entry<String, String> setting : data.settingsInfo.entrySet())
        {
            if (Settings.settings.containsKey(setting.getKey()))
            {
                Settings.settings.remove(setting.getKey());
            }
            Settings.settings.put(setting.getKey(), setting.getValue());
        }
        Settings.saveSettings();
        Settings.webSocketAPI.sendMessage(new settingsConfigureData.Reply(data, "success"));
    }
}
