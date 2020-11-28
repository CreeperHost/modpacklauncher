package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.os.OSUtils;

import java.io.File;
import java.nio.file.FileSystems;

public class Constants
{
    //CWD
    public static final String WORKING_DIR = System.getProperty("user.dir");
    public static final String DATA_DIR = System.getProperty("user.home") + File.separator + ".ftba";

    //Launcher titles
    public static final String windowTitle = "FTBApp";

    //Mojang
    public static final String MC_VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String MC_RESOURCES = "http://resources.download.minecraft.net/";
    public static final String MC_LIBS = "https://libraries.minecraft.net/";
    public static final String MC_LAUNCHER = "https://launcher.mojang.com/download/Minecraft.";

    //API
    public static final String CREEPERHOST_MODPACK = CreeperLauncher.isDevMode ? "https://modpack-api.ch.tools" : "https://api.modpacks.ch";
    public static final String CREEPERHOST_MODPACK_SEARCH2 = CREEPERHOST_MODPACK + "/public/modpack/";

    //Forge
    public static final String FORGE_XML = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/maven-metadata.xml";
    public static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/";
    public static final String FORGE_RECOMMENDED = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/promotions_slim.json";

    //Paths
    public static final String BIN_LOCATION_OURS = WORKING_DIR + File.separator + "bin";
    public static final String BIN_LOCATION = DATA_DIR + File.separator + "bin";
    public static final String MINECRAFT_LAUNCHER_LOCATION = BIN_LOCATION + File.separator + "launcher." + OSUtils.getExtension();
    public static final String MINECRAFT_MAC_LAUNCHER_EXECUTABLE = DATA_DIR + File.separator + "bin" + File.separator + "Minecraft.app" + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "launcher";
    public static final String MINECRAFT_MAC_LAUNCHER_VOLUME = "/Volumes/Minecraft";
    public static final String MINECRAFT_LINUX_LAUNCHER_EXECUTABLE = DATA_DIR + File.separator + "bin" + File.separator + "minecraft-launcher" + File.separator + "minecraft-launcher";
    public static final String VERSIONS_FOLDER_LOC = DATA_DIR + File.separator + "bin" + File.separator + "versions";
    public static final String INSTANCES_FOLDER_LOC = DATA_DIR + File.separator + "instances";
    public static final String LAUNCHER_PROFILES_JSON = DATA_DIR + File.separator + "bin" + File.separator + "launcher_profiles.json";
    public static final String LIBRARY_LOCATION = DATA_DIR + File.separator + "bin" + File.separator + "libraries";
    public static final String OLD_CACHE_LOCATION = DATA_DIR + File.separator + ".localCache";

    //Other
    public static final int WEBSOCKET_PORT = 13377;
    public static final String APPVERSION = "@APPVERSION@";
    public static final String BRANCH = "@BRANCH@";
    public static final String PLATFORM = FileSystems.getDefault().getPath(".").toAbsolutePath().toString().contains("Overwolf") ? "Overwolf" : "Electron";

    //Auth
    public static String KEY = "";
    public static String SECRET = "";

    //MT Identifiers
    public static String MT_HASH = "";
    public static String MTCONNECT_DIR = Constants.DATA_DIR + File.separator + "MTConnect";

    //S3 Auth
    public static String S3_KEY = "";
    public static String S3_SECRET = "";
    public static String S3_BUCKET = "";
    public static String S3_HOST = "";


    public static String getCreeperhostModpackSearch2(boolean _private)
    {
        if(Constants.KEY.isEmpty() || !_private)
        {
            return Constants.CREEPERHOST_MODPACK_SEARCH2;
        }
        if(Constants.KEY.isEmpty() && _private)
        {
            CreeperLogger.INSTANCE.error("Tried to access a private pack without having configured the secret and key.");
        }
        return Constants.CREEPERHOST_MODPACK + "/" + Constants.KEY + "/modpack/";
    }
}
