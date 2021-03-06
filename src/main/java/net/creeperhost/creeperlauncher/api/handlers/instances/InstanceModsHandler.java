package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.instances.InstanceModsData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.api.handlers.ModFile;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.pack.FTBPack;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.List;
import java.util.UUID;

public class InstanceModsHandler implements IMessageHandler<InstanceModsData> {
    @Override
    public void handle(InstanceModsData data) {
        LocalInstance instance = Instances.getInstance(UUID.fromString(data.uuid));
        FTBPack pack = FTBModPackInstallerTask.getPackFromAPI(instance.getId(), instance.getVersionId(), data._private, instance.packType);
        if (pack != null) {
            List<ModFile> instanceMods = instance.getMods();
            List<ModFile> packMods = pack.getMods();
            packMods.forEach(mod -> {
                if (instanceMods.contains(mod)) {
                    mod.setExists(true);
                    instanceMods.remove(mod);
                }
            });
            packMods.addAll(instanceMods);
            Settings.webSocketAPI.sendMessage(new InstanceModsData.Reply(data, packMods));
        }
    }
}
