package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.instances.UninstallInstanceData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
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
            CreeperLogger.INSTANCE.error("Error uninstalling pack", err);
            Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "error", err.toString()));
        }

    }
}
