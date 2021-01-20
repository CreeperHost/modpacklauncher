package net.creeperhost.creeperlauncher.migration;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.migration.migrators.LegacyMigrator;
import net.creeperhost.creeperlauncher.util.ElapsedTimer;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * TODO, user feedback needs to be added to this, as currently theres no way for a user to know anything is happening.
 *  Basic swing/javafx window should do.
 *
 * Created by covers1624 on 13/1/21.
 */
public class MigrationManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Specifies the highest data format version the FTBApp is capable of reading.
     */
    private static final int CURRENT_DATA_FORMAT = 1;

    private static final Set<Class<? extends Migrator>> migrators = new HashSet<>();

    static {
        migrators.add(LegacyMigrator.class);
    }

    private final Path formatJsonPath;
    private FormatJson formatJson;

    public MigrationManager() {
        formatJsonPath = Constants.getDataDir().resolve("._format.json");
        if (Files.exists(formatJsonPath)) {
            try {
                formatJson = GsonUtils.loadJson(formatJsonPath, FormatJson.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read FormatJson.");//TODO dont throw this.
            }
        }
    }

    public int getDataFormat() {
        return formatJson != null ? formatJson.format : -1;
    }

    public void doMigrations() {
        if (getDataFormat() == CURRENT_DATA_FORMAT) return;

        int from = getDataFormat();
        LOGGER.info("Starting migration from {} to {}", from, CURRENT_DATA_FORMAT);
        ElapsedTimer startTimer = new ElapsedTimer();
        MigrationContext ctx = MigrationContext.buildContext(migrators, from);
        LOGGER.info("Built MigrationContext in {}", startTimer.elapsedStr());

        for (MigrationContext.MigratorState state : ctx.getMigrators()) {
            Migrator.Properties properties = state.props;
            String migratorName = state.migratorClass.getName();
            LOGGER.info("Executing migrator: {}", migratorName);
            ElapsedTimer migratorTimer = new ElapsedTimer();
            try {
                state.migrator.operate(ctx);
            } catch (Throwable e) {
                LOGGER.fatal("Fatal exception occurred whilst performing migration from {} to {} with {}.", properties.from(), properties.to(), migratorName, e);
                Path logPath = Constants.BIN_LOCATION.resolve("logs/debug.log");
                //TODO, this needs to be improved.
                // Either automatically upload the logs and provide a web link, or somehow make the file link clickable.
                JOptionPane.showMessageDialog(null,
                        "<html>Fatal exception occurred whilst performing data migration. Please provide your debug.log to support. The FTBApp will now exit.<br/>" +
                                "  Logs: " + logPath.toAbsolutePath() + "</html>",
                        "Error migrating data.",
                        JOptionPane.ERROR_MESSAGE
                );
                System.exit(2);
            }
            state.executed = true;
            LOGGER.info("Finished in {}", migratorTimer.elapsedStr());
        }
        LOGGER.info("Finished migration in {}", startTimer.elapsedStr());
        formatJson = new FormatJson(CURRENT_DATA_FORMAT);
        saveJson();
    }

    private void saveJson() {
        try {
            GsonUtils.saveJson(formatJsonPath, formatJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write FormatJson.");//TODO, don't throw this.
        }
    }

    private static class FormatJson {

        public String ___comment = "Stores what format the data is laid out in. DO NOT EDIT THIS FILE, THINGS WILL BREAK.";
        public int format;

        public FormatJson() {
        }

        public FormatJson(int format) {
            this();
            this.format = format;
        }
    }
}
