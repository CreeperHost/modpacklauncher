package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.api.data.InstanceConfigureData;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.util.Map;
import java.util.UUID;

public class InstanceConfigureHandler implements IMessageHandler<InstanceConfigureData>
{
    @Override
    public void handle(InstanceConfigureData data)
    {
        try
        {
            LocalInstance instance = new LocalInstance(UUID.fromString(data.uuid));
            for (Map.Entry<String, String> setting : data.instanceInfo.entrySet())
            {
                switch (setting.getKey().toLowerCase())
                {
                    case "memory":
                        instance.memory = Integer.parseInt(setting.getValue());
                        break;
                    case "name":
                        instance.name = setting.getValue();
                        break;
                    case "jvmargs":
                        instance.jvmArgs = setting.getValue();
                        break;
                    case "width":
                        instance.width = Integer.parseInt(setting.getValue());
                        break;
                    case "height":
                        instance.height = Integer.parseInt(setting.getValue());
                        break;
                    case "embeddedjre":
                        instance.embeddedJre = Boolean.parseBoolean(setting.getValue());
                        if (!instance.embeddedJre)
                        {
                            instance.embeddedJre = (!instance.setJre(true, ""));
                        }
                        break;
                    case "cloudsaves":
                        instance.cloudSaves = Boolean.parseBoolean(setting.getValue());
                        break;
                }
            }
            instance.saveJson();
            Instances.refreshInstances();
            Settings.webSocketAPI.sendMessage(new InstanceConfigureData.Reply(data, "success"));
        } catch (Exception err)
        {
            Settings.webSocketAPI.sendMessage(new InstanceConfigureData.Reply(data, "error"));
        }

    }
}
