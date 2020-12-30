package net.creeperhost.creeperlauncher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.minetogether.cloudsaves.CloudSaveManager;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.MiscUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
        Path file = Settings.getInstanceLocOr(Constants.INSTANCES_FOLDER_LOC);
        instances.clear();
        List<Path> files = FileUtils.listDir(file);
        ArrayList<CompletableFuture<?>> futures = new ArrayList<>();
        AtomicInteger l = new AtomicInteger(0);
        AtomicInteger t = new AtomicInteger(0);
        if (files != null)
        {
            for (Path f : files)
            {
                futures.add(CompletableFuture.runAsync(() -> {
                    if (Files.isDirectory(f))
                    {
                        t.getAndIncrement();
                        try
                        {
                            Instances.loadInstance(f);
                            l.getAndIncrement();
                        } catch (FileNotFoundException err)
                        {
                            if(!f.getFileName().toString().startsWith(".")) {
                                CreeperLogger.INSTANCE.error("Not a valid instance '" + f.getFileName() + "', skipping...");
                            } else {
                                t.getAndDecrement();
                            }
                            //err.printStackTrace();
                        }
                    }
                }));
            }
        }
        futures.add(CompletableFuture.runAsync(() -> {
            if(Constants.S3_HOST != null && Constants.S3_BUCKET != null && Constants.S3_KEY != null && Constants.S3_SECRET != null) {
                if (!Constants.S3_HOST.isEmpty() && !Constants.S3_BUCKET.isEmpty() && !Constants.S3_KEY.isEmpty() && !Constants.S3_SECRET.isEmpty()) {
                    CreeperLogger.INSTANCE.info("Loading cloud instances");

                    cloudInstances = loadCloudInstances();
                    CreeperLogger.INSTANCE.info("Loaded " + cloudInstances().size() + " cloud instances.");
                }
            }
        }));
        MiscUtils.allFutures(futures).join();
        CreeperLogger.INSTANCE.info("Loaded "+l.get()+" out of "+t.get()+" instances.");

    }

    private static void loadInstance(Path path) throws FileNotFoundException
    {
        Path json = path.resolve("instance.json");
        if (Files.notExists(json)) throw new FileNotFoundException("Instance corrupted; " + json.toAbsolutePath());
        try {
            LocalInstance loadedInstance = new LocalInstance(path);
            Instances.addInstance(loadedInstance.getUuid(), loadedInstance);
        } catch(Exception e)
        {
            CreeperLogger.INSTANCE.error("Corrupted instance json!", e);
            throw new FileNotFoundException("Instance corrupted; " + json.toAbsolutePath());
        }
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
