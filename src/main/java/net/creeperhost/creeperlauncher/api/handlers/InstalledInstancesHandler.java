package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.InstalledInstancesData;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.List;

public class InstalledInstancesHandler implements IMessageHandler<InstalledInstancesData>
{

    @Override
    public void handle(InstalledInstancesData data)
    {
        int id = data.requestId;
        Instances.refreshInstances();
        List<LocalInstance> installedInstances = Instances.allInstances();
        InstalledInstancesData.Reply reply = new InstalledInstancesData.Reply(id, installedInstances);
        Settings.webSocketAPI.sendMessage(reply);
    }
}
