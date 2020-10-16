package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.UploadLogsData;

import java.io.File;

public class UploadLogsHandler implements IMessageHandler<UploadLogsData> {
    @Override
    public void handle(UploadLogsData data) {
        File logFile = new File("./launcher.log");
        File errorLogFile = new File("./error.log");
    }
}
