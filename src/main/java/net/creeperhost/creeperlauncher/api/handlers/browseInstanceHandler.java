package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.browseInstanceData;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.UUID;

public class browseInstanceHandler implements IMessageHandler<browseInstanceData>
{
    @Override
    public void handle(browseInstanceData data)
    {
        try
        {
            LocalInstance instance = Instances.getInstance(UUID.fromString(data.uuid));
            if (instance.browse())
            {
                Settings.webSocketAPI.sendMessage(new browseInstanceData.Reply(data, "success"));
            } else
            {
                Settings.webSocketAPI.sendMessage(new browseInstanceData.Reply(data, "error"));
            }
        } catch (Exception err)
        {
            Settings.webSocketAPI.sendMessage(new browseInstanceData.Reply(data, "error"));
        }
    }
}
