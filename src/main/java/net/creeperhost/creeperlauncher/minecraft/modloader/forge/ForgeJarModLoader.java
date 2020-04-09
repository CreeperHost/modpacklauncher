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
		String newname = getMinecraftVersion() + "-forge" + getMinecraftVersion() + "-" + getForgeVersion();
		CreeperLogger.INSTANCE.info("Minecraft version: " + getMinecraftVersion() + " Forge version: " + getForgeVersion());
		File file = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + newname);
		file.mkdir();
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
			DownloadableFile forge = new DownloadableFile(getForgeVersion(), forgeFile.getAbsolutePath(), url.toString(), new ArrayList<>(), 0, false, false, 0, newname, "modloader", String.valueOf(System.currentTimeMillis() / 1000L));
			DownloadTask task = new DownloadTask(forge, forgeFile.toPath());
			task.execute();

			File mcFile = new File(instMods.getAbsolutePath() + File.separator + "minecraft" + ".jar");
			DownloadableFile mc = McUtils.getMinecraftDownload(getMinecraftVersion(), instMods.getAbsolutePath());
			DownloadTask mcTask = new DownloadTask(mc, mcFile.toPath());
			mcTask.execute();

			instance.setPreUninstall(() ->
			{
				File versionInstance = new File(Constants.VERSIONS_FOLDER_LOC + File.separator + instance.getUuid());
				if (versionInstance.exists()) FileUtils.deleteDirectory(versionInstance);
			}, false);
			instance.modLoader = newname;
			try
			{
				instance.saveJson();
			} catch (Exception e)
			{
				CreeperLogger.INSTANCE.error("Failed to save instance json");
				CreeperLogger.INSTANCE.error(e.toString());
			}
		} catch (Exception ignored) { }
		return null;
	}

	@Override
	public boolean isApplicable()
	{
		int minorMcVersion = McUtils.parseMinorVersion(getTargetVersion("minecraft").orElse("0.0.0"));
		//1.2.5 -> 1.4.7
		return super.isApplicable() && minorMcVersion >= 2 && minorMcVersion <= 4;
	}
}
