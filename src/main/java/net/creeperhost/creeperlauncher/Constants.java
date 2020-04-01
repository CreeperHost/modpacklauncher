package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.os.OSUtils;

import java.io.File;

public class Constants
{
    //CWD
    public static final String WORKING_DIR = System.getProperty("user.dir");

    //Launcher titles
    public static final String windowTitle = "FTB Launcher";
    public static final String version = "1.0.0";

    //Mojang
    public static final String MC_VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String MC_RESOURCES = "http://resources.download.minecraft.net/";
    public static final String MC_LIBS = "https://libraries.minecraft.net/";
    public static final String MC_LAUNCHER = "https://launcher.mojang.com/download/Minecraft.";

    //API
    public static final String CREEPERHOST_MODPACK_SEARCH2 = "https://api.modpacks.ch/public/modpack/";

    //Forge
    public static final String FORGE_XML = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/maven-metadata.xml";
    public static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/";
    public static final String FORGE_RECOMMENDED = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/promotions_slim.json";

    //Paths
    public static final String BIN_LOCATION = WORKING_DIR + File.separator + "bin";
    public static final String MINECRAFT_LAUNCHER_LOCATION = WORKING_DIR + File.separator + "bin" + File.separator + "launcher." + OSUtils.getExtension();
    public static final String MINECRAFT_MAC_LAUNCHER_EXECUTABLE = WORKING_DIR + File.separator + "bin" + File.separator + "Minecraft.app" + File.separator + "Contents" + File.separator + "MacOS" + File.separator + "launcher";
    public static final String MINECRAFT_MAC_LAUNCHER_VOLUME = "/Volumes/Minecraft";
    public static final String MINECRAFT_LINUX_LAUNCHER_EXECUTABLE = WORKING_DIR + File.separator + "bin" + File.separator + "minecraft-launcher" + File.separator + "minecraft-launcher";
    public static final String VERSIONS_FOLDER_LOC = WORKING_DIR + File.separator + "bin" + File.separator + "versions";
    public static final String INSTANCES_FOLDER_LOC = WORKING_DIR + File.separator + "instances";
    public static final String LAUNCHER_PROFILES_JSON = WORKING_DIR + File.separator + "bin" + File.separator + "launcher_profiles.json";
    public static final String LIBRARY_LOCATION = WORKING_DIR + File.separator + "bin" + File.separator + "libraries";
    public static final String CACHE_LOCATION = WORKING_DIR + File.separator + ".localCache";

    //Other
    public static final int WEBSOCKET_PORT = 13377;
    public static final String APPVERSION = "@APPVERSION@";
}