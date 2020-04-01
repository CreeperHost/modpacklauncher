package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.UninstallInstanceData;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.UUID;

public class UninstallInstanceHandler implements IMessageHandler<UninstallInstanceData>
{
    @Override
    public void handle(UninstallInstanceData data)
    {
        try
        {
            LocalInstance instance = new LocalInstance(UUID.fromString(data.uuid));
            instance.uninstall();
            Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "success", ""));
        } catch (Exception err)
        {
            Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "error", err.toString().substring(err.toString().indexOf(":"), -1)));
        }

    }
}
