package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.instances.InstanceModsData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.pack.FTBPack;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.UUID;

public class InstanceModsHandler implements IMessageHandler<InstanceModsData> {
    @Override
    public void handle(InstanceModsData data) {
        LocalInstance instance = Instances.getInstance(UUID.fromString(data.uuid));
        FTBPack pack = FTBModPackInstallerTask.getPackFromAPI(instance.getId(), instance.getVersionId(), data._private);
        Settings.webSocketAPI.sendMessage(new InstanceModsData.Reply(data, pack.getMods()));
    }
}
