package net.creeperhost.creeperlauncher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CreeperLogger
{
    public static CreeperLogger INSTANCE = new CreeperLogger();
    private static Logger logger;
    private String filename = Constants.getDataDir() + File.separator + "ftbapp.log";
    private String oldFilename = Constants.getDataDirOld() + File.separator + "ftbapp.log";
    private FileHandler fileHandler;
    private final int limit = 1024 * 10000; //10 MB
    private SimpleFormatter simpleFormatter;

    public CreeperLogger()
    {
        try {
            //Logger is initialized before data directory is created on first run...
            File logDir = new File(Constants.getDataDir());
            logDir.mkdirs();
        } catch(Exception e)
        {
            System.out.println(e);
        }
        logger = Logger.getLogger("ftbapp.log");

        simpleFormatter = new SimpleFormatter();
        try
        {
            fileHandler = new FileHandler(filename, limit, 1, true);
            logger.addHandler(fileHandler);
            simpleFormatter = new SimpleFormatter();
            fileHandler.setFormatter(simpleFormatter);
        } catch (Exception e)
        {
            try {
                fileHandler = new FileHandler(oldFilename, limit, 1, true);
                logger.addHandler(fileHandler);
                simpleFormatter = new SimpleFormatter();
                fileHandler.setFormatter(simpleFormatter);
            } catch (Exception e2) {
                try {
                    new File(filename).getParentFile().mkdirs();
                    fileHandler = new FileHandler(filename, limit, 1, true);
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
            fileHandler = new FileHandler(filename, limit, 1, true);
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

    private String throwableToString(Throwable ex)
    {
        StringBuilder printStr = new StringBuilder();
        printStr.append(ex.getClass().toString()).append(": ").append(ex.getMessage()).append("\n");
        for(StackTraceElement el: ex.getStackTrace())
        {
            printStr.append(el.toString()).append("\n");
        }
        return printStr.toString();
    }

    public void warning(String input)
    {
        logger.warning(input);
    }

    public void warning(String input, Throwable ex)
    {
        warning(input + "\n" + throwableToString(ex));
    }

    public void error(String input)
    {
        String caller = getCaller("error");
        logger.severe(caller + (caller.isEmpty() ? "" : "\n") + input);
    }

    public void error(String input, Throwable ex)
    {
        error(input + "\n" + throwableToString(ex));
    }

    public void debug(String input, Throwable ex) {
        debug(input + "\n" + throwableToString(ex));
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
