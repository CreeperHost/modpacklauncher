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
	public Path install(LocalInstance instance)
	{
		Path returnFile = null;
		String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();

		CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion() + " NewName: " + newname);

		Path file = Constants.VERSIONS_FOLDER_LOC.resolve(newname);
		FileUtils.createDirectories(file);

		//Add the jvm args to fix loading older forge versions
		instance.jvmArgs = instance.jvmArgs + " -Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true -Dminecraft.applet.TargetDirectory=" + instance.getDir().toAbsolutePath().toString().trim();
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
            Path instMods = instance.getDir().resolve("instmods");
			Files.createDirectories(instMods);

            Path forgeFile = instMods.resolve(newname + ".jar");
			if(Files.notExists(forgeFile))
			{
				DownloadableFile forge = new DownloadableFile(newname, forgeFile, url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
				DownloadTask task = new DownloadTask(forge, forgeFile);
				task.execute();
			}

            Path mcFile = instMods.resolve("minecraft.jar");
			if(Files.notExists(mcFile))
			{
				DownloadableFile mc = McUtils.getMinecraftDownload(getMinecraftVersion(), instMods);
				DownloadTask mcTask = new DownloadTask(mc, mcFile);
				mcTask.execute();
			}

			Path forgeJson = file.resolve(newname + ".json");
			if(Files.notExists(forgeJson))
			{
				CreeperLogger.INSTANCE.error("Failed to extract version json, attempting to download it from repo");
				String downloadName = "forge-" + getMinecraftVersion() + ".json";
				String jsonurl = "https://apps.modpacks.ch/versions/minecraftjsons/" + downloadName;

				if(WebUtils.checkExist(new URL(jsonurl)))
				{
					DownloadableFile fjson = new DownloadableFile(forgeJson.getFileName().toString(), forgeJson, jsonurl, new ArrayList<>(), 0, false, false, 0, downloadName, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
					DownloadTask ftask = new DownloadTask(fjson, forgeJson);
					ftask.execute().join();
				}
				else
				{
					CreeperLogger.INSTANCE.error("Failed to download " + downloadName + " from repo");
				}
			}

			if(Files.exists(forgeJson))
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
		try
		{
			CreeperLogger.INSTANCE.info("Pre-Play started");
			String newname = instance.getMcVersion() + "-forge" + instance.getMcVersion() + "-" + instance.getModLoader();

			Path instanceDir = instance.getDir();
			Path instMods = instanceDir.resolve("instmods");
            Path jarMods = instanceDir.resolve("jarmods");

			CreeperLogger.INSTANCE.info("intmods location: " + instMods.toAbsolutePath());
            Path mcFile = instMods.resolve("minecraft.jar");
			CreeperLogger.INSTANCE.info("mc location: " + mcFile.toAbsolutePath());

			//Merge Jars, This will be a prePlayTask in release code
			Path libVersionDir = Constants.LIBRARY_LOCATION.resolve("net/minecraftforge/forge/" + instance.getMcVersion() + "-" + instance.getModLoader());
			CreeperLogger.INSTANCE.info("LibVersionDir: " + libVersionDir);
			FileUtils.createDirectories(libVersionDir);
            Path forgeVersion = libVersionDir.resolve("forge-" +instance.getMcVersion() + "-" + instance.getModLoader() + ".jar");
			CreeperLogger.INSTANCE.info("forgeVersion: " + forgeVersion);
            Path versionsJar = Constants.VERSIONS_FOLDER_LOC.resolve(newname).resolve(newname + ".jar");
			FileUtils.createDirectories(versionsJar.getParent());

			//Remove the forge jar that is loaded so we can build a new one, This will be required for us to load newly added core mods
			Files.deleteIfExists(forgeVersion);

			if (Files.exists(mcFile))
			{
				CreeperLogger.INSTANCE.info("mc file exists, attempting to merge jars");
				try
				{
					Path merged = instMods.resolve("merged.jar");

					//Remove the prebuilt jar so we can make a fresh one
					Files.deleteIfExists(merged);

					Files.copy(mcFile, merged);

					FileUtils.removeMeta(merged);

					List<Path> instFiles = FileUtils.listDir(instMods);
                    List<Path> jarFiles = FileUtils.listDir(jarMods);

					if (instFiles != null) {
						CreeperLogger.INSTANCE.info("instmod folder has mods to merge, attempting to merge jars");
						//Merge every file in the instmods folder that is not the mc jar or the merge target
						for (Path instFile : instFiles) {
							if (instFile != null && !instFile.equals(merged)) {
								if (!instFile.getFileName().toString().contains("minecraft") && !instFile.getFileName().toString().contains("merged")) {
									CreeperLogger.INSTANCE.info("Merging " + instFile.getFileName() + " into the merged.jar");
									if (!FileUtils.mergeJars(instFile, merged)) {
										CreeperLogger.INSTANCE.error("Filed to merge " + instFile.getFileName() + " into merged.jar");
									}
								}
							}
						}

						if (jarFiles != null) {
							CreeperLogger.INSTANCE.info("jarmods folder has mods to merge, attempting to merge jars");
							//Merge every file in the instmods folder that is not the mc jar or the merge target
							for (Path instFile : jarFiles) {
								if (instFile.equals(merged)) {
									if (!instFile.getFileName().toString().contains("minecraft") && !instFile.getFileName().toString().contains("merged")) {
										CreeperLogger.INSTANCE.info("Merging " + instFile.getFileName() + " into the merged.jar");
										if (!FileUtils.mergeJars(instFile, merged)) {
											CreeperLogger.INSTANCE.error("Filed to merge " + instFile.getFileName() + " into merged.jar");
										}
									}
								}
							}
						}
					}
					//Move the merged jar to it location in the libs folder to load
					if (Files.exists(merged)) {
						Files.copy(merged, versionsJar, StandardCopyOption.REPLACE_EXISTING);
						Files.copy(merged, forgeVersion, StandardCopyOption.REPLACE_EXISTING);
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
		//1.2.5 -> 1.5.2
		return super.isApplicable() && minorMcVersion >= 2 && minorMcVersion <= 5;
	}
}
