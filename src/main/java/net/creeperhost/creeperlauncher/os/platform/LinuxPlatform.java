package net.creeperhost.creeperlauncher.os.platform;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.util.FileUtils;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents the Linux platform.
 * <p>
 * Created by covers1624 on 9/2/21.
 */
public class LinuxPlatform extends UnixPlatform {

    private static final String LAUNCHER_URL = "https://launcher.mojang.com/download/Minecraft.tar.gz";
    private static final String LAUNCHER_EXECUTABLE = "minecraft-launcher/minecraft-launcher";

    @Override
    public String getLauncherURL() {
        return LAUNCHER_URL;
    }

    @Override
    public Path getLauncherExecutable() {
        return Constants.BIN_LOCATION.resolve(LAUNCHER_EXECUTABLE);
    }

    @Override
    public void unpackLauncher(Path downloadedLauncher) throws IOException {
        FileUtils.unTar(new GzipCompressorInputStream(Files.newInputStream(downloadedLauncher)), Constants.BIN_LOCATION);
        chmod755(getLauncherExecutable());
    }
}
