package net.creeperhost.creeperlauncher.minecraft.modloader.forge;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.ForgeUtils;
import net.creeperhost.creeperlauncher.util.LoaderTarget;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ForgeUniversalModLoader extends ForgeModLoader
{
	public ForgeUniversalModLoader(List<LoaderTarget> loaderTargets)
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
        Path returnFile = null;
		String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();
		instance.modLoader = newname;
		CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion());
        Path file = Constants.VERSIONS_FOLDER_LOC.resolve(newname);
        FileUtils.createDirectories(file);

		DownloadUtils.downloadFile(file.resolve(newname + ".json"), "https://apps.modpacks.ch/versions/minecraftjsons/forge-1.5.2.json");

		//TODO clean this up but it should work for testing
		if(getMinecraftVersion().equalsIgnoreCase("1.5.2"))
		{
			instance.jvmArgs = instance.jvmArgs + " -Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true".trim();
			Path vanillaFolder = Constants.VERSIONS_FOLDER_LOC.resolve(getMinecraftVersion());
			if(Files.notExists(vanillaFolder))
			{
				FileUtils.createDirectories(vanillaFolder);
				DownloadableFile mc = McUtils.getMinecraftDownload(getMinecraftVersion(), vanillaFolder);
				DownloadTask mcTask = new DownloadTask(mc, new File(vanillaFolder + File.separator + getMinecraftVersion() + ".jar").toPath());
				mcTask.execute().join();
			}
		}

		try
		{
			URI url = ForgeUtils.findForgeDownloadURL(getMinecraftVersion(), getForgeVersion());
			Path forgeFile = file.resolve(newname + ".jar");
			DownloadableFile forge = new DownloadableFile(getForgeVersion(), forgeFile, url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
			DownloadTask task = new DownloadTask(forge, forgeFile);
			task.execute().join();

			CreeperLogger.INSTANCE.info("Completed download of " + newname);

			if (Files.exists(forgeFile))
			{
				boolean extracted = ForgeUtils.extractJson(forgeFile, newname + ".json");
				Path forgeJson = file.resolve(newname + ".json");
				if(!extracted)
				{
					CreeperLogger.INSTANCE.error("Failed to extract version json, attempting to download it from repo");
					String downloadName = "forge-" + getMinecraftVersion() + ".json";
					DownloadableFile fjson = new DownloadableFile(forgeJson.getFileName().toString(), forgeJson, "https://apps.modpacks.ch/versions/minecraftjsons/" + downloadName, new ArrayList<>(), 0, false, false, 0, downloadName, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
					DownloadTask ftask = new DownloadTask(fjson, forgeJson);
					ftask.execute().join();
				}
				if (Files.exists(forgeJson))
				{
					ForgeUtils.updateForgeJson(forgeJson, newname, getMinecraftVersion());
					//Move the forge jar to its home in libs
					File libForgeDir = new File(Constants.LIBRARY_LOCATION + File.separator + "net" + File.separator + "minecraftforge" + File.separator + "forge" + File.separator + getMinecraftVersion() + "-" + getForgeVersion());
					if (!libForgeDir.exists()) libForgeDir.mkdirs();
					File forgeLib = new File(libForgeDir + File.separator + "forge-" + getMinecraftVersion() + "-" + getForgeVersion() + ".jar");
					if (!forgeLib.exists()) Files.copy(forgeFile, forgeLib.toPath(), StandardCopyOption.REPLACE_EXISTING);

					returnFile = forgeJson;
				} else
				{
					CreeperLogger.INSTANCE.error("Failed to get the 'version.json' for '" + newname + "'");
				}
			}
		} catch (Throwable e)
		{
			CreeperLogger.INSTANCE.error(e.toString());
			e.printStackTrace();
		}
		try
		{
			instance.saveJson();
		} catch (Exception ignored) {}
		return returnFile;
	}

	@Override
	public boolean isApplicable()
	{
		int minorMcVersion = McUtils.parseMinorVersion(getTargetVersion("minecraft").orElse("0.0.0"));
		//1.6 -> 1.12.2
		return super.isApplicable() && minorMcVersion >= 6 && minorMcVersion <= 12;
	}
}
