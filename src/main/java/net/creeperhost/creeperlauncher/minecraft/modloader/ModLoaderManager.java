package net.creeperhost.creeperlauncher.minecraft.modloader;

import net.creeperhost.creeperlauncher.minecraft.modloader.fabric.FabricModLoader;
import net.creeperhost.creeperlauncher.minecraft.modloader.forge.ForgeInstallerModLoader;
import net.creeperhost.creeperlauncher.minecraft.modloader.forge.ForgeJarModLoader;
import net.creeperhost.creeperlauncher.minecraft.modloader.forge.ForgeUniversalModLoader;
import net.creeperhost.creeperlauncher.util.LoaderTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModLoaderManager {

	private static final List<ModLoaderFactory<?>> MOD_LOADER_FACTORIES = new ArrayList<>();

	public static final ModLoaderFactory<ForgeInstallerModLoader> FORGE_INSTALLER = register(ForgeInstallerModLoader::new);
	public static final ModLoaderFactory<ForgeUniversalModLoader> FORGE_UNIVERSAL = register(ForgeUniversalModLoader::new);
	public static final ModLoaderFactory<ForgeJarModLoader> FORGE_JAR = register(ForgeJarModLoader::new);
	public static final ModLoaderFactory<FabricModLoader> FABRIC = register(FabricModLoader::new);

	private static <T extends ModLoader> ModLoaderFactory<T> register(ModLoaderFactory<T> modLoaderFactory)
	{
		MOD_LOADER_FACTORIES.add(modLoaderFactory);
		return modLoaderFactory;
	}

	public static List<ModLoader> getModLoaders(List<LoaderTarget> loaderTargets)
	{
		return MOD_LOADER_FACTORIES.stream()
				.map(modLoaderFactory -> modLoaderFactory.create(loaderTargets))
				.filter(ModLoader::isApplicable)
				.collect(Collectors.toList());
	}

	@FunctionalInterface
	public interface ModLoaderFactory<T extends ModLoader>
	{
		T create(List<LoaderTarget> loaderTargets);
	}
}
