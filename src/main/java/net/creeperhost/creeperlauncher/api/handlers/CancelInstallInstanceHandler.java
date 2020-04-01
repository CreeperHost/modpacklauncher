package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.CancelInstallInstanceData;

public class CancelInstallInstanceHandler implements IMessageHandler<CancelInstallInstanceData>
{
    @Override
    public void handle(CancelInstallInstanceData data)
    {
        if (CreeperLauncher.isInstalling.get())
        {
            CreeperLauncher.currentInstall.get().cancel();
            Settings.webSocketAPI.sendMessage(new CancelInstallInstanceData.Reply(data, "success", "Cancelled Install", CreeperLauncher.currentInstall.get().currentUUID));
        }
    }
}
