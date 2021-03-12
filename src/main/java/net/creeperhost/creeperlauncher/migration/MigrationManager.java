package net.creeperhost.creeperlauncher.migration;

import net.covers1624.quack.util.SneakyUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.migration.migrators.LegacyMigrator;
import net.creeperhost.creeperlauncher.migration.migrators.V1To2;
import net.creeperhost.creeperlauncher.util.ElapsedTimer;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import net.creeperhost.creeperlauncher.util.LogsUploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * TODO, some form of progress dialog.
 * Created by covers1624 on 13/1/21.
 */
public class MigrationManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Specifies the highest data format version the FTBApp is capable of reading.
     */
    private static final int CURRENT_DATA_FORMAT = 2;

    private static final Set<Class<? extends Migrator>> migrators = new HashSet<>();

    static {
        migrators.add(LegacyMigrator.class);
        migrators.add(V1To2.class);
    }

    private final Path formatJsonPath;
    private FormatJson formatJson;

    public MigrationManager() {
        formatJsonPath = Constants.getDataDir().resolve("._format.json");
        if (Files.exists(formatJsonPath)) {
            try {
                formatJson = GsonUtils.loadJson(formatJsonPath, FormatJson.class);
            } catch (IOException e) {
                LOGGER.fatal("Failed to read FormatJson. Assuming FormatJson does not exist.", e);
            }
        }
    }

    public int getDataFormat() {
        return formatJson != null ? formatJson.format : -1;
    }

    public void doMigrations() {
        int from = getDataFormat();
        if (from == CURRENT_DATA_FORMAT) return;
        ResourceBundle bundle = ResourceBundle.getBundle("MigrationMessages");

        if (from > CURRENT_DATA_FORMAT) {
            LOGGER.warn("Loaded newer data format from disk: {}, current: {}", from, CURRENT_DATA_FORMAT);
            int ret = JOptionPane.showConfirmDialog(null, bundle.getString("migration.newer_format"), "FTBApp", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ret == JOptionPane.NO_OPTION) {
                LOGGER.info("Exiting at user request.");
                System.exit(2);
            }
            LOGGER.warn("Ignoring warning at user request, forcibly reverting saved data format.");
            markAndSaveLatest();
            return;
        }

        try {
            LOGGER.info("Starting migration from {} to {}", from, CURRENT_DATA_FORMAT);
            ElapsedTimer startTimer = new ElapsedTimer();
            MigrationContext ctx = MigrationContext.buildContext(migrators, from);
            LOGGER.info("Built MigrationContext in {}", startTimer.elapsedStr());

            List<MigrationContext.MigratorState> migrators = ctx.getMigrators();

            if (migrators.isEmpty()) {
                LOGGER.info("No migrators will be run for this upgrade. Skipping..");
                markAndSaveLatest();
                return;
            }

            LOGGER.debug("Built migration list:");
            for (MigrationContext.MigratorState state : migrators) {
                Migrator.Properties properties = state.props;
                String name = state.migratorClass.getName();
                LOGGER.debug("{} to {} with {}", properties.from(), properties.to(), name);
            }

            int ret = JOptionPane.showConfirmDialog(null, bundle.getString("migration.required"), "FTBApp", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ret == JOptionPane.CANCEL_OPTION) {
                LOGGER.info("Exiting at user request.");
                return;
            }

            for (MigrationContext.MigratorState state : migrators) {
                Migrator.Properties properties = state.props;
                String migratorName = state.migratorClass.getName();
                LOGGER.info("Executing migrator: {}", migratorName);
                ElapsedTimer migratorTimer = new ElapsedTimer();
                try {
                    state.migrator.operate(ctx);
                } catch (Throwable e) {
                    LOGGER.fatal("Fatal exception occurred whilst performing migration from {} to {} with {}.", properties.from(), properties.to(), migratorName, e);
                    captureMigrationError(bundle);
                }
                state.executed = true;
                LOGGER.info("Finished in {}", migratorTimer.elapsedStr());
            }
            LOGGER.info("Finished migration in {}", startTimer.elapsedStr());
            markAndSaveLatest();
        } catch (Throwable e) {
            LOGGER.fatal("Fatal exception occurred whilst performing migration.", e);
            captureMigrationError(bundle);
        }
    }

    private void markAndSaveLatest() {
        formatJson = new FormatJson(CURRENT_DATA_FORMAT);
        saveJson();
    }

    private void saveJson() {
        try {
            GsonUtils.saveJson(formatJsonPath, formatJson);
        } catch (IOException e) {
            LOGGER.fatal("Failed to save FormatJson.", e);
        }
    }

    private void captureMigrationError(ResourceBundle bundle) {
        String uploadCode = LogsUploader.uploadPaste(LogsUploader.getDebugLog());
        String logsHtml = uploadCode == null ? "Logs upload failed." : "<a href=\"https://pste.ch/" + uploadCode + "\">https://pste.ch/" + uploadCode + "</a>";
        JOptionPane.showMessageDialog(null, makeClickablePane(bundle.getString("migration.error") +
                        "  Logs: " + logsHtml),
                "FTBApp",
                JOptionPane.ERROR_MESSAGE
        );
        System.exit(2);
    }

    //TODO NO BAD COVERS!!! We have a UI, USE IT!!
    private static JEditorPane makeClickablePane(String content) {

        JLabel label = new JLabel();
        Font font = label.getFont();

        // create some css from the label's font
        StringBuilder style = new StringBuilder("font-family:" + font.getFamily() + ";");
        style.append("font-weight:" + (font.isBold() ? "bold" : "normal") + ";");
        style.append("font-size:" + font.getSize() + "pt;");

        JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + style + "\">" + content + "</body></html>");
        pane.addHyperlinkListener(e -> SneakyUtils.sneaky(() -> {
            if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                Desktop.getDesktop().browse(e.getURL().toURI());
            }
        }));
        pane.setEditable(false);
        pane.setBackground(label.getBackground());
        return pane;
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
