package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.*;
import net.creeperhost.creeperlauncher.api.data.InstallInstanceData;
import net.creeperhost.creeperlauncher.cloudsaves.CloudSaveManager;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.pack.FTBPack;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.MiscUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SyncInstanceHandler implements IMessageHandler<InstallInstanceData>
{
    public static AtomicReference<String> lastError = new AtomicReference<String>();

    @Override
    public void handle(InstallInstanceData data)
    {
        if(data.uuid != null && data.uuid.length() > 0) {
            if (CreeperLauncher.isInstalling.get()) {
                Settings.webSocketAPI.sendMessage(new InstallInstanceData.Reply(data, "error", "Install in progress.", CreeperLauncher.currentInstall.get().currentUUID));
                return;
            }
            Settings.webSocketAPI.sendMessage(new InstallInstanceData.Reply(data, "init", "Install started.", ""));
            File instanceDir = new File(Constants.INSTANCES_FOLDER_LOC + File.separator + data.uuid);
            File instanceJson = new File(instanceDir + File.separator + "instance.jon");
            instanceDir.mkdir();

            try
            {
                CloudSaveManager.downloadFile(data.uuid + "/instance.json", instanceJson, false, null);
            } catch (Exception e) {}

            LocalInstance instance;
            try
            {
                instance = new LocalInstance(UUID.fromString(data.uuid));
                instance.cloudSync();
                Settings.webSocketAPI.sendMessage(new InstallInstanceData.Reply(data, "success", "Install complete.", instance.getUuid().toString()));
            } catch (FileNotFoundException e) { e.printStackTrace(); }
        }
        Settings.webSocketAPI.sendMessage(new InstallInstanceData.Reply(data, "error", lastError.get(), data.uuid));
    }
}
