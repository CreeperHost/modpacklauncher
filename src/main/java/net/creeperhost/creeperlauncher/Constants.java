package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.os.OSUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants
{
    //CWD
    public static final Path WORKING_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String INNER_DATA_DIR = ".ftba";
    private static final Path DATA_DIR = Paths.get(System.getProperty("user.home"), INNER_DATA_DIR);

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
    public static final Path BIN_LOCATION_OURS = WORKING_DIR.resolve("bin");
    public static final Path BIN_LOCATION = getDataDir().resolve("bin");
    public static final String MINECRAFT_LAUNCHER_NAME = "launcher."+OSUtils.getExtension();
    public static final Path MINECRAFT_LAUNCHER_LOCATION = BIN_LOCATION.resolve(MINECRAFT_LAUNCHER_NAME);
    public static final String MINECRAFT_MAC_LAUNCHER_NAME = "Minecraft.app";
    public static final Path MINECRAFT_MAC_LAUNCHER_APP = BIN_LOCATION.resolve(MINECRAFT_MAC_LAUNCHER_NAME);
    public static final String MINECRAFT_MAC_LAUNCHER_EXECUTABLE_NAME = MINECRAFT_MAC_LAUNCHER_NAME + "/" + "Contents/MacOS/launcher";
    public static final Path MINECRAFT_MAC_LAUNCHER_EXECUTABLE = BIN_LOCATION.resolve(MINECRAFT_MAC_LAUNCHER_EXECUTABLE_NAME);

    public static final String MINECRAFT_MAC_LAUNCHER_VOLUME = "/Volumes/Minecraft";
    public static final String MINECRAFT_LINUX_LAUNCHER_EXECUTABLE_NAME = "minecraft-launcher/minecraft-launcher";
    public static final Path MINECRAFT_LINUX_LAUNCHER_EXECUTABLE = BIN_LOCATION.resolve(MINECRAFT_LINUX_LAUNCHER_EXECUTABLE_NAME);
    public static final Path VERSIONS_FOLDER_LOC = getDataDir().resolve(Paths.get("bin", "versions"));
    public static final Path INSTANCES_FOLDER_LOC = getDataDir().resolve("instances");
    public static final String LAUNCHER_PROFILES_JSON_NAME = "launcher_profiles.json";
    public static final Path LAUNCHER_PROFILES_JSON = BIN_LOCATION.resolve(LAUNCHER_PROFILES_JSON_NAME);
    public static final Path LIBRARY_LOCATION = BIN_LOCATION.resolve("libraries");
    public static final Path OLD_CACHE_LOCATION = getDataDir().resolve(".localCache");

    //Other
    public static final int WEBSOCKET_PORT = 13377;
    public static final String APPVERSION = "@APPVERSION@";
    public static final String BRANCH = "@BRANCH@";
    public static final String PLATFORM = WORKING_DIR.toAbsolutePath().toString().contains("Overwolf") ? "Overwolf" : "Electron";

    //Auth
    public static String KEY = "";
    public static String SECRET = "";

    //MT Identifiers
    public static String MT_HASH = "";
    public static Path MTCONNECT_DIR = getDataDir().resolve("MTConnect");

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
    public static Path getDataDir()
    {
        Path ret = DATA_DIR;
        if(OSUtils.getOs() == OS.WIN)
        {
            ret = Paths.get(System.getenv("LOCALAPPDATA"), INNER_DATA_DIR);
        } else if (OSUtils.getOs() == OS.MAC)
        {
            ret = Paths.get(System.getProperty("user.home"), "Library", "Application Support", INNER_DATA_DIR);
        }
        return ret.toAbsolutePath().normalize();
    }

    public static Path getDataDirOld() {
        return DATA_DIR.toAbsolutePath();
    }
}
