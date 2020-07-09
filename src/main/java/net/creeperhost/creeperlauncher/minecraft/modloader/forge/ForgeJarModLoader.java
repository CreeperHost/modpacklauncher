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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
		String forgeVersionSmall = instance.getModLoader().split("-")[2];
		String newname = instance.getMcVersion() + "-forge" + instance.getMcVersion() + "-" + forgeVersionSmall;
		CreeperLogger.INSTANCE.info("Minecraft version: " + instance.getMcVersion() + " Forge version: " + forgeVersionSmall);
		File file = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname);
		file.mkdir();

		//Add the jvm args to fix loading older forge versions
		instance.jvmArgs = instance.jvmArgs + " -Dminecraft.applet.TargetDirectory=" + instance.getDir() +
				"-Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true -Duser.language=en -Duser.country=US";
		try
		{
			URI url = null;
			try
			{
				url = ForgeUtils.findForgeDownloadURL(instance.getMcVersion(), forgeVersionSmall);
			} catch (URISyntaxException | MalformedURLException e)
			{
				e.printStackTrace();
			}
			File instMods = new File(instance.getDir() + File.separator + "instmods");
			instMods.mkdir();

			File forgeFile = new File(instMods.getAbsolutePath() + File.separator + instance.getModLoader() + ".jar");
			if(!forgeFile.exists())
			{
				DownloadableFile forge = new DownloadableFile(forgeVersionSmall, forgeFile.getAbsolutePath(), url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
				DownloadTask task = new DownloadTask(forge, forgeFile.toPath());
				task.execute();
			}

			File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");
			if(!mcFile.exists())
			{
				DownloadableFile mc = McUtils.getMinecraftDownload(instance.getMcVersion(), instMods.getAbsolutePath());
				DownloadTask mcTask = new DownloadTask(mc, mcFile.toPath());
				mcTask.execute();
			}

			File forgeJson = new File(file.getAbsolutePath() + File.separator + newname + ".json");
			if(!forgeJson.exists())
			{
				String downloadName = "forge-" + instance.getMcVersion() + ".json";
				DownloadableFile fjson = new DownloadableFile(forgeJson.getName(), forgeJson.getAbsolutePath(), "https://apps.modpacks.ch/versions/minecraftjsons/" + downloadName, new ArrayList<>(), 0, false, false, 0, forgeJson.getName(), "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
				DownloadTask ftask = new DownloadTask(fjson, forgeJson.toPath());
				ftask.execute();
			}
			ForgeUtils.updateForgeJson(forgeJson, newname, instance.getMcVersion());

			instance.setPrePlay(() -> prePlay(instance), false);

			instance.modLoader = newname;
			try
			{
				instance.saveJson();
			} catch (Exception e)
			{
				CreeperLogger.INSTANCE.error("Failed to save instance json");
				CreeperLogger.INSTANCE.error(e.toString());
			}
			return forgeJson;
		} catch (Exception ignored) { }
		return null;
	}

	public static void prePlay(LocalInstance instance)
	{
		String forgeVersionSmall = instance.getModLoader().split("-")[2];
		File instMods = new File(instance.getDir() + File.separator + "instmods");
		File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");

		//Merge Jars, This will be a prePlayTask in release code
		File libVersionDir = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + "net" + File.separator + "minecraftforge" + File.separator + "forge" + File.separator + instance.getModLoader() + "-" + forgeVersionSmall);

		if (!libVersionDir.exists()) libVersionDir.mkdirs();
		File forgeVersion = new File(libVersionDir + File.separator + "forge-" + instance.getMcVersion() + "-" + forgeVersionSmall + ".jar");

		//Remove the forge jar that is loaded so we can build a new one, This will be required for us to load newly added core mods
		if(forgeVersion.exists()) forgeVersion.delete();

		if(mcFile.exists())
		{
			try
			{
				File out = new File(instMods + File.separator + "merged.jar");

				//Remove the prebuilt jar so we can make a fresh one
				if(out.exists()) out.delete();

				Files.copy(mcFile.toPath(), out.toPath());

				File[] instFiles = instMods.listFiles();
				if (instFiles != null)
				{
					//Merge every file in the instmods folder that is not the mc jar or the merge target
					for (File instFile : instFiles)
					{
						if (instFile != null && instFile != out)
						{
							if(!instFile.getName().contains("minecraft") && !instFile.getName().contains("merged"))
							{
								CreeperLogger.INSTANCE.info("Merging " + instFile.getName() + " into the merged.jar");
								if (!FileUtils.mergeJars(instFile, out))
								{
									CreeperLogger.INSTANCE.error("Filed to merge " + instFile.getName() + " into merged.jar");
								}
							}
						}
					}
				}
				//Move the merged jar to it location in the libs folder to load
				if (!out.exists())
				{
					Files.copy(out.toPath(), forgeVersion.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				CreeperLogger.INSTANCE.info("All files successfully merged");
			} catch (Exception e) { e.printStackTrace(); }
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
