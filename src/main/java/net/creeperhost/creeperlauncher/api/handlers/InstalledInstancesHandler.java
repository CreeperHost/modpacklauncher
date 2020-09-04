package net.creeperhost.creeperlauncher.api.handlers;

import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.InstalledInstancesData;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InstalledInstancesHandler implements IMessageHandler<InstalledInstancesData>
{

    @Override
    public void handle(InstalledInstancesData data)
    {
        int id = data.requestId;
        boolean refresh = data.refresh;
        CompletableFuture.runAsync(() -> {
            if(refresh) Instances.refreshInstances();
            List<LocalInstance> installedInstances = Instances.allInstances();
            List<JsonObject> cloudInstances = Instances.cloudInstances();
            InstalledInstancesData.Reply reply = new InstalledInstancesData.Reply(id, installedInstances, cloudInstances);
            Settings.webSocketAPI.sendMessage(reply);
        });
    }
}
