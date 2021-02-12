package net.creeperhost.creeperlauncher.os;

import net.covers1624.quack.net.download.DownloadAction;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.other.CloseModalData;
import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;
import net.creeperhost.creeperlauncher.util.ElapsedTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a specific Platform implementation for a specific Operating System(or variant).
 *
 * TODO, Long Term, all platform specific code should be isolated in its respective Platform implementation class.
 *  - Starting Minecraft Launcher.
 *  - MT connect.
 *
 * Created by covers1624 on 9/2/21.
 */
public interface Platform {

    //INTERNAL
    Logger _LOGGER = LogManager.getLogger();

    /**
     * Gets the URL hosting the Minecraft Launcher for this specific platform.
     *
     * @return The URL.
     */
    String getLauncherURL();

    /**
     * Gets the path to the MinecraftLauncher executable for this Platform.
     *
     * @return The path to the executable.
     */
    Path getLauncherExecutable();

    /**
     * Performs whatever actions necessary to unpack the Minecraft Launcher for this platform into {@link Constants#BIN_LOCATION}.
     * This method should be agnostic to the Launcher already being installed and should overwrite it if that is the case.
     *
     * @param downloadedLauncher The location of the file downloaded from {@link #getLauncherURL()}.
     * @throws IOException If bork.
     */
    void unpackLauncher(Path downloadedLauncher) throws IOException;

    /**
     * Installs the Minecraft Launcher into {@link Constants#BIN_LOCATION}.
     *
     * @return If the operation succeeds.
     */
    default boolean installLauncher() {
        _LOGGER.info("Installing Minecraft Launcher.");
        Path launcherCache = Constants.BIN_LOCATION.resolve("launcherCache");
        String launcherUrl = getLauncherURL();
        String fileName = "Launcher";
        int lastSlash = launcherUrl.lastIndexOf('/');
        if (lastSlash != -1) {
            fileName = launcherUrl.substring(lastSlash + 1);
        }
        Path launcherFile = launcherCache.resolve(fileName);

        DownloadAction action = new DownloadAction();
        action.setSrc(launcherUrl);
        action.setDest(launcherFile);
        action.setUseETag(true);
        action.setOnlyIfModified(true);
        try {
            _LOGGER.info("Downloading launcher from {} to {}", launcherUrl, launcherFile);
            ElapsedTimer timer = new ElapsedTimer();
            action.execute();
            _LOGGER.info("Downloaded launcher in {}, action taken {}.", timer.elapsedStr(), !action.isUpToDate());
        } catch (IOException e) {
            _LOGGER.error("Failed to download Minecraft Launcher.", e);
            OpenModalData.openModal("Error", "Failed to Download the Minecraft Launcher. Please report this error with your logs.", List.of(
                    new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
            ));
            return false;
        }
        try {
            _LOGGER.info("Unpacking launcher.");
            unpackLauncher(launcherFile);
        } catch (IOException e) {
            _LOGGER.error("Failed to prepare the Minecraft Launcher for use.", e);
            OpenModalData.openModal("Error", "Failed to Prepare the Minecraft Launcher. Please report this error with your logs.", List.of(
                    new OpenModalData.ModalButton("Ok", "red", () -> Settings.webSocketAPI.sendMessage(new CloseModalData()))
            ));
            return false;
        }
        return true;
    }

}
