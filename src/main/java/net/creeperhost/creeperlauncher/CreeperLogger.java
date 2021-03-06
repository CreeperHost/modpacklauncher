package net.creeperhost.creeperlauncher;

import net.creeperhost.creeperlauncher.util.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.LogManager;

public class CreeperLogger
{
    public static CreeperLogger INSTANCE = new CreeperLogger();
    private static Logger logger;
    private Path filename = Constants.getDataDir().resolve("ftbapp.log");
    private Path oldFilename = Constants.getDataDirOld().resolve("ftbapp.log");
    private FileHandler fileHandler;
    private final int limit = 1024 * 10000; //10 MB
    private SimpleFormatter simpleFormatter;

    public CreeperLogger()
    {
        FileUtils.createDirectories(Constants.getDataDir());
        try (InputStream is = CreeperLogger.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException ignored) {
        }
        logger = Logger.getLogger("ftbapp.log");

        simpleFormatter = new SimpleFormatter();
        try
        {
            fileHandler = new FileHandler(filename.toAbsolutePath().toString(), limit, 1, true);
            logger.addHandler(fileHandler);
            simpleFormatter = new SimpleFormatter();
            fileHandler.setFormatter(simpleFormatter);
        } catch (Exception e)
        {
            try {
                fileHandler = new FileHandler(oldFilename.toAbsolutePath().toString(), limit, 1, true);
                logger.addHandler(fileHandler);
                simpleFormatter = new SimpleFormatter();
                fileHandler.setFormatter(simpleFormatter);
            } catch (Exception e2) {
                try {
                    FileUtils.createDirectories(filename.getParent());
                    fileHandler = new FileHandler(filename.toAbsolutePath().toString(), limit, 1, true);
                    logger.addHandler(fileHandler);
                    simpleFormatter = new SimpleFormatter();
                    fileHandler.setFormatter(simpleFormatter);
                } catch (Exception e3) {
                    error("You'll probably never see this, but there was an error creating the log - tried everything!", e3);
                }
            }

        }
    }

    public void reinitialise()
    {
        try {
            fileHandler = new FileHandler(filename.toAbsolutePath().toString(), limit, 1, true);
            fileHandler.setFormatter(simpleFormatter);
            logger.addHandler(fileHandler);
        } catch (Exception e) {
            INSTANCE = new CreeperLogger(); // try recreating entire thing
        }
    }

    public void close() {
        fileHandler.close();
        logger.removeHandler(fileHandler);
    }

    public void info(String input)
    {
        logger.info(input);
    }

    public void warning(String input)
    {
        logger.warning(input);
    }

    public void warning(String input, Throwable ex)
    {
        warning(input + "\n" + ExceptionUtils.getStackTrace(ex));
    }

    public void error(String input)
    {
        String caller = getCaller("error");
        logger.severe(caller + (caller.isEmpty() ? "" : ": ") + input);
    }

    public void error(String input, Throwable ex)
    {
        error(input + "\n" + ExceptionUtils.getStackTrace(ex));
    }

    public void debug(String input, Throwable ex) {
        debug(input + "\n" + ExceptionUtils.getStackTrace(ex));
    }

    public void debug(String input)
    {
        if (CreeperLauncher.verbose) logger.log(Level.INFO, input);
    }

    private String getCaller(String exclude) {
        for(StackTraceElement el: Thread.currentThread().getStackTrace())
        {
            String methodName = el.getMethodName();
            if (methodName.contains(exclude) || methodName.contains("getStackTrace") || methodName.contains("getCaller")) continue;
            return el.toString();
        }
        return "";
    }
}
