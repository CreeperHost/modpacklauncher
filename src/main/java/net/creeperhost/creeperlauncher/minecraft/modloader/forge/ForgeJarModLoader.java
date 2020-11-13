package net.creeperhost.creeperlauncher.minecraft.modloader.forge;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.CreeperLogger;
import net.creeperhost.creeperlauncher.api.DownloadableFile;
import net.creeperhost.creeperlauncher.install.tasks.DownloadTask;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.ForgeUtils;
import net.creeperhost.creeperlauncher.util.LoaderTarget;
import net.creeperhost.creeperlauncher.util.WebUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ForgeJarModLoader extends ForgeModLoader
{
	public ForgeJarModLoader(List<LoaderTarget> loaderTargets)
	{
		super(loaderTargets);
	}

	@Override
	public String getName()
	{
		return "forge";
	}

	@Override
	public File install(LocalInstance instance)
	{
		File returnFile = null;
		String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();

		CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion() + " NewName: " + newname);

		File file = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname);
		file.mkdir();

		//Add the jvm args to fix loading older forge versions
		instance.jvmArgs = instance.jvmArgs + " -Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true".trim();
		try
		{
			URI url = null;
			try
			{
				url = ForgeUtils.findForgeDownloadURL(getMinecraftVersion(), getForgeVersion());
			} catch (URISyntaxException | MalformedURLException e)
			{
				e.printStackTrace();
			}
			File instMods = new File(instance.getDir() + File.separator + "instmods");
			instMods.mkdir();

			File forgeFile = new File(instMods.getAbsolutePath() + File.separator + newname + ".jar");
			if(!forgeFile.exists())
			{
				DownloadableFile forge = new DownloadableFile(newname, forgeFile.getAbsolutePath(), url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
				DownloadTask task = new DownloadTask(forge, forgeFile.toPath());
				task.execute();
			}

			File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");
			if(!mcFile.exists())
			{
				DownloadableFile mc = McUtils.getMinecraftDownload(getMinecraftVersion(), instMods.getAbsolutePath());
				DownloadTask mcTask = new DownloadTask(mc, mcFile.toPath());
				mcTask.execute();
			}

			File forgeJson = new File(file.getAbsolutePath() + File.separator + newname + ".json");
			if(!forgeJson.exists())
			{
				CreeperLogger.INSTANCE.error("Failed to extract version json, attempting to download it from repo");
				String downloadName = "forge-" + getMinecraftVersion() + ".json";
				String jsonurl = "https://apps.modpacks.ch/versions/minecraftjsons/" + downloadName;

				if(WebUtils.checkExist(new URL(jsonurl)))
				{
					DownloadableFile fjson = new DownloadableFile(forgeJson.getName(), forgeJson.getAbsolutePath(), jsonurl, new ArrayList<>(), 0, false, false, 0, downloadName, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
					DownloadTask ftask = new DownloadTask(fjson, forgeJson.toPath());
					ftask.execute().join();
				}
				else
				{
					CreeperLogger.INSTANCE.error("Failed to download " + downloadName + " from repo");
				}
			}

			if(forgeJson.exists())
			{
				ForgeUtils.updateForgeJson(forgeJson, newname, getMinecraftVersion());
				returnFile = forgeJson;
			}

			instance.mcVersion = getMinecraftVersion();
			instance.modLoader = getForgeVersion();

			try
			{
				instance.saveJson();
			} catch (Exception e)
			{
				CreeperLogger.INSTANCE.error("Failed to save instance json");
				CreeperLogger.INSTANCE.error(e.toString());
			}
			instance.setPostInstall(() -> prePlay(instance), false);

			return returnFile;
		} catch (Exception ignored) { }
		return returnFile;
	}

	public static void prePlay(LocalInstance instance)
	{
		try {

			CreeperLogger.INSTANCE.info("Pre-Play started");
			String newname = instance.getMcVersion() + "-forge" + instance.getMcVersion() + "-" + instance.getModLoader();

			File instMods = new File(instance.getDir() + File.separator + "instmods");
			File jarMods = new File(instance.getDir() + File.separator + "jarmods");

			CreeperLogger.INSTANCE.info("intmods location: " + instMods.getAbsolutePath());
			File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");
			CreeperLogger.INSTANCE.info("mc location: " + mcFile.getAbsolutePath());

			//Merge Jars, This will be a prePlayTask in release code
			File libVersionDir = new File(Constants.LIBRARY_LOCATION + File.separator + "net" + File.separator + "minecraftforge" + File.separator + "forge" + File.separator + instance.getMcVersion() + "-" + instance.getModLoader());
			CreeperLogger.INSTANCE.info("LibVersionDir: " + libVersionDir);
			if (!libVersionDir.exists()) libVersionDir.mkdirs();
			File forgeVersion = new File(libVersionDir + File.separator + "forge-" + instance.getMcVersion() + "-" + instance.getModLoader() + ".jar");
			CreeperLogger.INSTANCE.info("forgeVersion: " + forgeVersion);
			File versionsDir = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname + File.separator + newname + ".jar");
			if(!versionsDir.getParentFile().exists()) versionsDir.getParentFile().mkdirs();

			//Remove the forge jar that is loaded so we can build a new one, This will be required for us to load newly added core mods
			if(forgeVersion.exists()) forgeVersion.delete();

			if (mcFile.exists())
			{
				CreeperLogger.INSTANCE.info("mc file exists, attempting to merge jars");
				try
				{
					File merged = new File(instMods + File.separator + "merged.jar");
					FileUtils.removeMeta(merged);

					//Remove the prebuilt jar so we can make a fresh one
					if(merged.exists()) merged.delete();

					Files.copy(mcFile.toPath(), merged.toPath());

					File[] instFiles = instMods.listFiles();
					File[] jarFiles = jarMods.listFiles();

					if (instFiles != null) {
						CreeperLogger.INSTANCE.info("instmod folder has mods to merge, attempting to merge jars");
						//Merge every file in the instmods folder that is not the mc jar or the merge target
						for (File instFile : instFiles) {
							if (instFile != null && instFile != merged) {
								if (!instFile.getName().contains("minecraft") && !instFile.getName().contains("merged")) {
									CreeperLogger.INSTANCE.info("Merging " + instFile.getName() + " into the merged.jar");
									if (!FileUtils.mergeJars(instFile, merged)) {
										CreeperLogger.INSTANCE.error("Filed to merge " + instFile.getName() + " into merged.jar");
									}
								}
							}
						}

						if (jarFiles != null) {
							CreeperLogger.INSTANCE.info("jarmods folder has mods to merge, attempting to merge jars");
							//Merge every file in the instmods folder that is not the mc jar or the merge target
							for (File instFile : jarFiles) {
								if (instFile != null && instFile != merged) {
									if (!instFile.getName().contains("minecraft") && !instFile.getName().contains("merged")) {
										CreeperLogger.INSTANCE.info("Merging " + instFile.getName() + " into the merged.jar");
										if (!FileUtils.mergeJars(instFile, merged)) {
											CreeperLogger.INSTANCE.error("Filed to merge " + instFile.getName() + " into merged.jar");
										}
									}
								}
							}
						}
					}
					//Move the merged jar to it location in the libs folder to load
					if (merged.exists()) {
						Files.copy(merged.toPath(), versionsDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
						Files.copy(merged.toPath(), forgeVersion.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
					CreeperLogger.INSTANCE.info("All files successfully merged");

					instance.modLoader = newname;
					instance.saveJson();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public boolean isApplicable()
	{
		int minorMcVersion = McUtils.parseMinorVersion(getTargetVersion("minecraft").orElse("0.0.0"));
		//1.2.5 -> 1.4.7
		return super.isApplicable() && minorMcVersion >= 2 && minorMcVersion <= 4;
	}
}
