package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.api.data.instances.InstanceInstallModData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import net.creeperhost.creeperlauncher.mod.Mod;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.UUID;

public class InstanceInstallModHandler implements IMessageHandler<InstanceInstallModData> {
    @Override
    public void handle(InstanceInstallModData data) {
        String _uuid = data.uuid;
        UUID uuid = UUID.fromString(_uuid);
        LocalInstance instance = Instances.getInstance(uuid);
        Mod mod = Mod.getFromAPI(data.modId);
        if (mod == null) {
            // do fail here
            return;
        } else {
            Mod.Version version = mod.getVersion(data.versionId);
            if (version == null) {
                // do fail here
                return;
            }

            // TODO: dependency checks, single file for now
            DownloadableFile downloadableFile = version.getDownloadableFile(instance);
            DownloadTask downloadTask = new DownloadTask(downloadableFile, downloadableFile.getPath());
            downloadTask.execute();
            //new InstanceInstallModData.Reply()
        }
    }
}
