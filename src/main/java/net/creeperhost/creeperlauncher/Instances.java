package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Instances
{
    private static HashMap<UUID, LocalInstance> instances = new HashMap<UUID, LocalInstance>();

    public static boolean addInstance(UUID uuid, LocalInstance instance)
    {
        Instances.instances.put(uuid, instance);
        return true;
    }

    public static LocalInstance getInstance(UUID uuid)
    {
        return Instances.instances.get(uuid);
    }

    public static List<String> listInstances()
    {
        return instances.keySet().stream().map(UUID::toString).collect(Collectors.toList());
    }

    public static List<LocalInstance> allInstances()
    {
        return Instances.instances.values().stream().collect(Collectors.toList());
    }

    public static void refreshInstances()
    {
        File file = new File(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC));
        instances.clear();
        File[] files = file.listFiles();
        int l=0,t=0;
        if (files != null)
        {
            for (File f : files)
            {
                if (f.isDirectory())
                {
                    t++;
                    try
                    {
                        Instances.loadInstance(f.getName());
                        l++;
                    } catch (FileNotFoundException err)
                    {
                        CreeperLogger.INSTANCE.error("Not a valid instance '" + f.getName() + "', skipping...");
                        //err.printStackTrace();
                    }
                }
            }
        }
        CreeperLogger.INSTANCE.info("Loaded "+l+" out of "+t+" instances.");
    }

    private static void loadInstance(String _uuid) throws FileNotFoundException
    {
        File json = new File(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + _uuid, "instance.json");
        if (!json.exists()) throw new FileNotFoundException("Instance corrupted; " + json.getAbsoluteFile());
        UUID uuid = UUID.fromString(_uuid);
        LocalInstance loadedInstance = new LocalInstance(uuid);
        Instances.addInstance(uuid, loadedInstance);
    }
}
