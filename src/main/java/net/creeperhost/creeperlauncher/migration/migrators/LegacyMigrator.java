package net.creeperhost.creeperlauncher.migration.migrators;

import com.google.gson.reflect.TypeToken;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.migration.MigrationContext;
import net.creeperhost.creeperlauncher.migration.Migrator;
import net.creeperhost.creeperlauncher.os.OS;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles moving FTBApp data from ~/.ftba on Windows and Mac.
 * <p>
 * Created by covers1624 on 13/1/21.
 */
@Migrator.Properties (from = -1, to = 1)
public class LegacyMigrator implements Migrator {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Type settingsToken = new TypeToken<HashMap<String, String>>() {}.getType();

    @Override
    public void operate(MigrationContext ctx) throws MigrationException {
        try {
            OS os = OS.current();
            if (os == OS.LINUX) return;
            LOGGER.info("Attempting Legacy Migration.");

            Path newSettings = Constants.BIN_LOCATION.resolve("settings.json");

            if (Files.exists(newSettings)) return;
            LOGGER.info("New settings location does not exist: {}", newSettings);

            Path oldDataDir = Constants.getDataDirOld();
            Path newDataDir = Constants.getDataDir();

            Path oldSettings = oldDataDir.resolve("bin/settings.json");
            if (Files.notExists(oldSettings)) return;
            LOGGER.info("Old settings location exists. {}", oldSettings);

            LOGGER.info("Moving settings..");
            Files.move(oldSettings, newSettings);

            Map<String, String> settings = GsonUtils.loadJson(newSettings, settingsToken);

            Path oldInstancesDir = oldDataDir.resolve("instances");
            if (Files.exists(oldInstancesDir)) {
                String oldInstancesLoc = settings.getOrDefault("instanceLocation", "");
                if (oldInstancesLoc.equals(oldInstancesDir.toAbsolutePath().toString())) {
                    LOGGER.info("Found old instances in non-custom location. Updating settings json..");
                    //Remove old cache and update to new instance location.
                    FileUtils.deleteDirectory(oldInstancesDir.resolve(".localCache"));
                    settings.put("instanceLocation", Constants.INSTANCES_FOLDER_LOC.toAbsolutePath().toString());
                    GsonUtils.saveJson(newSettings, settings, settingsToken);
                }
            }
            LOGGER.info("Moving files..");
            FileUtils.move(oldDataDir, newDataDir, false);
            LOGGER.info("Finished.");
        } catch (IOException e) {
            throw new MigrationException("Failed to migrate.", e);
        }
    }
}
