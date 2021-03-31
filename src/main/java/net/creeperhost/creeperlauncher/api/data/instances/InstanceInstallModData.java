package net.creeperhost.creeperlauncher.api.data.instances;

import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.api.handlers.instances.InstanceInstallModHandler;
import net.creeperhost.creeperlauncher.install.tasks.FTBModPackInstallerTask;
import net.creeperhost.creeperlauncher.mod.Mod;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.ArrayList;
import java.util.List;

public class InstanceInstallModData extends BaseData {
    public String uuid;
    public int modId;
    public int versionId;

    public static class Reply extends BaseData
    {
        final String status;
        final String message;
        final List<Mod.Version> dependencyList;

        public Reply(InstanceInstallModData data, String status, String message, ArrayList<Mod.Version> dependencies)
        {
            type = "instanceInstallModReply";
            requestId = data.requestId;
            this.status = status;
            this.message = message;
            this.dependencyList = dependencies;
        }
    }

    public static class Progress extends BaseData {
        final Double overallPercentage;
        final long speed;
        final long currentBytes;
        final long overallBytes;
        final FTBModPackInstallerTask.Stage currentStage;

        public Progress(InstallInstanceData data, Double overallPercentage, long speed, long currentBytes, long overallBytes, FTBModPackInstallerTask.Stage currentStage) {
            this.requestId = data.requestId;
            type = "instanceInstallModProgress";
            this.overallPercentage = overallPercentage;
            this.speed = speed;
            this.currentBytes = currentBytes;
            this.overallBytes = overallBytes;
            this.currentStage = currentStage;
        }

    }

    public static void main(String[] args) {
        CreeperLauncher.initSettingsAndCache();
        Instances.refreshInstances();
        LocalInstance localInstance = Instances.allInstances().get(0);
        InstanceInstallModData instanceInstallModData = new InstanceInstallModData();
        instanceInstallModData.modId = 267172;
        instanceInstallModData.versionId = 3077015;
        instanceInstallModData.uuid = localInstance.getUuid().toString();
        new InstanceInstallModHandler().handle(instanceInstallModData);
    }
}
