package net.creeperhost.creeperlauncher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.minetogether.cloudsaves.CloudSaveManager;
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
    private static HashMap<UUID, JsonObject> cloudInstances = new HashMap<UUID, JsonObject>();

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

    public static List<JsonObject> cloudInstances()
    {
        return Instances.cloudInstances.values().stream().collect(Collectors.toList());
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
                        if(!f.getName().startsWith(".")) {
                            CreeperLogger.INSTANCE.error("Not a valid instance '" + f.getName() + "', skipping...");
                        } else {
                            t--;
                        }
                        //err.printStackTrace();
                    }
                }
            }
        }
        CreeperLogger.INSTANCE.info("Loaded "+l+" out of "+t+" instances.");

        if(!Constants.S3_HOST.isEmpty() && !Constants.S3_BUCKET.isEmpty() && !Constants.S3_KEY.isEmpty() && !Constants.S3_SECRET.isEmpty())
        {
            CreeperLogger.INSTANCE.info("Loading cloud instances");

            cloudInstances = loadCloudInstances();
            CreeperLogger.INSTANCE.info("Loaded " + cloudInstances().size() + " cloud instances.");
        }
    }

    private static void loadInstance(String _uuid) throws FileNotFoundException
    {
        File json = new File(Settings.settings.getOrDefault("instanceLocation", Constants.INSTANCES_FOLDER_LOC) + File.separator + _uuid, "instance.json");
        if (!json.exists()) throw new FileNotFoundException("Instance corrupted; " + json.getAbsoluteFile());
        UUID uuid = UUID.fromString(_uuid);
        LocalInstance loadedInstance = new LocalInstance(uuid);
        Instances.addInstance(uuid, loadedInstance);
    }

    private static HashMap<UUID, JsonObject> loadCloudInstances()
    {
        List<UUID> uuidList = CloudSaveManager.getPrefixes();
        HashMap<UUID, JsonObject> hashMap = new HashMap<>();

        for (UUID uuid : uuidList)
        {
            try
            {
                if(Instances.getInstance(uuid) == null)
                {
                    String jsonResp = CloudSaveManager.getFile(uuid.toString() + "/instance.json");
                    Gson gson = new Gson();
                    JsonObject object = gson.fromJson(jsonResp, JsonObject.class);
                    hashMap.put(uuid, object);
                }
            } catch (Exception e)
            {
                CreeperLogger.INSTANCE.error("Invalid cloudsave found with UUID of " + uuid.toString());
            }

        }
        return hashMap;
    }
}
