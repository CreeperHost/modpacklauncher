package net.creeperhost.creeperlauncher.minecraft.modloader.forge;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.ForgeUtils;
import net.creeperhost.creeperlauncher.util.LoaderTarget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ForgeInstallerModLoader extends ForgeModLoader
{
	public ForgeInstallerModLoader(List<LoaderTarget> loaderTargets)
	{
		super(loaderTargets);
	}

	@Override
	public String getName()
	{
		return "forge";
	}

	@Override
	public Path install(LocalInstance instance)
	{
		String forgeUrl = "https://apps.modpacks.ch/versions/net/minecraftforge/forge/" + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.jar";
		String forgeUrlJson = "https://apps.modpacks.ch/versions/net/minecraftforge/forge/" + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.json";

		CreeperLogger.INSTANCE.info("Attempting to download " + forgeUrl);
		Path installerFile = instance.getDir().resolve("installer.jar");
        Path installerJson = instance.getDir().resolve("installer.json");

		DownloadUtils.downloadFile(installerFile, forgeUrl);
		DownloadUtils.downloadFile(installerJson, forgeUrlJson);

		if(Files.notExists(installerJson))
		{
			//If we do not have the file lets extract it from the installer jar
			ForgeUtils.extractJson(installerFile, "installer.json");
		}

		instance.setPostInstall(() ->
		{
			ForgeUtils.runForgeInstaller(installerFile.toAbsolutePath());
			McUtils.removeProfile(Constants.LAUNCHER_PROFILES_JSON, "forge");
            try {
                Files.delete(installerFile);
            } catch (IOException ignored) {
            }
        }, false);

		instance.modLoader = getMinecraftVersion() + "-forge-" + getForgeVersion();
		try
		{
			instance.saveJson();
		} catch (Exception ignored) {}
		return installerJson;
	}

	@Override
	public boolean isApplicable() {
		int minorMcVersion = McUtils.parseMinorVersion(getTargetVersion("minecraft").orElse("0.0.0"));
		//1.13 onwards

		if (minorMcVersion == 12) {
			String[] versions = getForgeVersion().split("\\.");
			try {
				int forgeVer = Integer.parseInt(versions[versions.length - 1]);
				return super.isApplicable() && forgeVer >= 2851;
			} catch (NumberFormatException ignored) {
				// ¯\_(ツ)_/¯
			}
		}
		return super.isApplicable() && minorMcVersion >= 13;
	}
}
