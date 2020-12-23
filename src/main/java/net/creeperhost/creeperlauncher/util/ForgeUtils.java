package net.creeperhost.creeperlauncher.util;

import com.google.gson.*;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.minecraft.StartJson;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForgeUtils
{
    public static URI findForgeDownloadURL(String minecraftVersion, String forgeVersion) throws URISyntaxException, MalformedURLException
    {
        String repo = "https://apps.modpacks.ch/versions/net/minecraftforge/forge/";

        URI url = new URI(repo + minecraftVersion + "-" + forgeVersion + "/" +
                "forge-" + minecraftVersion + "-" + forgeVersion + "-universal.jar");

        //Temp code to get around there being -universal.jars on our repo that are not real
        if(minecraftVersion.equalsIgnoreCase("1.2.5"))
        {
            CreeperLogger.INSTANCE.info("Legacy version detected, Using older forge urls " + url);
            return new URI(repo + minecraftVersion + "-" + forgeVersion + "/" +
                    "forge-" + minecraftVersion + "-" + forgeVersion + "-client.jar");
        }

        if (!WebUtils.checkExist(url.toURL()))
        {
            CreeperLogger.INSTANCE.info("File does not exist on repo for " + url);
            url = new URI(repo + minecraftVersion + "-" + forgeVersion + "-" + minecraftVersion + "/" +
                    "forge-" + minecraftVersion + "-" + forgeVersion + "-" + minecraftVersion + "-universal.jar");

            if (!WebUtils.checkExist(url.toURL()))
            {
                CreeperLogger.INSTANCE.info("File does not exist on repo for " + url);
                url = new URI(repo + minecraftVersion + "-" + forgeVersion + "/" +
                        "forge-" + minecraftVersion + "-" + forgeVersion + "-universal.zip");
            }
            if (!WebUtils.checkExist(url.toURL()))
            {
                CreeperLogger.INSTANCE.info("File does not exist on repo for " + url);
                url = new URI(repo + minecraftVersion + "-" + forgeVersion + "/" +
                        "forge-" + minecraftVersion + "-" + forgeVersion + "-client.jar");
            }

            if (!WebUtils.checkExist(url.toURL()))
            {
                CreeperLogger.INSTANCE.info("File does not exist on repo for " + url);
                url = new URI(repo + minecraftVersion + "-" + forgeVersion + "/" +
                        "forge-" + minecraftVersion + "-" + forgeVersion + "-universal.jar");
            }
        }

        CreeperLogger.INSTANCE.info("Downloading forge from: " + url.toString());
        return url;
    }

    public static String getLatest(String minecraftVersion)
    {
        return get(minecraftVersion, "latest");
    }

    public static String getRecommended(String minecraftVersion)
    {
        return get(minecraftVersion, "recommended");
    }

    public static String get(String minecraftVersion, String type)
    {
        String resp = WebUtils.getWebResponse(Constants.FORGE_RECOMMENDED);
        JsonElement jElement = new JsonParser().parse(resp).getAsJsonObject().get("promos");

        if (jElement.isJsonObject())
        {
            JsonObject object = jElement.getAsJsonObject();

            return object.get(minecraftVersion + "-" + type).getAsString();
        }
        return null;
    }

    public static boolean validateJson(File file, LocalInstance instance)
    {
        StartJson sj = getFromJson(file);
        if (sj == null) return false;

        if (sj.getJar() == null) sj.setJar(instance.getMcVersion());
        if (sj.getInheritsFrom() == null) sj.setInheritsFrom(instance.getMcVersion());
        if (!sj.getId().equalsIgnoreCase(instance.getModLoader())) sj.setId(instance.getModLoader());

        String jstring = GsonUtils.GSON.toJson(sj);
        try
        {
            Files.write(file.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return true;
    }

    public static StartJson getFromJson(File target)
    {
        try (InputStream stream = new FileInputStream(target))
        {
            JsonObject json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            stream.close();
            return GsonUtils.GSON.fromJson(json, StartJson.class);
        } catch (IOException ignored) {}
        return null;
    }

    public static boolean updateForgeJson(Path target, String newname, String minecraftversion)
    {
        CreeperLogger.INSTANCE.info("Attempting to update forge json");
        try
        {
            JsonObject json;
            try (BufferedReader reader = Files.newBufferedReader(target))
            {
                json = GsonUtils.GSON.fromJson(reader, JsonObject.class);
            } catch (IOException e)
            {
                CreeperLogger.INSTANCE.error("Failed to read " + target);
                e.printStackTrace();
                return false;
            }

            JsonPrimitive id = json.getAsJsonPrimitive("id");
            if (id != null && !id.getAsString().equalsIgnoreCase(newname))
            {
                json.remove("id");
                json.addProperty("id", newname);

                if (json.get("inheritsFrom") == null)
                    json.addProperty("inheritsFrom", minecraftversion);
                if (json.get("jar") == null)
                    json.addProperty("jar", minecraftversion);

                JsonArray targets = json.getAsJsonObject().getAsJsonArray("libraries");
                if (targets != null)
                {
                    for (JsonElement i : targets)
                    {
                        JsonObject server = (JsonObject) i;
                        String name = server.get("name").getAsString();
                        if (name.contains("net.minecraftforge:forge:") || name.contains("net.minecraftforge:minecraftforge:"))
                        {
                            String[] strings = newname.split("forge");
                            String updated = "net.minecraftforge:forge:" + strings[1];
                            server.remove("name");
                            server.addProperty("name", updated);
                        }
                    }
                }
                String jstring = GsonUtils.GSON.toJson(json);
                Files.write(target, jstring.getBytes(StandardCharsets.UTF_8));
                return true;
            }
        } catch (IOException e)
        {
            return false;
        }
        return false;
    }

    public static boolean isUrlValid(String url)
    {
        try
        {
            new URL(url).toURI();
            return true;
        } catch (Exception e)
        {
            return false;
        }
    }

    public static boolean extractJson(Path path, String name)
    {
        try
        {
            FileUtils.fileFromZip(path, path.resolveSibling(name), "version.json");
            return true;
        } catch (IOException err)
        {
            CreeperLogger.INSTANCE.error("Failed to extract 'version.json' from '" + path + "' to '" + name + "'");
        }
        return false;
    }

    @SuppressWarnings("all")
    public static void runForgeInstaller(Path jarloc)
    {
        try
        {
            System.out.println(Files.exists(jarloc));
            URLClassLoader child = new URLClassLoader(new URL[]{jarloc.toUri().toURL()}, CreeperLauncher.class.getClassLoader());
            Class<?> simpleInstallerClass = Class.forName("net.minecraftforge.installer.SimpleInstaller", true, child);
            simpleInstallerClass.getDeclaredField("headless").set(null, true);
            Class<?> utilClass = Class.forName("net.minecraftforge.installer.json.Util", true, child);
            Method method = utilClass.getDeclaredMethod("loadInstallProfile");
            Object install = method.invoke(null);
            Class<?> progressCallbackClass = Class.forName("net.minecraftforge.installer.actions.ProgressCallback", true, child);
            method = progressCallbackClass.getDeclaredMethod("withOutputs", OutputStream[].class);
            Object progress = method.invoke(null, (Object) new OutputStream[]{System.out});
            Class<?> clientInstallClass = Class.forName("net.minecraftforge.installer.actions.ClientInstall", true, child);
            Constructor constructor = clientInstallClass.getDeclaredConstructor(Class.forName("net.minecraftforge.installer.json.Install", true, child), progressCallbackClass);
            Object clientInstall = constructor.newInstance(install, progress);
            Method runMethod = clientInstallClass.getDeclaredMethod("run", File.class, java.util.function.Predicate.class);
            java.util.function.Predicate<String> pred = (p) -> true;
            runMethod.invoke(clientInstall, Constants.BIN_LOCATION.toFile(), pred);
            System.out.println(install);
            System.out.println(progress);
            System.out.println(clientInstall);
            child.close();
            Thread.sleep(10000);
        } catch (Exception e)
        {
            CreeperLogger.INSTANCE.error(e.toString());
            e.printStackTrace();
        }
    }
}
