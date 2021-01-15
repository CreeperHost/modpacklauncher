package net.creeperhost.creeperlauncher.api.handlers.other;

import com.google.gson.JsonParser;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.other.UploadLogsData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.os.OSUtils;
import net.creeperhost.creeperlauncher.util.WebUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadLogsHandler implements IMessageHandler<UploadLogsData> {
    @Override
    public void handle(UploadLogsData data) {
        uploadLogs(data.uiVersion, data.frontendLogs, data.requestId);
    }

    public static void uploadLogs(String uiVersion, String frontendLogs, int requestId)
    {
        Path debugLogFile = Constants.getDataDir().resolve("logs/debug.log");

        String debugLogs = null;

        if(Files.exists(debugLogFile))
        {
            try {
                debugLogs = Files.readString(debugLogFile);
            } catch (IOException ignored) {
            }
        }

        String uploadData = "UI Version: " + (uiVersion != null ? uiVersion : "Unknown") + "\n" +
            "App Version: " + Constants.APPVERSION + "\n" +
            "Platform: " + Constants.PLATFORM + "\n" +
            "Operating System: " + OSUtils.getOs() + "\n" +
            "\n" +
            "\n" +
            padString(" debug.log ") + "\n" +
            (debugLogs == null ? "Not available" : debugLogs) + "\n" +
            "\n" +
            "\n" +
            padString(" main.log ") + "\n" +
            (frontendLogs == null ? "Not available" : frontendLogs);

        String s = WebUtils.postWebResponse("https://pste.ch/documents", uploadData, "text/plain; charset=UTF-8");
        JsonParser jsonParser = new JsonParser();
        String code = "";
        try {
            code = jsonParser.parse(s).getAsJsonObject().get("key").getAsString();
        } catch (Throwable t) {
            Settings.webSocketAPI.sendMessage(new UploadLogsData.Reply(requestId)); // error
        }

        Settings.webSocketAPI.sendMessage(new UploadLogsData.Reply(requestId, code));
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
