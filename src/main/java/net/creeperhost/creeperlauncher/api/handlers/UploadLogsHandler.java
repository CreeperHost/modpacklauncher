package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.api.data.UploadLogsData;
import net.creeperhost.creeperlauncher.util.WebUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadLogsHandler implements IMessageHandler<UploadLogsData> {
    @Override
    public void handle(UploadLogsData data) {
        uploadLogs(data.uiVersion, data.frontendLogs);
    }

    public static void uploadLogs(String uiVersion, String frontendLogs)
    {
        Path logFile = Path.of("./launcher.log");
        Path errorLogFile = Path.of("./error.log");

        String launcherLog = null;

        if(logFile.toFile().exists())
        {
            try {
                launcherLog = Files.readString(logFile);
            } catch (IOException ignored) {
            }
        }

        String errorLog = null;

        if(errorLogFile.toFile().exists())
        {
            try {
                errorLog = Files.readString(errorLogFile);
            } catch (IOException ignored) {
            }
        }

        String uploadData = "UI Version:" + (uiVersion != null ? uiVersion : "Unknown") + "\n" +
                "App Version: " + Constants.APPVERSION + "\n" +
                "\n" +
                "\n" +
                padString("launcher.log") + "\n" +
                (launcherLog == null ? "Not available" : launcherLog) + "\n" +
                "\n" +
                "\n" +
                padString("error.log") + "\n" +
                (errorLog == null ? "Not available" : errorLog) + "\n" +
                "\n" +
                "\n" +
                padString("main.log") + "\n" +
                (frontendLogs == null ? "Not available" : frontendLogs);
        System.out.println(WebUtils.postWebResponse("https://pste.ch/documents", uploadData, "text/plain; charset=UTF-8"));
    }

    private static String padString(String stringToPad) {
        int desiredLength = 86;
        char padChar = '=';
        int strLen = stringToPad.length();
        float halfLength = ((float)desiredLength - (float)strLen) / (float)2;
        int leftPad;
        int rightPad;
        if (((int) halfLength) != halfLength) {
            leftPad = (int) halfLength + 1;
            rightPad = (int) halfLength;
        } else {
            leftPad = rightPad = (int)halfLength;
        }

        String padCharStr = String.valueOf(padChar);

        return padCharStr.repeat(leftPad).concat(stringToPad).concat(padCharStr.repeat(rightPad));
    }
}
